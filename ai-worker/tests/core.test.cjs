'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const core = require('../core.js');
const root = path.resolve(__dirname, '..');
const { jobs } = JSON.parse(fs.readFileSync(path.join(root, 'data/opportunities.json'), 'utf8'));
const seed = JSON.parse(fs.readFileSync(path.join(root, 'demo/seed.json'), 'utf8'));
assert.equal(jobs.length, 20);
assert.deepEqual(core.rank(jobs).slice(0,5).map(j => [j.id,core.score(j)]), [
  ['PC-27641d',75],['FH-1664590',72],['FH-1664077',69],['PC-d7651d',67],['PC-9e651d',65]
]);
assert.equal(core.filterJobs(jobs,{filter:'direct'}).length,7);
assert.equal(core.filterJobs(jobs,{filter:'index'}).length,13);
assert.equal(core.filterJobs(jobs,{filter:'top'}).length,5);
assert.equal(core.filterJobs(jobs,{filter:'top',category:'Таблицы'}).length,2);
assert.equal(core.filterJobs(jobs,{query:'PC-27641d'}).length,1);
assert.equal(core.filterJobs(jobs,{query:'БРАКА'}).length,1);
assert.equal(core.filterJobs(jobs,{query:'<img onerror=alert(1)>'}).length,0);
assert.deepEqual(core.rank([{id:'b',score_parts:[1,1,1,1]},{id:'a',score_parts:[1,1,1,1]}]).map(j=>j.id),['a','b']);
for (const bad of [[-1,0,0,0],[26,0,0,0],[1,2,3],[1.5,2,3,4]]) assert.throws(()=>core.score({score_parts:bad}));
const november = core.summarize(seed.records,'2026-11',seed.causes);
assert.equal(november.total,18); assert.equal(november.rows.length,8);
assert.deepEqual(seed.causes.map(c=>november.totals[c]),[4,2,4,2,3,3]);
assert.deepEqual(november.workers,{'P-001':7,'P-002':6,'P-003':5});
assert.equal(core.summarize(seed.records,'2026-12',seed.causes).total,3);
assert.equal(core.summarize(seed.records,'2027-01',seed.causes).total,0);
const valid = {date:'2026-11-21',worker:'P-100',cause:seed.causes[0],count:100000};
assert(core.validateRecord(valid,seed.causes));
for (const invalid of [
  {...valid,date:'2026-02-30'}, {...valid,date:'1999-12-31'}, {...valid,date:'2101-01-01'},
  {...valid,worker:'P-000'}, {...valid,worker:'P-101'}, {...valid,cause:'other'},
  {...valid,count:-1}, {...valid,count:100001}, {...valid,count:1.5}, {...valid,count:NaN}, {...valid,count:'3'}
]) assert.equal(core.validateRecord(invalid,seed.causes),false);
assert.throws(()=>core.summarize(seed.records,'2026-13',seed.causes));
assert.throws(()=>core.summarize(seed.records,'2026-11',[seed.causes[0],seed.causes[0]]));
console.log('✓ Ranking, filters, validations and synthetic calculations');
