#!/usr/bin/env python3
"""Build a self-contained spreadsheet from synthetic fixtures (requires XlsxWriter)."""
import json
from datetime import date, datetime
from pathlib import Path
import xlsxwriter

ROOT = Path(__file__).resolve().parents[1]
FIXTURE = json.loads((ROOT / 'demo/seed.json').read_text(encoding='utf-8'))
OUT = ROOT / 'demo/Учебный-учёт.xlsx'
CAUSES = FIXTURE['causes']
RECORDS = FIXTURE['records']
MONTH = '2026-11'
MAX_RECORDS = 1000

wb = xlsxwriter.Workbook(OUT)
wb.set_properties({'title': 'Учебный учёт — вымышленные данные',
                   'subject': 'Самостоятельный пример без данных заказчика',
                   'created': datetime(2026, 11, 21, 0, 0, 0),
                   'comments': 'Только для внутреннего изучения. Не является выполненным заказом.'})
wb.set_calc_mode('auto')
# Shared styles. Yellow cells are inputs, not calculated fields.
base = {'font_name': 'Calibri', 'font_size': 11, 'font_color': '#253C34'}
def fmt(**kw): return wb.add_format({**base, **kw})
navy = '#183C30'; muted = '#60746A'; lime = '#DDF09E'; pale = '#F3F7F0'
title = fmt(font_size=22, bold=True, font_color='#FFFFFF', bg_color=navy, valign='vcenter')
label = fmt(font_size=9, bold=True, font_color='#658071', bg_color=pale)
header = fmt(bold=True, font_color='#FFFFFF', bg_color=navy, text_wrap=True, valign='vcenter', border_color='#EDF2EC', border=1)
body = fmt(text_wrap=True, valign='vcenter')
small = fmt(font_size=10, font_color=muted, text_wrap=True, valign='vcenter')
accent = fmt(bold=True, font_color=navy, bg_color=lime, text_wrap=True, valign='vcenter')
input_date = fmt(bg_color='#FFF7D7', num_format='dd.mm.yyyy', border=1, border_color='#E7DDB9')
input_text = fmt(bg_color='#FFF7D7', border=1, border_color='#E7DDB9')
input_int = fmt(bg_color='#FFF7D7', border=1, border_color='#E7DDB9', num_format='0')
metric = fmt(font_size=14, bold=True, bg_color='#EAF2E7', align='center', valign='vcenter', num_format='0')
metric_diff = fmt(font_size=14, bold=True, bg_color='#EAF2E7', align='center', valign='vcenter', num_format='0')
cell = fmt(bg_color='#FFFFFF', border=1, border_color='#E7EEE8', num_format='0', align='center')
cell_code = fmt(bg_color='#F5F8F3', border=1, border_color='#E7EEE8', bold=True)
total = fmt(bold=True, bg_color=lime, border=1, border_color='#D2E4B7', num_format='0', align='center')

intro = wb.add_worksheet('Начало')
intro.hide_gridlines(2)
intro.set_tab_color(navy)
intro.set_column('A:A', 3); intro.set_column('B:B', 20); intro.set_column('C:G', 16); intro.set_column('H:H', 3)
intro.set_row(1, 42)
intro.merge_range('B2:G2', 'УЧЕБНЫЙ УЧЁТ БРАКА', title)
intro.set_row(3, 34)
intro.merge_range('B4:G4', 'ВЫМЫШЛЕННЫЕ ДАННЫЕ  •  ВНУТРЕННЕЕ ОБУЧЕНИЕ', accent)
intro.set_row(5, 43)
intro.merge_range('B6:G6', 'Один самостоятельный пример по общей теме учёта. Это не работа для клиента: настоящих данных, вложений, соглашений и передачи нет.', body)
intro.merge_range('B8:G8', 'КАК РАБОТАЕТ', header)
for row, text in [
 (9, '01   Откройте «Данные». Строки 7–1006 рассчитаны на 1 000 учебных записей. Дата, код, причина и количество заполняются в жёлтых ячейках.'),
 (11, '02   Выберите дату нужного месяца в B4 листа «Сводка». Начало и конец месяца определяются формулами независимо от числа в B4.'),
 (13, '03   Для каждого кода P-001…P-100 и каждой причины показана сумма. Верхняя строка сверяет общий итог данных с итогом матрицы.'),
 (15, '04   В примере за ноябрь 2026: всего 18; по причинам — 4, 2, 4, 2, 3, 3. За декабрь — 3. Данные полностью вымышлены.'),
 (17, 'ОГРАНИЧЕНИЯ   Только записи внутри подготовленного диапазона. Вставка через буфер может обойти проверку ввода; пустые/неверные даты и текст вместо чисел нужно обнаруживать вручную. При изменении структуры формулы нужно проверить.'),
 (20, 'ПРАВА И СОВМЕСТИМОСТЬ   Не передавать или применять коммерчески до выяснения применимых условий и прав. Формулы и кэш проверены программно; отдельно проверьте пересчёт в своей версии табличного редактора.')]:
    intro.set_row(row-1, 42 if row != 17 else 55)
    intro.merge_range(row-1, 1, row, 6, text, small if row >= 17 else body)
