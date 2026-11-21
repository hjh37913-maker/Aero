#!/usr/bin/env python3
"""Check the snapshot, build outputs and review boundaries without third-party packages."""
import json
import re
import subprocess
from pathlib import Path
from urllib.parse import urlparse

ROOT = Path(__file__).resolve().parents[1]
snapshot = json.loads((ROOT/'data/opportunities.json').read_text(encoding='utf-8'))
jobs = snapshot['jobs']
assert snapshot['snapshot_utc'] == '2026-11-21'
assert len(jobs) == 20
assert len({j['id'] for j in jobs}) == 20
assert len({j['url'] for j in jobs}) == 20
assert sum(j['verification']=='direct_open' for j in jobs) == 7
assert sum(j['verification']=='index_only' for j in jobs) == 13
for j in jobs:
    p=urlparse(j['url'])
    assert p.scheme=='https' and p.hostname in ('projects.co.id','freelancehunt.com')
    assert j['observed_at']=='2026-11-21'
    assert all(isinstance(x,int) and 0 <= x <= 25 for x in j['score_parts']) and len(j['score_parts'])==4
    assert j['platform'] == ('Projects.co.id' if p.hostname=='projects.co.id' else 'Freelancehunt')
    assert j['verification'] == ('direct_open' if p.hostname=='projects.co.id' else 'index_only')
    assert (j['deadline_utc'] is not None) == (p.hostname=='projects.co.id')
    assert isinstance(j['proposals'],int) and j['proposals'] >= 0
    for field in ('title','requirements','difficulty','risk','economics','deadline','work_deadline','budget'):
        assert j[field]
    assert j['url'] in (ROOT/'ОБЗОР-20.md').read_text(encoding='utf-8')
report=(ROOT/'ОБЗОР-20.md').read_text(encoding='utf-8')
assert len(re.findall(r'^### \d+\. ', report, flags=re.M)) == 20
assert 'статус не подтверждён' in report and 'не список доступных заказов' in report
before = [(ROOT/p).read_bytes() for p in ('data/bundle.js','ОБЗОР-20.md')]
subprocess.run(['python3',str(ROOT/'tools/build-data.py')], check=True, capture_output=True)
after = [(ROOT/p).read_bytes() for p in ('data/bundle.js','ОБЗОР-20.md')]
assert before == after, 'Generated data or report was stale'
print('✓ 20 unique sources, 7 direct and 13 index, metadata, report and reproducible bundle')
