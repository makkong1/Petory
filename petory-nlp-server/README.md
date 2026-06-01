# Petory NLP Server

한국어 반려생활 의도 분석 FastAPI 서버

## 실행

```bash
# petory-nlp-server 디렉터리에서 (최초 1회 venv·pip 자동)
./run.sh
```

프로젝트 루트에서: `npm run nlp`

수동 설정이 필요할 때만:

```bash
python -m venv venv
source venv/bin/activate  # Windows: venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

## 엔드포인트

- `GET /health` — 서버 상태 확인
- `POST /api/pet-intent/analyze` — 반려생활 의도 분석

## 구조

```
app/
 ├─ main.py              # FastAPI 앱 진입점
 ├─ config.py            # 설정 (confidence_threshold 등)
 ├─ api/                 # 라우터
 ├─ schemas/             # Pydantic 요청/응답 모델
 ├─ nlp/                 # NLP 코어 (형태소 분석, 임베딩, 분류)
 ├─ rules/               # 카테고리/긴급도 규칙
 └─ data/                # intent_examples.yml, intent_tags.yml
```
