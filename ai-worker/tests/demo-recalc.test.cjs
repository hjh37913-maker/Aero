'use strict';
// Optional: NODE_PATH must contain xlsx, xlsx-calc and @formulajs/formulajs.
// This is a library recalculation, not a compatibility test in Excel/Sheets.
const assert=require('node:assert/strict');
const path=require('node:path');
const XLSX=require('xlsx');
const calculate=require('xlsx-calc');
calculate.import_functions(require('@formulajs/formulajs'));
// xlsx-calc's built-in YEAR/MONTH expect JS Date, while SheetJS loads Excel serials.
// Test adapter for the workbook's restricted 2000–2100 range (not a general Excel date engine).
const epoch=Date.UTC(1899,11,30), day=86400000;
const excelDate = date => (Date.parse(date+'T00:00:00Z')-epoch)/day;
calculate.set_fx('YEAR', n=>new Date(epoch+Math.floor(n)*day).getUTCFullYear());
calculate.set_fx('MONTH', n=>new Date(epoch+Math.floor(n)*day).getUTCMonth()+1);
calculate.set_fx('DATE',(y,m,d)=>(Date.UTC(y,m-1,d)-epoch)/day);
const open=()=>XLSX.readFile(path.join(__dirname,'../demo/Учебный-учёт.xlsx'));
const get=(w,c)=>w.Sheets['Сводка'][c].v;
let w=open(); calculate(w);
for (const [c,v] of Object.entries({D4:18,F4:18,H4:0,B9:3,C9:2,H9:7,H10:6,H11:5,B110:4,C110:2,D110:4,E110:2,F110:3,G110:3,H110:18})) assert.equal(get(w,c),v,`November ${c}`);
w=open(); w.Sheets['Сводка'].B4.v=excelDate('2026-12-15'); calculate(w);
for (const [c,v] of Object.entries({D4:3,F4:3,H4:0,H9:2,H12:1,B110:2,C110:1,H110:3})) assert.equal(get(w,c),v,`December ${c}`);
w=open();
Object.assign(w.Sheets['Данные'], {
 A1006:{t:'n',v:excelDate('2026-11-30')}, B1006:{t:'s',v:'P-100'}, C1006:{t:'s',v:'Вакуум'}, D1006:{t:'n',v:9},
 A1007:{t:'n',v:excelDate('2026-11-30')}, B1007:{t:'s',v:'P-100'}, C1007:{t:'s',v:'Вакуум'}, D1007:{t:'n',v:99}
});
calculate(w);
for (const [c,v] of Object.entries({D4:27,F4:27,H4:0,G108:9,H108:9,G110:12,H110:27})) assert.equal(get(w,c),v,`Boundary ${c}`);
w=open(); Object.assign(w.Sheets['Данные'], {A17:{t:'n',v:excelDate('2026-11-21')},B17:{t:'s',v:'BAD'},C17:{t:'s',v:'Поставщик'},D17:{t:'n',v:5}}); calculate(w);
for (const [c,v] of Object.entries({D4:23,F4:18,H4:5})) assert.equal(get(w,c),v,`Unmatched code ${c}`);
console.log('✓ Library recalculation: default, changed month, range boundary and unmatched code');