intro.set_landscape(); intro.fit_to_pages(1,1); intro.set_margins(0.25,0.25,0.3,0.3); intro.print_area('B2:G21')

reference = wb.add_worksheet('Справочник')
reference.hide_gridlines(2); reference.set_tab_color('#A3C94A')
reference.set_column('A:A', 18); reference.set_column('C:C', 25); reference.set_column('D:D', 36)
reference.merge_range('A1:D2', 'СПРАВОЧНИК  /  ТОЛЬКО УЧЕБНЫЕ КОДЫ', title)
reference.write('A4', 'Код', header); reference.write('C4', 'Причина', header)
for i in range(100): reference.write(4+i, 0, f'P-{i+1:03d}', cell_code)
for i,cause in enumerate(CAUSES): reference.write(4+i, 2, cause, body)
reference.write('D4', 'Никаких имён и данных людей.', small)
reference.freeze_panes(4, 0)
wb.define_name('УчебныеКоды', "='Справочник'!$A$5:$A$104")
wb.define_name('УчебныеПричины', "='Справочник'!$C$5:$C$10")

source = wb.add_worksheet('Данные')
source.hide_gridlines(2); source.set_tab_color('#DBBE62')
source.set_column('A:A', 15); source.set_column('B:B', 18); source.set_column('C:C', 24); source.set_column('D:D', 14); source.set_column('E:E', 35)
source.merge_range('A1:E2', 'ДАННЫЕ  /  1 000 СТРОК', title)
source.merge_range('A3:E4', 'Все строки — вымышленные. Жёлтые ячейки можно менять для обучения; персональные или клиентские данные не вводить.', small)
source.set_row(5, 26)
for c,v in enumerate(['Дата','Учебный код','Причина','Количество','Примечание']): source.write(5,c,v,header)
source.freeze_panes(6,0); source.autofilter(5,0,5+MAX_RECORDS,4)
for i in range(MAX_RECORDS):
    row = 6 + i
    source.write_blank(row, 0, None, input_date)
    source.write_blank(row, 1, None, input_text)
    source.write_blank(row, 2, None, input_text)
    source.write_blank(row, 3, None, input_int)
    source.write_blank(row, 4, None, input_text)
for i,record in enumerate(RECORDS):
    row=6+i
    source.write_datetime(row, 0, date.fromisoformat(record['date']), input_date)
    source.write_string(row,1,record['worker'],input_text)
    source.write_string(row,2,record['cause'],input_text)
    source.write_number(row,3,record['count'],input_int)
source.data_validation(6,0,5+MAX_RECORDS,0,{'validate':'date','criteria':'between','minimum':datetime(2000,1,1),'maximum':datetime(2100,12,31),'ignore_blank':True,'error_type':'stop','error_title':'Нужна дата','error_message':'Введите дату между 2000 и 2100 годом.'})
source.data_validation(6,1,5+MAX_RECORDS,1,{'validate':'list','source':'=УчебныеКоды','ignore_blank':True,'error_type':'stop','error_title':'Учебный код','error_message':'Выберите код из списка.'})
source.data_validation(6,2,5+MAX_RECORDS,2,{'validate':'list','source':'=УчебныеПричины','ignore_blank':True,'error_type':'stop','error_title':'Учебная причина','error_message':'Выберите причину из списка.'})
source.data_validation(6,3,5+MAX_RECORDS,3,{'validate':'integer','criteria':'between','minimum':0,'maximum':100000,'ignore_blank':True,'error_type':'stop','error_title':'Только целые числа','error_message':'Введите целое число от 0 до 100000.'})

