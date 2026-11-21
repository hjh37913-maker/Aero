/* Pure logic shared by the offline dashboard and its tests. */
(function (root, factory) {
  if (typeof module === 'object' && module.exports) module.exports = factory();
  else root.WorkerCore = factory();
})(typeof globalThis === 'object' ? globalThis : this, function () {
  'use strict';

  function score(job) {
    if (!job || !Array.isArray(job.score_parts) || job.score_parts.length !== 4 ||
        job.score_parts.some(n => !Number.isInteger(n) || n < 0 || n > 25)) {
      throw new Error('Expected four integer scores between 0 and 25');
    }
    return job.score_parts.reduce((a, b) => a + b, 0);
  }

  function rank(jobs) {
    return jobs.slice().sort((a, b) => score(b) - score(a) || a.id.localeCompare(b.id, 'en'));
  }

  function filterJobs(jobs, options = {}) {
    const query = String(options.query || '').trim().toLocaleLowerCase('ru');
    const category = options.category || 'all';
    const filter = options.filter || 'all';
    const topIds = new Set(rank(jobs).slice(0, 5).map(j => j.id));
    return rank(jobs).filter(job => {
      if (filter === 'top' && !topIds.has(job.id)) return false;
      if (filter === 'direct' && job.verification !== 'direct_open') return false;
      if (filter === 'index' && job.verification !== 'index_only') return false;
      if (category !== 'all' && job.category !== category) return false;
      const haystack = [job.title, job.platform, job.id, job.category, job.requirements].join(' ').toLocaleLowerCase('ru');
      return !query || haystack.includes(query);
    });
  }

  function dateValid(value) {
    if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
    const d = new Date(value + 'T00:00:00Z');
    return Number.isFinite(d.getTime()) && d.toISOString().slice(0, 10) === value &&
      value >= '2000-01-01' && value <= '2100-12-31';
  }

  function validateRecord(record, causes) {
    return !!record && dateValid(record.date) && /^P-(?:00[1-9]|0[1-9]\d|100)$/.test(record.worker || '') &&
      Array.isArray(causes) && causes.includes(record.cause) && Number.isInteger(record.count) &&
      record.count >= 0 && record.count <= 100000;
  }

  function summarize(records, month, causes) {
    if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(month) || month < '2000-01' || month > '2100-12') {
      throw new Error('Invalid month');
    }
    if (!Array.isArray(records) || !Array.isArray(causes) || !causes.length ||
        new Set(causes).size !== causes.length || records.some(r => !validateRecord(r, causes))) {
      throw new Error('Invalid synthetic records');
    }
    const selected = records.filter(r => r.date.startsWith(month));
    const totals = Object.fromEntries(causes.map(c => [c, 0]));
    const workers = {};
    for (const record of selected) {
      totals[record.cause] += record.count;
      workers[record.worker] = (workers[record.worker] || 0) + record.count;
    }
    return { total: selected.reduce((a, r) => a + r.count, 0), totals, workers, rows: selected };
  }

  return Object.freeze({ score, rank, filterJobs, dateValid, validateRecord, summarize });
});
