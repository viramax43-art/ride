#!/bin/sh
set -e
python3 - <<'PY'
import re
from pathlib import Path

bundle = Path('/var/www/rideminiapp/frontend/dist/assets/index-cMcSvF0J.js')
html = Path('/var/www/rideminiapp/frontend/dist/index.html')

text = bundle.read_text()
text = re.sub(r'const _=w\.role==="admin"\|\|w\.role==="moderator"\|\|w\.role==="chief_admin"\|\|b==="shitshibb";', 'const _=!1;', text, count=1)
text = re.sub(r'a\(y==="driver"\?"/driver/register":y==="admin"\?"/admin":"/"\)', 'a(y==="driver"?"/driver/register":"/")', text, count=1)
text = re.sub(r'if\(p\.role==="admin"\|\|p\.role==="moderator"\)\{n\("/admin",\{replace:!0\}\);return\}', '', text, count=1)
text = text.replace('i.jsx(Hi,{path:"/admin",element:i.jsx(EN,{})})', 'i.jsx(Hi,{path:"/admin",element:i.jsx(u8,{to:"/",replace:!0})})')
bundle.write_text(text)

html_text = html.read_text()
old_src = "var APP_BUNDLE_SRC = '/assets/index-C7K9rAeR.js?v=20260724q';"
new_src = "var APP_BUNDLE_SRC = '/assets/index-cMcSvF0J.js?v=20260727a';"
if old_src not in html_text:
    raise SystemExit('missing APP_BUNDLE_SRC entry')
html_text = html_text.replace(old_src, new_src)
html.write_text(html_text)
PY
grep -n -m 5 'shitshibb\|/admin\|APP_BUNDLE_SRC' /var/www/rideminiapp/frontend/dist/index.html /var/www/rideminiapp/frontend/dist/assets/index-cMcSvF0J.js