summary = wb.add_worksheet('Сводка')
summary.activate(); summary.hide_gridlines(2); summary.set_tab_color('#A3C94A')
summary.set_column('A:A', 17); summary.set_column('B:G', 17); summary.set_column('H:H', 15)
summary.set_row(0, 30); summary.set_row(1, 29); summary.set_row(3, 31); summary.set_row(7, 36)
summary.merge_range('A1:H2', 'СВОДКА  /  УЧЕБНЫЙ ПРИМЕР', title)
summary.write('A4','Месяц →',label); summary.write_datetime('B4',date(2026,11,1),input_date)
summary.write('C4','Все данные →',label); summary.write('E4','В таблице →',label); summary.write('G4','Разница →',label)
summary.data_validation('B4', {'validate':'date','criteria':'between','minimum':datetime(2000,1,1),'maximum':datetime(2100,12,31),'ignore_blank':False,'error_type':'stop','error_title':'Нужна дата','error_message':'Введите корректную дату.'})
start='DATE(YEAR($B$4),MONTH($B$4),1)'
end='DATE(YEAR($B$4),MONTH($B$4)+1,1)'
total_formula=f'=IF(ISNUMBER($B$4),SUMIFS(\'Данные\'!$D$7:$D$1006,\'Данные\'!$A$7:$A$1006,">="&{start},\'Данные\'!$A$7:$A$1006,"<"&{end}),"")'
example_total=sum(r['count'] for r in RECORDS if r['date'].startswith(MONTH))
summary.write_formula('D4',total_formula,metric,example_total)
summary.write_formula('F4','=IF(ISNUMBER($B$4),H110,"")',metric,example_total)
summary.write_formula('H4','=IF(ISNUMBER($B$4),D4-F4,"")',metric_diff,0)
summary.conditional_format('H4',{'type':'cell','criteria':'not equal to','value':0,'format':fmt(bold=True,font_color='#A02C25',bg_color='#FFE4DF')})
summary.merge_range('A6:H6', 'Разница не равна нулю? Проверьте коды и причины в данных. Ноль не заменяет ручную проверку всех строк и дат.', small)
summary.write(7,0,'Учебный код',header)
for k,cause in enumerate(CAUSES): summary.write(7,k+1,cause,header)
summary.write(7,7,'ИТОГО',header)
summary.freeze_panes(8,1)
for i in range(100):
    row=8+i; excel_row=row+1; code=f'P-{i+1:03d}'
    summary.write_string(row,0,code,cell_code)
    for col,cause in enumerate(CAUSES,1):
        col_letter=xlsxwriter.utility.xl_col_to_name(col)
        formula=(f'=IF(ISNUMBER($B$4),SUMIFS(\'Данные\'!$D$7:$D$1006,'
                 f'\'Данные\'!$A$7:$A$1006,">="&{start},\'Данные\'!$A$7:$A$1006,"<"&{end},'
                 f'\'Данные\'!$B$7:$B$1006,$A{excel_row},\'Данные\'!$C$7:$C$1006,{col_letter}$8),"")')
        cached=sum(r['count'] for r in RECORDS if r['date'].startswith(MONTH) and r['worker']==code and r['cause']==cause)
        summary.write_formula(row,col,formula,cell,cached)
    worker_total=sum(r['count'] for r in RECORDS if r['date'].startswith(MONTH) and r['worker']==code)
    summary.write_formula(row,7,f'=IF(ISNUMBER($B$4),SUM(B{excel_row}:G{excel_row}),"")',cell,worker_total)
summary.write('A110','ИТОГО',total)
for col in range(1,8):
    letter=xlsxwriter.utility.xl_col_to_name(col)
    cached=(sum(r['count'] for r in RECORDS if r['date'].startswith(MONTH) and r['cause']==CAUSES[col-1]) if col<7 else example_total)
    summary.write_formula(109,col,f'=IF(ISNUMBER($B$4),SUM({letter}9:{letter}108),"")',total,cached)
summary.set_landscape(); summary.fit_to_pages(1,0); summary.repeat_rows(7)
wb.close()
print(f'Built {OUT} ({OUT.stat().st_size} bytes); default November total = {example_total}')
