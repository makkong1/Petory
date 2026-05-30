# Step 1: Python 서버 뼈대 — 디렉토리 구조 + requirements.txt + health check

## 목표
`petory-nlp-server/` 디렉토리를 Petory 프로젝트 루트에 생성하고,
FastAPI 서버 뼈대와 `GET /health` 엔드포인트를 구현한다.
이후 Step들이 이 구조 위에 모듈을 추가한다.

## 배경
- Petory 루트: `/Users/maknkkong/project/Petory/`
- Python FastAPI 서버는 `localhost:8000`에서 실행
- Spring(8080) → Python(8000) → MySQL(3306) 구조
- Python 서버는 NLP 분석만 담당, DB 접근 없음

## 생성할 디렉토리 구조

```
petory-nlp-server/
 ├─ app/
 │   ├─ __init__.py
 │   ├─ main.py
 │   ├─ config.py
 │   ├─ api/
 │   │   └─ __init__.py
 │   ├─ schemas/
 │   │   └─ __init__.py
 │   ├─ nlp/
 │   │   └─ __init__.py
 │   ├─ rules/
 │   │   └─ __init__.py
 │   └─ data/
 ├─ tests/
 │   └─ __init__.py
 ├─ requirements.txt
 └─ README.md
```

## 생성할 파일

### `petory-nlp-server/requirements.txt`

```
fastapi==0.115.0
uvicorn[standard]==0.30.6
pydantic==2.8.2
kiwipiepy==0.18.1
sentence-transformers==3.1.1
scikit-learn==1.5.2
PyYAML==6.0.2
httpx==0.27.2
```

### `petory-nlp-server/app/config.py`

```python
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    app_name: str = "Petory NLP Server"
    debug: bool = False
    confidence_threshold: float = 0.55

settings = Settings()
```

> pydantic_settings가 없으면 requirements.txt에 `pydantic-settings==2.4.0` 추가

### `petory-nlp-server/app/main.py`

```python
from fastapi import FastAPI
from app.config import settings

app = FastAPI(title=settings.app_name)

@app.get("/health")
def health():
    return {"status": "ok", "service": settings.app_name}
```

### `petory-nlp-server/README.md`

```markdown
# Petory NLP Server

한국어 반려생활 의도 분석 FastAPI 서버

## 실행
```bash
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

## 엔드포인트
- `GET /health` — 서버 상태 확인
- `POST /api/pet-intent/analyze` — 반려생활 의도 분석 (Step 4에서 구현)
```

## Acceptance Criteria

```bash
cd /Users/maknkkong/project/Petory/petory-nlp-server
python -m venv venv && source venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 127.0.0.1 --port 8000 &
sleep 2
curl http://localhost:8000/health
# 기대: {"status":"ok","service":"Petory NLP Server"}
```
