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

### `POST /api/pet-intent/analyze`

요청:

```json
{
  "text": "강아지가 귀를 자꾸 긁어요",
  "petType": "DOG"
}
```

응답 예시:

```json
{
  "intentDomain": "MEDICAL",
  "intent": "MEDICAL_CONCERN",
  "recommendedCategories": ["동물병원", "동물약국"],
  "confidence": 0.92,
  "keywords": ["강아지", "귀", "긁"],
  "intentTags": ["ear", "scratch"],
  "urgency": "NORMAL",
  "message": "ear, scratch 불편 표현이 감지되었습니다. 정확한 진단은 수의사 상담이 필요합니다. 증상이 심하거나 지속된다면 가까운 동물병원에 문의하세요."
}
```

`petType`은 현재 요청 계약으로 수신하고 `classify(text, pet_type=None)`까지 전달하지만, 분류 로직에서는 아직 사용하지 않는다. 이후 DOG/CAT별 규칙 분기를 추가할 때 확장한다.

## 분석 방식

- 규칙 기반 키워드 매칭을 먼저 적용하고, rule miss 시 intent example centroid와 문장 임베딩 코사인 유사도를 비교한다.
- rule confidence는 0.88~0.92의 휴리스틱 값이고, embedding confidence는 코사인 유사도다. 두 값은 직접 비교하지 않는다.
- `confidence_threshold=0.45` 미만이면 Python이 `UNKNOWN`을 반환한다. Spring은 signal 저장 시 0.60 미만을 한 번 더 거른다.
- 서버 시작 시 `lifespan`에서 임베딩 모델과 intent centroid를 모두 warm-up한다. 첫 analyze 요청에서 모델 로드와 centroid 계산이 몰리는 것을 줄이기 위한 처리다.

## 구조

```
app/
 ├─ main.py              # FastAPI 앱 진입점, 모델/centroid warm-up
 ├─ config.py            # 설정 (confidence_threshold 등)
 ├─ api/                 # 라우터
 ├─ schemas/             # Pydantic 요청/응답 모델
 ├─ nlp/                 # NLP 코어 (형태소 분석, 임베딩, 분류)
 ├─ rules/               # 카테고리/긴급도 규칙
 └─ data/                # intent_examples.yml, intent_tags.yml
```
