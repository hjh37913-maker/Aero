/* Local-only research workspace. No network integrations or client actions. */
(() => {
  'use strict';
  const core = window.WorkerCore;
  const snapshot = window.WORKER_SNAPSHOT;
  const seed = window.WORKER_DEMO;
  if (!core || !snapshot || !seed) return;
  const jobs = snapshot.jobs;
  const ranked = core.rank(jobs);
  const keys = { selected: 'ai-worker:selected:v1', checks: 'ai-worker:checks:v1' };
  const checkItems = [
    ['source', 'Проверить источник и статус', 'Открыть оригинал вручную и не принимать старый индекс за текущий статус.'],
    ['synthetic', 'Убедиться, что данные вымышлены', 'В образце нет настоящих имён, клиентских материалов и доступов.'],
    ['totals', 'Сверить суммы по категориям и месяцам', 'Для исходных данных: ноябрь — 18; декабрь — 3. Ноль расхождения не ловит все ошибки.'],
    ['limits', 'Проверить пределы таблицы', '100 кодов, 1 000 строк; вставка может обойти проверку ввода.'],
    ['compat', 'Открыть в своём редакторе', 'Excel / Google Sheets не тестировались здесь: требуется отдельная проверка пересчёта и совместимости.']
  ];
  const storage = {
    get(key) { try { return localStorage.getItem(key); } catch (_) { return null; } },
    set(key, value) { try { localStorage.setItem(key, value); return true; } catch (_) { return false; } },
    remove(key) { try { localStorage.removeItem(key); return true; } catch (_) { return false; } }
  };
  function getChecks() {
    try {
      const parsed = JSON.parse(storage.get(keys.checks) || '[]');
      return new Set(Array.isArray(parsed) ? parsed.filter(x => checkItems.some(c => c[0] === x)) : []);
    } catch (_) { return new Set(); }
  }
  const storedSelected = storage.get(keys.selected);
  const state = {
    selected: jobs.some(j => j.id === storedSelected) ? storedSelected : ranked[0].id,
    checks: getChecks(), filter: 'all', query: '', category: 'all',
    records: seed.records.map(r => ({ ...r })), month: '2026-11'
  };
  const $ = id => document.getElementById(id);
  const esc = value => String(value == null ? '' : value).replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
  const svg = name => `<svg aria-hidden="true"><use href="#i-${name}"></use></svg>`;
  const score = job => core.score(job);
  const selectedJob = () => jobs.find(j => j.id === state.selected) || ranked[0];
  const scoreLabels = ['Ясность брифа', 'Учебная выполнимость', 'Проверяемость', 'Ясность экономики'];
  const routeLabels = { overview: 'Обзор', catalog: 'Поиск', ranking: 'Оценка', brief: 'Разбор', practice: 'Практика', quality: 'Проверка', control: 'Контроль' };

  function safeSource(url) {
    try {
      const parsed = new URL(url);
      return parsed.protocol === 'https:' && ['freelancehunt.com', 'projects.co.id'].includes(parsed.hostname) ? parsed.href : '';
    } catch (_) { return ''; }
  }
  function status(job) {
    return job.verification === 'direct_open'
      ? '<span class="status-label direct">Открыто 21.11</span>'
      : '<span class="status-label">Статус не подтверждён</span>';
  }
  function icon(job) {
    if (job.category === 'Таблицы') return ['chart', ''];
    if (job.category === 'Презентации') return ['file', 'purple'];
    if (job.category === 'Видео') return ['spark', 'sand'];
    if (job.category === 'Дизайн') return ['spark', 'purple'];
    if (job.category === 'Документы' || job.category === 'Редактура') return ['brief', 'blue'];
    return ['grid', 'blue'];
  }
  function row(job, full = false) {
    const [symbol, tint] = icon(job);
    return `<div class="job-row" data-observation="${esc(job.id)}">
      <div class="job-name"><span class="job-icon ${tint}">${svg(symbol)}</span><div><button type="button" data-job="${esc(job.id)}">${esc(job.title)}</button><small>${esc(job.platform)} · ${esc(job.category)}</small></div></div>
      <div class="job-budget">${esc(job.budget)}<small>${Number(job.proposals).toLocaleString('ru-RU')} откл. на дату</small></div>
      <div class="job-score"><span class="score-pill">${score(job)}<small>/100</small></span></div>
      ${full ? `<div class="job-status">${status(job)}</div>` : ''}
    </div>`;
  }
  function components(job) {
    return `<div class="score-breakdown">${job.score_parts.map((v, i) => `<div class="score-component"><div><span>${scoreLabels[i]}</span><span>${v}/25</span></div><div class="bar" aria-hidden="true"><span style="width:${v * 4}%"></span></div></div>`).join('')}</div>`;
  }

  function renderOverview() {
    const direct = jobs.filter(j => j.verification === 'direct_open').length;
    const vals = [
      ['В ВЫБОРКЕ', jobs.length, 'реальных страниц', 'grid'],
      ['ОТКРЫТО НА ДАТУ', direct, 'прямая проверка 21.11', 'search'],
      ['СТАТУС НЕ ЯСЕН', jobs.length - direct, 'подробности из индекса', 'clock'],
      ['ЗАЯВОК ОТПРАВЛЕНО', 0, 'в рамках этой работы', 'brief']
    ];
    $('stats').innerHTML = vals.map(([label, n, hint, iconName]) => `<div class="stat"><div class="stat-top"><span>${label}</span>${svg(iconName)}</div><strong>${n}</strong><small>${hint}</small></div>`).join('');
    $('overview-list').innerHTML = ranked.slice(0, 6).map(j => row(j)).join('');
    $('overview-top').innerHTML = `<div class="mini-rank">${ranked.slice(0, 5).map((j, i) => `<div class="mini-row"><span class="rank-number">0${i + 1}</span><button type="button" data-job="${esc(j.id)}">${esc(j.title)}</button><strong>${score(j)}</strong></div>`).join('')}</div>`;
  }
  function renderCatalogList() {
    const filtered = core.filterJobs(jobs, { query: state.query, filter: state.filter, category: state.category });
    $('catalog-count').textContent = `Показано: ${filtered.length} из ${jobs.length}`;
    $('catalog-list').innerHTML = filtered.length ? filtered.map(j => row(j, true)).join('') : '<div class="empty">Совпадений нет. Попробуйте изменить поиск или фильтр.</div>';
    document.querySelectorAll('[data-filter]').forEach(btn => {
      const active = btn.dataset.filter === state.filter;
      btn.classList.toggle('active', active);
      btn.setAttribute('aria-pressed', String(active));
    });
  }
  function renderRanking() {
    $('ranking-list').innerHTML = ranked.slice(0,5).map((j,i) => `<article class="panel rank-card">
      <div class="rank-index">0${i+1}</div>
      <div class="rank-main"><div class="section-kicker">${esc(j.category)} / ${esc(j.platform)}</div><h2>${esc(j.title)}</h2><div class="rank-meta">${esc(j.budget)} · ${esc(j.deadline)}</div><p>${esc(j.requirements)}</p><p class="rank-risk"><strong>Ограничения:</strong> ${esc(j.risk)}</p><button type="button" class="plain-link" data-job="${esc(j.id)}">Открыть разбор ${svg('arrow')}</button></div>
      <div class="rank-metrics"><div class="rank-total"><span>ОЦЕНКА ДЛЯ ИЗУЧЕНИЯ</span><strong>${score(j)}<small>/100</small></strong></div>${components(j)}</div>
    </article>`).join('');
  }
  function renderBrief() {
    const job = selectedJob();
    $('job-picker').value = job.id;
    const direct = job.verification === 'direct_open';
    const evidence = direct
      ? '21.11.2026 страница напрямую показывала Published и открытый приём. Сейчас статус может быть другим.'
      : 'Страница и счётчик видны напрямую; описание, цена и срок — из поискового индекса. Приём и актуальность не подтверждены.';
    const expired = job.deadline_utc && Date.now() > Date.parse(job.deadline_utc)
      ? ' По часам этого устройства срок выбора уже прошёл.' : '';
    const source = safeSource(job.url);
    const sourceLink = source ? `<a href="${esc(source)}" class="button outline" target="_blank" rel="noopener noreferrer">Открыть источник ${svg('external')}</a>` : '';
    $('brief-content').innerHTML = `<div class="brief-grid"><article class="panel brief-main"><div class="brief-titleline"><div><div class="section-kicker">${esc(job.platform)} / ${esc(job.category)}</div><h2>${esc(job.title)}</h2><div class="brief-id">${esc(job.id)} · наблюдение от 21.11.2026</div></div><span class="score-pill">${score(job)}<small>/100</small></span></div>
      <div class="detail-cards"><div><small>БЮДЖЕТ В СРЕЗЕ</small><strong>${esc(job.budget)}</strong></div><div><small>СРОК ВЫБОРА / ОТКЛИКА</small><strong>${esc(job.deadline)}</strong></div><div><small>СРОК ВЫПОЛНЕНИЯ</small><strong>${esc(job.work_deadline)}</strong></div></div>
      <div class="brief-block"><h3>Что известно о задаче</h3><p>${esc(job.requirements)}</p></div>
      <div class="brief-block"><h3>Экономика без обещаний</h3><p>${esc(job.economics)}</p></div>
      <div class="brief-block"><h3>Подтверждение и ограничения</h3><p>${esc(evidence + expired)} ${esc(job.risk)} Откликов на дату: ${Number(job.proposals).toLocaleString('ru-RU')}.</p></div>
      <div class="brief-links">${sourceLink}<a href="#ranking" class="button text-button">Метод оценки ${svg('arrow')}</a></div></article>
      <article class="panel brief-side"><div class="section-kicker">ПЛАН ПРОВЕРКИ / ВРУЧНУЮ</div><h2>Прежде чем делать вывод</h2><ol class="steps"><li>Проверить на источнике, существует ли проект и открыт ли сейчас приём; не путать дедлайн выбора со сроком выполнения.</li><li>Уточнить объём, язык, исходные материалы, критерии приёмки и допустимость ИИ; не загружать частные данные.</li><li>Рассчитать время, расходы, комиссию и налоги отдельно. Объявленная сумма не равна чистому доходу.</li><li>Проверить возраст, право пользования площадкой, договор, оплату и права на результат независимо от балла.</li></ol><div class="blocked"><strong>${svg('lock')} Действия с клиентом отключены</strong><p>Это учебный разбор, не допуск к заявке или передаче материалов.</p><button class="button outline full" type="button" disabled>Отправить отклик</button></div></article></div>`;
  }
  function renderDemo() {
    let summary;
    try { summary = core.summarize(state.records, state.month, seed.causes); }
    catch (_) {
      $('demo-summary').innerHTML = '<div class="empty">Укажите корректный месяц с 2000 по 2100 год.</div>';
      $('demo-chart').innerHTML = '';
      $('demo-records').innerHTML = '';
      return;
    }
    $('demo-summary').innerHTML = `<div class="demo-totals"><div class="demo-total"><span>ВСЕГО ЕДИНИЦ</span><strong>${summary.total.toLocaleString('ru-RU')}</strong></div><div class="demo-total"><span>УЧЕБНЫХ ЗАПИСЕЙ</span><strong>${summary.rows.length}</strong></div><div class="demo-total"><span>УСЛОВНЫХ КОДОВ</span><strong>${Object.keys(summary.workers).length}</strong></div></div>`;
    const max = Math.max(1, ...Object.values(summary.totals));
    $('demo-chart').innerHTML = seed.causes.map(c => `<div class="chart-row"><span>${esc(c)}</span><div class="chart-track" aria-hidden="true"><span style="width:${summary.totals[c] / max * 100}%"></span></div><strong>${summary.totals[c].toLocaleString('ru-RU')}</strong></div>`).join('');
    const recent = summary.rows.slice().sort((a,b) => b.date.localeCompare(a.date)).slice(0, 12);
    $('demo-records').innerHTML = `<div class="records"><div class="records-title"><span>ЗАПИСИ ЗА ВЫБРАННЫЙ МЕСЯЦ</span><span>${summary.rows.length > 12 ? 'Последние 12' : `${summary.rows.length} шт.`}</span></div>${recent.length ? recent.map(r => `<div class="record-row"><span>${esc(r.date)}</span><span>${esc(r.worker)}</span><span>${esc(r.cause)}</span><strong>${r.count}</strong></div>`).join('') : '<div class="empty">За этот месяц учебных записей нет.</div>'}</div>`;
  }
  function updateQualityProgress() {
    $('check-count').textContent = `${state.checks.size} / ${checkItems.length}`;
    $('check-progress').style.width = `${state.checks.size / checkItems.length * 100}%`;
  }
  function renderQuality() {
    updateQualityProgress();
    $('quality-checklist').innerHTML = checkItems.map(([id, title, detail]) => `<label class="check-item"><input type="checkbox" data-check="${id}" ${state.checks.has(id) ? 'checked' : ''}><span><strong>${title}</strong><small>${detail}</small></span></label>`).join('');
  }
  function chooseJob(id) {
    if (!jobs.some(j => j.id === id)) return;
    state.selected = id;
    storage.set(keys.selected, id);
    renderBrief();
    if (location.hash === '#brief') { showRoute(); window.scrollTo(0,0); }
    else location.hash = 'brief';
  }
  function showRoute() {
    let route = location.hash.slice(1) || 'overview';
    if (!Object.prototype.hasOwnProperty.call(routeLabels, route)) route = 'overview';
    document.querySelectorAll('.view').forEach(view => { view.hidden = view.id !== `view-${route}`; });
    document.querySelectorAll('.nav-item').forEach(link => {
      const active = link.dataset.route === route;
      link.classList.toggle('active', active);
      if (active) link.setAttribute('aria-current', 'page'); else link.removeAttribute('aria-current');
    });
    $('breadcrumb').textContent = routeLabels[route];
    document.title = `${routeLabels[route]} — AI WORKER`;
    if (route === 'brief') renderBrief();
    if (route === 'practice') renderDemo();
    if (route === 'quality') renderQuality();
  }
  function setMessage(id, text, isError = false) {
    $(id).textContent = text;
    $(id).classList.toggle('error', isError);
  }

  const categories = [...new Set(jobs.map(j => j.category))].sort((a,b) => a.localeCompare(b, 'ru'));
  $('category').innerHTML += categories.map(c => `<option value="${esc(c)}">${esc(c)}</option>`).join('');
  $('job-picker').innerHTML = ranked.map(j => `<option value="${esc(j.id)}">${esc(j.title)} — ${esc(j.platform)}</option>`).join('');
  $('demo-worker').innerHTML = Array.from({ length: 100 }, (_,i) => `<option>P-${String(i+1).padStart(3,'0')}</option>`).join('');
  $('demo-cause').innerHTML = seed.causes.map(c => `<option value="${esc(c)}">${esc(c)}</option>`).join('');
  $('search').addEventListener('input', e => { state.query = e.target.value; renderCatalogList(); });
  $('category').addEventListener('change', e => { state.category = e.target.value; renderCatalogList(); });
  $('job-picker').addEventListener('change', e => { state.selected = e.target.value; storage.set(keys.selected, state.selected); renderBrief(); });
  $('demo-month').addEventListener('input', e => { state.month = e.target.value; renderDemo(); });
  $('demo-form').addEventListener('submit', e => {
    e.preventDefault();
    const rawCount = $('demo-count').value.trim();
    const record = { date: $('demo-date').value, worker: $('demo-worker').value, cause: $('demo-cause').value, count: rawCount === '' ? NaN : Number(rawCount) };
    if (!core.validateRecord(record, seed.causes)) { setMessage('demo-message', 'Проверьте дату, код, причину и целое количество от 0 до 100 000.', true); return; }
    if (state.records.length >= 200) { setMessage('demo-message', 'В интерактивном примере допустимо не более 200 записей за сеанс.', true); return; }
    state.records.push(record);
    state.month = record.date.slice(0,7);
    $('demo-month').value = state.month;
    setMessage('demo-message', 'Вымышленная запись добавлена только на эту страницу. XLSX не изменён.');
    renderDemo();
  });
  $('reset-demo').addEventListener('click', () => {
    state.records = seed.records.map(r => ({ ...r })); state.month = '2026-11'; $('demo-month').value = state.month;
    setMessage('demo-message', 'Начальные вымышленные данные восстановлены.'); renderDemo();
  });
  $('run-check').addEventListener('click', () => {
    const result = $('check-result');
    try {
      const nov = core.summarize(seed.records, '2026-11', seed.causes);
      const dec = core.summarize(seed.records, '2026-12', seed.causes);
      const expected = [4,2,4,2,3,3];
      const ok = nov.total === 18 && dec.total === 3 && nov.rows.length === 8 &&
        seed.causes.every((c,i) => nov.totals[c] === expected[i]) &&
        nov.workers['P-001'] === 7 && nov.workers['P-002'] === 6 && nov.workers['P-003'] === 5 &&
        !core.validateRecord({ date:'2026-11-03', worker:'P-001', cause:seed.causes[0], count:-1 }, seed.causes);
      if (!ok) throw new Error('mismatch');
      result.textContent = 'Пройдено: ноябрь 18, декабрь 3, категории и коды совпадают. XLSX этим тестом не проверялся.';
      result.className = 'check-result success';
    } catch (_) { result.textContent = 'Проверка не прошла. Не полагайтесь на результаты до исправления.'; result.className = 'check-result fail'; }
  });
  $('clear-checks').addEventListener('click', () => { state.checks.clear(); storage.remove(keys.checks); renderQuality(); });
  $('reset-storage').addEventListener('click', () => {
    const a = storage.remove(keys.selected); const b = storage.remove(keys.checks);
    state.selected = ranked[0].id; state.checks.clear(); renderBrief(); renderQuality();
    setMessage('storage-message', a && b ? 'Выбор и отметки удалены из этого браузера.' : 'Состояние сброшено в памяти; хранилище браузера недоступно.');
  });
  document.addEventListener('click', e => {
    const pick = e.target.closest('[data-job]');
    if (pick) { chooseJob(pick.dataset.job); return; }
    const filter = e.target.closest('[data-filter]');
    if (filter) { state.filter = filter.dataset.filter; renderCatalogList(); }
  });
  document.addEventListener('change', e => {
    const box = e.target.closest('[data-check]');
    if (!box) return;
    if (box.checked) state.checks.add(box.dataset.check); else state.checks.delete(box.dataset.check);
    storage.set(keys.checks, JSON.stringify([...state.checks])); updateQualityProgress();
  });
  window.addEventListener('hashchange', () => { showRoute(); window.scrollTo(0, 0); });
  renderOverview(); renderCatalogList(); renderRanking(); renderBrief(); renderDemo(); renderQuality(); showRoute();
})();
