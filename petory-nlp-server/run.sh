#!/usr/bin/env bash
# NLP 서버 실행 (venv activate 불필요)
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT"

PYTHON="${PYTHON:-python3}"
VENV="$ROOT/venv"

if [[ ! -x "$VENV/bin/uvicorn" ]]; then
  echo "[petory-nlp-server] 최초 실행 — venv 생성 및 의존성 설치"
  "$PYTHON" -m venv "$VENV"
  "$VENV/bin/pip" install -r requirements.txt
fi

exec "$VENV/bin/uvicorn" app.main:app --reload --host 127.0.0.1 --port 8000
