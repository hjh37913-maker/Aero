'use strict';
// Run against the local static server. Needs playwright-core and Chromium (see README).
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const {pathToFileURL}=require('node:url');
const {chromium}=require('playwright-core');
const BASE=process.env.BASE_URL || 'http://127.0.0.1:8000';
const ROOT=path.resolve(__dirname,'..');
let browser;
(async()=>{
  let executablePath=process.env.CHROMIUM_PATH;
  let extra=[];
  if(!executablePath) { const sp=require('@sparticuz/chromium'); executablePath=await sp.executablePath(); extra=sp.args; }
  browser=await chromium.launch({headless:true,executablePath,args:extra});
  const errors=[], external=[];
  const context=await browser.newContext({viewport:{width:1440,height:900},acceptDownloads:true});
  await context.route('**/*',route=>{
    const url=route.request().url();
    if(url.startsWith('file:') || url.startsWith(BASE+'/') || url===BASE || url.startsWith('data:')) return route.continue();
    external.push(url); return route.abort();
  });
  const page=await context.newPage();
  page.on('pageerror',error=>errors.push(error.stack || error.message));
  const response=await page.goto(BASE+'/',{waitUntil:'networkidle'});
  assert.equal(response.status(),200);
  assert.equal(await page.title(),'Обзор — AI WORKER');
  assert.equal(await page.locator('#overview-list .job-row').count(),6);
  assert.deepEqual(await page.locator('#stats .stat strong').allTextContents(),['20','7','13','0']);
  await page.click('a.nav-item[data-route="catalog"]');
  assert.equal(await page.locator('#catalog-list .job-row').count(),20);
  await page.click('[data-filter="direct"]'); assert.equal(await page.locator('#catalog-list .job-row').count(),7);
  await page.click('[data-filter="index"]'); assert.equal(await page.locator('#catalog-list .job-row').count(),13);
  await page.click('[data-filter="top"]'); assert.equal(await page.locator('#catalog-list .job-row').count(),5);
  await page.selectOption('#category','Таблицы'); assert.equal(await page.locator('#catalog-list .job-row').count(),2);
  await page.fill('#search','БРАКА'); assert.equal(await page.locator('#catalog-list .job-row').count(),1);
  await page.fill('#search','<img src=x onerror=window.bad=1>');
  assert.equal(await page.locator('#catalog-list .job-row').count(),0); assert.equal(await page.evaluate(()=>window.bad),undefined);
  await page.fill('#search',''); await page.selectOption('#category','all'); await page.click('[data-filter="all"]');
  await page.locator('#catalog-list [data-job="PC-27641d"]').click();
  assert.equal(page.url().endsWith('#brief'),true);
  assert.match(await page.locator('#brief-content').innerText(),/Шаблон Excel для учёта брака/);
  assert.equal(await page.getByRole('button',{name:'Отправить отклик'}).isDisabled(),true);
  const externalLink=page.locator('#brief-content a[target="_blank"]');
  assert.match(await externalLink.getAttribute('href'),/^https:\/\/projects\.co\.id\//);
  assert.match(await externalLink.getAttribute('rel'),/noopener/);
  await page.selectOption('#job-picker','FH-1664590');
  assert.match(await page.locator('#brief-content').innerText(),/Страница и счётчик видны напрямую/);
  await page.reload({waitUntil:'networkidle'});
  assert.equal(await page.locator('#job-picker').inputValue(),'FH-1664590');
  await page.click('a.nav-item[data-route="ranking"]');
  assert.equal(await page.locator('.rank-card').count(),5);
  assert.match(await page.locator('.rank-card').first().innerText(),/75/);
  await page.click('a.nav-item[data-route="practice"]');
  assert.deepEqual(await page.locator('#demo-summary .demo-total strong').allTextContents(),['18','8','3']);
  await page.fill('#demo-date','2026-12-15'); await page.selectOption('#demo-worker','P-100');
  await page.selectOption('#demo-cause','Поставщик'); await page.fill('#demo-count','5');
  await page.click('#demo-form button[type="submit"]');
  assert.equal(await page.locator('#demo-month').inputValue(),'2026-12');
  assert.deepEqual(await page.locator('#demo-summary .demo-total strong').allTextContents(),['8','3','3']);
  assert.match(await page.locator('#demo-message').innerText(),/XLSX не изменён/);
  await page.fill('#demo-count','-1'); await page.click('#demo-form button[type="submit"]');
  assert.match(await page.locator('#demo-message').innerText(),/Проверьте/);
  assert.equal(await page.locator('#demo-summary .demo-total strong').first().innerText(),'8');
  await page.click('#reset-demo');
  assert.deepEqual(await page.locator('#demo-summary .demo-total strong').allTextContents(),['18','8','3']);
  const downloadPromise=page.waitForEvent('download');
  await page.locator('a[download][href$=".xlsx"]').click();
  const download=await downloadPromise; const bytes=fs.readFileSync(await download.path());
  assert.equal(bytes.subarray(0,2).toString(),'PK'); assert(bytes.length>10000);
  await page.click('a.nav-item[data-route="quality"]');
  await page.click('#run-check'); assert.match(await page.locator('#check-result').innerText(),/Пройдено/);
  await page.locator('[data-check="source"]').check();
  assert.equal(await page.evaluate(()=>document.activeElement.dataset.check),'source');
  assert.equal(await page.locator('#check-count').innerText(),'1 / 5');
  await page.reload({waitUntil:'networkidle'}); assert.equal(await page.locator('[data-check="source"]').isChecked(),true);
  for(const id of ['synthetic','totals','limits','compat']) await page.locator(`[data-check="${id}"]`).check();
  assert.equal(await page.locator('#check-count').innerText(),'5 / 5');
  await page.click('a.nav-item[data-route="brief"]'); assert.equal(await page.getByRole('button',{name:'Отправить отклик'}).isDisabled(),true);
  await page.evaluate(()=>localStorage.setItem('unrelated','keep'));
  await page.click('a.nav-item[data-route="control"]'); await page.click('#reset-storage');
  assert.equal(await page.evaluate(()=>localStorage.getItem('unrelated')),'keep');
  assert.equal(await page.evaluate(()=>localStorage.getItem('ai-worker:checks:v1')),null);
  assert.equal(await page.evaluate(()=>localStorage.getItem('ai-worker:selected:v1')),null);
  // Check every view for viewport-level horizontal overflow, including the 320px minimum.
  for(const width of [320,375,620,760,1024,1440]){
    await page.setViewportSize({width,height:800});
    for(const route of ['overview','catalog','ranking','brief','practice','quality','control']){
      await page.evaluate(route=>{location.hash=route},route);
      await page.waitForFunction(route=>!document.getElementById('view-'+route).hidden,route);
      const dims=await page.evaluate(()=>({viewport:innerWidth,scroll:document.documentElement.scrollWidth}));
      assert(dims.scroll<=dims.viewport,`overflow ${route} at ${width}: ${JSON.stringify(dims)}`);
    }
  }
  // Unknown hash must safely fall back to overview.
  await page.evaluate(()=>{location.hash='__proto__'});
  await page.waitForFunction(()=>!document.getElementById('view-overview').hidden);
  const filePage=await context.newPage(); filePage.on('pageerror',error=>errors.push(error.stack || error.message));
  await filePage.goto(pathToFileURL(path.join(ROOT,'index.html')).href,{waitUntil:'load'});
  assert.equal(await filePage.locator('#overview-list .job-row').count(),6);
  await filePage.click('a.nav-item[data-route="practice"]');
  assert.equal(await filePage.locator('#demo-summary .demo-total strong').first().innerText(),'18');
  assert.deepEqual(errors,[]); assert.deepEqual(external,[]);
  await browser.close();
  console.log('✓ Browser navigation, search, filters, ranking, demo, QA, persistence, download, 6 viewport widths, file:// and no external requests');
})().catch(async e=>{console.error(e);if(browser) await browser.close();process.exitCode=1});
