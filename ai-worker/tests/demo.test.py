#!/usr/bin/env python3
"""Inspect XLSX structure and cached results using only Python's standard library.

A cache/structure test is not a spreadsheet recalculation or Excel compatibility test.
"""
import json
import re
import zipfile
from collections import defaultdict
from datetime import datetime, timedelta
from pathlib import Path
from xml.etree import ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
seed=json.loads((ROOT/'demo/seed.json').read_text(encoding='utf-8'))
path=ROOT/'demo/Учебный-учёт.xlsx'
ns={'m':'http://schemas.openxmlformats.org/spreadsheetml/2006/main',
    'r':'http://schemas.openxmlformats.org/officeDocument/2006/relationships',
    'p':'http://schemas.openxmlformats.org/package/2006/relationships'}
with zipfile.ZipFile(path) as z:
    assert z.testzip() is None
    names=z.namelist()
    assert not any('externalLink' in n or 'vbaProject' in n or 'connections.xml' in n for n in names)
    for name in (n for n in names if n.endswith('.rels')):
        assert b'TargetMode="External"' not in z.read(name), f'external relationship: {name}'
    workbook=ET.fromstring(z.read('xl/workbook.xml'))
    relationships=ET.fromstring(z.read('xl/_rels/workbook.xml.rels'))
    rels={r.attrib['Id']:r.attrib['Target'] for r in relationships}
    sheet_paths={s.attrib['name']:'xl/'+rels[s.attrib['{%s}id'%ns['r']]] for s in workbook.findall('m:sheets/m:sheet',ns)}
    assert list(sheet_paths)==['Начало','Справочник','Данные','Сводка']
    assert workbook.find('m:calcPr',ns).attrib.get('fullCalcOnLoad')=='1'
    named={x.attrib['name']:x.text for x in workbook.findall('m:definedNames/m:definedName',ns)}
    assert named['УчебныеКоды']=="'Справочник'!$A$5:$A$104"
    assert named['УчебныеПричины']=="'Справочник'!$C$5:$C$10"
    strings_root=ET.fromstring(z.read('xl/sharedStrings.xml'))
    strings=[''.join(t.text or '' for t in si.findall('.//m:t',ns)) for si in strings_root.findall('m:si',ns)]
    sheets={name:ET.fromstring(z.read(p)) for name,p in sheet_paths.items()}
    cells={name:{c.attrib['r']:c for c in tree.findall('.//m:sheetData/m:row/m:c',ns)} for name,tree in sheets.items()}
    def value(sheet, address):
        c=cells[sheet].get(address)
        if c is None: return None
        v=c.find('m:v',ns)
        if v is None: return None
        if c.attrib.get('t')=='s': return strings[int(v.text)]
        if c.attrib.get('t')=='str': return v.text or ''
        number=float(v.text)
        return int(number) if number.is_integer() else number
    def formula(sheet,address):
        c=cells[sheet][address]
        f=c.find('m:f',ns)
        return f.text if f is not None else None
    def serial(iso):
        d=datetime.strptime(iso,'%Y-%m-%d')
        return (d-datetime(1899,12,30)).days
    # Every input row corresponds to the synthetic fixture, with no hidden extras.
    for i, r in enumerate(seed['records'],7):
        assert [value('Данные',f'{col}{i}') for col in 'ABCD']==[serial(r['date']),r['worker'],r['cause'],r['count']]
    assert value('Данные',f"A{7+len(seed['records'])}") is None
    assert all(value('Справочник',f'A{5+i}') == f'P-{i+1:03d}' for i in range(100))
    assert [value('Справочник',f'C{5+i}') for i in range(6)]==seed['causes']
    validations={v.attrib['sqref']:v for v in sheets['Данные'].findall('m:dataValidations/m:dataValidation',ns)}
    assert set(validations)=={'A7:A1006','B7:B1006','C7:C1006','D7:D1006'}
    assert validations['B7:B1006'].find('m:formula1',ns).text=='УчебныеКоды'
    assert validations['C7:C1006'].find('m:formula1',ns).text=='УчебныеПричины'
    assert validations['D7:D1006'].find('m:formula1',ns).text=='0'
    assert validations['D7:D1006'].find('m:formula2',ns).text=='100000'
    month=[r for r in seed['records'] if r['date'].startswith('2026-11')]
    assert value('Сводка','B4')==serial('2026-11-01')
    assert [value('Сводка',c) for c in ('D4','F4','H4')]==[18,18,0]
    for n in range(1,101):
        row=8+n; code=f'P-{n:03d}'
        assert value('Сводка',f'A{row}')==code
        for col,cause in zip('BCDEFG',seed['causes']):
            expected=sum(r['count'] for r in month if r['worker']==code and r['cause']==cause)
            assert value('Сводка',f'{col}{row}')==expected
            f=formula('Сводка',f'{col}{row}')
            assert f and f.startswith('IF(ISNUMBER($B$4),SUMIFS(')
            assert f"$A{row}" in f and f'{col}$8' in f and '$D$7:$D$1006' in f
            assert 'DATE(YEAR($B$4),MONTH($B$4),1)' in f
            assert 'DATE(YEAR($B$4),MONTH($B$4)+1,1)' in f
        assert value('Сводка',f'H{row}')==sum(r['count'] for r in month if r['worker']==code)
        assert formula('Сводка',f'H{row}')==f'IF(ISNUMBER($B$4),SUM(B{row}:G{row}),"")'
    for col,cause in zip('BCDEFG',seed['causes']):
        assert value('Сводка',f'{col}110')==sum(r['count'] for r in month if r['cause']==cause)
        assert f'SUM({col}9:{col}108)' in formula('Сводка',f'{col}110')
    assert value('Сводка','H110')==18
    assert 'D4-F4' in formula('Сводка','H4')
print('✓ Workbook ZIP, no external relationships, validations, 600 formulas and cached synthetic totals')
