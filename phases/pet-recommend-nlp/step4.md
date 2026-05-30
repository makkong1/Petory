# Step 4: 규칙 + API 엔드포인트 — category_rules + urgency_rules + POST /api/pet-intent/analyze

## 목표
의도 도메인 → Petory LocationService 카테고리 매핑 규칙과
긴급도 판단 규칙을 구현하고, `POST /api/pet-intent/analyze` 엔드포인트를 완성한다.

## 배경
- Step 3의 NLP 코어(intent_classifier, tag_extractor)를 사용
- `recommendedCategories`는 Petory DB의 `location_service.category1/2/3` 문자열과 정확히 일치해야 함
- confidence < 0.55 이면 `recommendedCategories: []`, `suggestedCategories` 반환
- `intentDomain == MEDICAL`이면 응답 message에 의료 안전 문구 포함 필수

## 도메인 → 카테고리 매핑 (Petory DB 기준)

```
MEDICAL          → ["동물병원", "동물약국"]
GROOMING         → ["미용"]
SUPPLIES         → ["반려동물용품"]
FOOD_SNACK       → ["반려동물용품"]
WALK_OUTING      → ["여행지", "반려동반여행"]
CAFE_DINING      → ["카페", "식당"]
LODGING_TRAVEL   → ["호텔", "펜션"]
DAYCARE_BOARDING → ["위탁관리"]
CULTURE_SPACE    → ["반려문화시설", "미술관", "박물관", "문예회관"]
```

## 생성할 파일

### `petory-nlp-server/app/rules/category_rules.py`

```python
from typing import List

DOMAIN_TO_CATEGORIES: dict[str, List[str]] = {
    "MEDICAL":          ["동물병원", "동물약국"],
    "GROOMING":         ["미용"],
    "SUPPLIES":         ["반려동물용품"],
    "FOOD_SNACK":       ["반려동물용품"],
    "WALK_OUTING":      ["여행지", "반려동반여행"],
    "CAFE_DINING":      ["카페", "식당"],
    "LODGING_TRAVEL":   ["호텔", "펜션"],
    "DAYCARE_BOARDING": ["위탁관리"],
    "CULTURE_SPACE":    ["반려문화시설", "미술관", "박물관", "문예회관"],
}

ALL_CATEGORIES = list({cat for cats in DOMAIN_TO_CATEGORIES.values() for cat in cats})

def get_categories(intent_domain: str) -> List[str]:
    return DOMAIN_TO_CATEGORIES.get(intent_domain, [])

def get_suggested_categories(top_n: int = 3) -> List[str]:
    return ["동물병원", "미용", "반려동물용품"][:top_n]
```

### `petory-nlp-server/app/rules/urgency_rules.py`

```python
_HIGH_URGENCY_KEYWORDS = [
    "응급", "위급", "쓰러", "피", "출혈", "숨", "호흡", "경련", "발작",
    "못 먹", "이틀", "사흘", "며칠째", "계속", "심해"
]

_LOW_URGENCY_DOMAINS = {
    "SUPPLIES", "FOOD_SNACK", "WALK_OUTING", "CAFE_DINING",
    "LODGING_TRAVEL", "CULTURE_SPACE"
}

def judge_urgency(text: str, intent_domain: str) -> str:
    if intent_domain in _LOW_URGENCY_DOMAINS:
        return "LOW"
    for kw in _HIGH_URGENCY_KEYWORDS:
        if kw in text:
            return "HIGH"
    return "NORMAL"
```

### `petory-nlp-server/app/api/pet_intent_router.py`

```python
from fastapi import APIRouter
from app.schemas.request import PetIntentAnalyzeRequest
from app.schemas.response import PetIntentAnalyzeResponse, IntentDomain, Urgency
from app.nlp.intent_classifier import classify
from app.nlp.tag_extractor import extract_tags
from app.nlp.tokenizer import extract_keywords
from app.rules.category_rules import get_categories, get_suggested_categories
from app.rules.urgency_rules import judge_urgency
from app.config import settings

router = APIRouter(prefix="/api/pet-intent", tags=["pet-intent"])

MEDICAL_SAFETY_MSG = "정확한 진단은 수의사 상담이 필요합니다. 증상이 심하거나 지속된다면 가까운 동물병원에 문의하세요."

@router.post("/analyze", response_model=PetIntentAnalyzeResponse)
def analyze(req: PetIntentAnalyzeRequest):
    intent, domain, confidence = classify(req.text)
    keywords = extract_keywords(req.text)
    intent_tags = extract_tags(req.text, domain)
    urgency = judge_urgency(req.text, domain)

    if confidence < settings.confidence_threshold:
        return PetIntentAnalyzeResponse(
            intentDomain=IntentDomain.UNKNOWN,
            intent="UNKNOWN",
            recommendedCategories=[],
            confidence=round(confidence, 4),
            keywords=keywords,
            intentTags=intent_tags,
            urgency=Urgency.NORMAL,
            message="입력하신 내용으로 적합한 서비스를 찾기 어렵습니다. 카테고리를 직접 선택해 주세요.",
            suggestedCategories=get_suggested_categories(),
        )

    categories = get_categories(domain)
    if domain == "MEDICAL":
        message = f"{', '.join(intent_tags) or '관련'} 불편 표현이 감지되었습니다. {MEDICAL_SAFETY_MSG}"
    else:
        message = f"'{req.text}' 입력을 바탕으로 {', '.join(categories)} 카테고리를 추천합니다."

    return PetIntentAnalyzeResponse(
        intentDomain=IntentDomain(domain),
        intent=intent,
        recommendedCategories=categories,
        confidence=round(confidence, 4),
        keywords=keywords,
        intentTags=intent_tags,
        urgency=Urgency(urgency),
        message=message,
    )
```

### `petory-nlp-server/app/main.py` (라우터 등록으로 업데이트)

```python
from fastapi import FastAPI
from app.config import settings
from app.api.pet_intent_router import router as pet_intent_router

app = FastAPI(title=settings.app_name)
app.include_router(pet_intent_router)

@app.get("/health")
def health():
    return {"status": "ok", "service": settings.app_name}
```

## Acceptance Criteria

```bash
cd /Users/maknkkong/project/Petory/petory-nlp-server
source venv/bin/activate
uvicorn app.main:app --reload --host 127.0.0.1 --port 8000 &
sleep 3

curl -X POST http://localhost:8000/api/pet-intent/analyze \
  -H "Content-Type: application/json" \
  -d '{"text": "우리 강아지가 귀를 자꾸 긁어요", "petType": "DOG"}'
```

기대 응답:
```json
{
  "intentDomain": "MEDICAL",
  "intent": "MEDICAL_CONCERN",
  "recommendedCategories": ["동물병원", "동물약국"],
  "confidence": 0.8 이상,
  "intentTags": ["ear", "scratch"] 중 하나 이상 포함,
  "urgency": "NORMAL",
  "message": "수의사 상담이 필요합니다" 문구 포함
}
```
