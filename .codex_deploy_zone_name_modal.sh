#!/bin/sh
set -eu

ROOT=/var/www/rideminiapp
ASSET_VOLUME=/var/lib/docker/volumes/rideminiapp_frontend_dist/_data/assets
INDEX_VOLUME=/var/lib/docker/volumes/rideminiapp_frontend_dist/_data/index.html
STAMP=20260729a

mkdir -p "$ASSET_VOLUME"
cp "$ROOT/frontend/index.html" "$ROOT/frontend/index.html.before-zone-name-modal"
cp "$INDEX_VOLUME" "$INDEX_VOLUME.before-zone-name-modal"
cp "$ROOT/frontend/dist/assets/index-zone-name-modal.js" "$ASSET_VOLUME/"
cp "$ROOT/frontend/dist/assets/chunk-BXdiCFWD.js" "$ASSET_VOLUME/"
cp "$ROOT/frontend/dist/assets/chunk-j9iVWyIo.js" "$ASSET_VOLUME/"
cp "$ROOT/frontend/dist/assets/main-GwU0wFOJ.css" "$ASSET_VOLUME/"

python3 - <<'PY'
from pathlib import Path
import re

paths = [
    Path('/var/www/rideminiapp/frontend/index.html'),
    Path('/var/www/rideminiapp/frontend/dist/index.html'),
    Path('/var/lib/docker/volumes/rideminiapp_frontend_dist/_data/index.html'),
]

for path in paths:
    text = path.read_text()
    text, bundle_count = re.subn(
        r"var APP_BUNDLE_SRC = '[^']+';",
        "var APP_BUNDLE_SRC = '/assets/index-zone-name-modal.js?v=20260729a';",
        text,
        count=1,
    )
    text, css_count = re.subn(
        r'<link rel="stylesheet" crossorigin href="/assets/[^"]+">',
        '<link rel="stylesheet" crossorigin href="/assets/main-GwU0wFOJ.css?v=20260729a">',
        text,
        count=1,
    )
    if bundle_count != 1 or css_count != 1:
        raise SystemExit(f'Could not update bundle references in {path}')
    path.write_text(text)
PY

grep -n 'APP_BUNDLE_SRC\|main-GwU0wFOJ.css' "$INDEX_VOLUME"
ls -l "$ASSET_VOLUME/index-zone-name-modal.js" "$ASSET_VOLUME/main-GwU0wFOJ.css"
