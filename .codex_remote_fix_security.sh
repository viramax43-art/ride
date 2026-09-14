#!/bin/sh
set -e
cd /var/www/rideminiapp/backend
python3 - <<'PY'
from pathlib import Path
import re

path = Path("app/core/security.py")
text = path.read_text()
pattern = r"def create_access_token\(subject: Any, role: str\) -> str:\n.*?^def hash_admin_key\(raw_key: str\) -> str:\n"
replacement = """def create_access_token(subject: Any, role: str) -> str:\n    \"\"\"\n    Создает JWT access_token без срока истечения.\n\n    :param subject: Идентификатор пользователя (или другие данные), который будет храниться в 'sub' claim.\n    \"\"\"\n    to_encode = {\"sub\": str(subject), \"role\": role}\n    encoded_jwt = jwt.encode(\n        to_encode, settings.secret_key, algorithm=settings.algorithm\n    )\n    return encoded_jwt\n\n\ndef hash_admin_key(raw_key: str) -> str:\n"""
new_text, count = re.subn(pattern, replacement, text, flags=re.S | re.M)
if count != 1:
    raise SystemExit(f"expected 1 replacement, got {count}")
path.write_text(new_text)
print("security.py fixed")
PY
docker compose restart api
