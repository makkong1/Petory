# Step 2: 스키마 + 의도 예시 데이터

## 목표
Pydantic 요청/응답 스키마와 NLP 학습용 데이터 파일을 작성한다.
`intent_examples.yml`은 의도 분류기의 레퍼런스 문장 집합이고,
`intent_tags.yml`은 의도별 키워드 태그 목록이다.

## 배경
- Step 1에서 생성한 `petory-nlp-server/` 구조 위에 추가
- 의도 도메인 9개: MEDICAL, GROOMING, SUPPLIES, FOOD_SNACK, WALK_OUTING, CAFE_DINING, LODGING_TRAVEL, DAYCARE_BOARDING, CULTURE_SPACE
- `recommendedCategories`는 Petory LocationService의 `category1/category2/category3` 문자열과 일치해야 함
- 도메인 → 카테고리 매핑:
  - MEDICAL → 동물병원, 동물약국
  - GROOMING → 미용
  - SUPPLIES → 반려동물용품
  - FOOD_SNACK → 반려동물용품
  - WALK_OUTING → 여행지, 반려동반여행
  - CAFE_DINING → 카페, 식당
  - LODGING_TRAVEL → 호텔, 펜션
  - DAYCARE_BOARDING → 위탁관리
  - CULTURE_SPACE → 반려문화시설, 미술관, 박물관, 문예회관

## 생성할 파일

### `petory-nlp-server/app/schemas/request.py`

```python
from pydantic import BaseModel
from typing import Optional
from enum import Enum

class PetType(str, Enum):
    DOG = "DOG"
    CAT = "CAT"
    OTHER = "OTHER"

class PetIntentAnalyzeRequest(BaseModel):
    text: str
    petType: Optional[PetType] = None
```

### `petory-nlp-server/app/schemas/response.py`

```python
from pydantic import BaseModel
from typing import List, Optional
from enum import Enum

class IntentDomain(str, Enum):
    MEDICAL = "MEDICAL"
    GROOMING = "GROOMING"
    SUPPLIES = "SUPPLIES"
    FOOD_SNACK = "FOOD_SNACK"
    WALK_OUTING = "WALK_OUTING"
    CAFE_DINING = "CAFE_DINING"
    LODGING_TRAVEL = "LODGING_TRAVEL"
    DAYCARE_BOARDING = "DAYCARE_BOARDING"
    CULTURE_SPACE = "CULTURE_SPACE"
    UNKNOWN = "UNKNOWN"

class Urgency(str, Enum):
    HIGH = "HIGH"
    NORMAL = "NORMAL"
    LOW = "LOW"

class PetIntentAnalyzeResponse(BaseModel):
    intentDomain: IntentDomain
    intent: str
    recommendedCategories: List[str]
    confidence: float
    keywords: List[str]
    intentTags: List[str]
    urgency: Urgency
    message: str
    suggestedCategories: Optional[List[str]] = None
```

### `petory-nlp-server/app/data/intent_examples.yml`

```yaml
intents:
  MEDICAL_CONCERN:
    domain: MEDICAL
    examples:
      - "우리 강아지가 귀를 자꾸 긁어요"
      - "고양이가 밥을 잘 안 먹어요"
      - "강아지 눈에 눈물이 많이 나요"
      - "고양이가 구토를 자꾸 해요"
      - "강아지 발을 자꾸 핥아요"
      - "우리 강아지가 기침을 해요"
      - "강아지 피부에 빨간 발진이 생겼어요"
      - "고양이가 화장실을 못 가요"
      - "강아지가 다리를 절뚝거려요"
      - "고양이 눈이 충혈됐어요"

  GROOMING_NEED:
    domain: GROOMING
    examples:
      - "강아지 털이 너무 엉켰어요"
      - "우리 강아지 목욕 시켜야 해요"
      - "고양이 발톱을 잘라줘야 해요"
      - "강아지 미용 맡길 곳 찾아요"
      - "강아지 털이 너무 많이 빠져요"
      - "우리 강아지 귀 청소 해줘야 해요"
      - "고양이 그루밍 서비스 어디 있어요"
      - "강아지 목욕 + 미용 같이 되는 곳"

  SUPPLIES_NEED:
    domain: SUPPLIES
    examples:
      - "강아지 간식 사러 가야 해요"
      - "고양이 모래 떨어졌어요"
      - "강아지 목줄 새로 사야 해요"
      - "반려동물 용품점 가까운 곳 알려줘"
      - "강아지 장난감 사고 싶어요"
      - "고양이 캣타워 살 곳"
      - "강아지 옷 사려고요"

  FOOD_SNACK_NEED:
    domain: FOOD_SNACK
    examples:
      - "강아지 사료 떨어졌어요"
      - "고양이 습식 사료 파는 곳 찾아요"
      - "강아지한테 좋은 간식 추천"
      - "고양이 츄르 어디서 사요"
      - "강아지 영양제 사야 해요"
      - "반려동물 자연식 파는 곳"

  WALK_OUTING:
    domain: WALK_OUTING
    examples:
      - "강아지랑 산책할 곳 찾아요"
      - "반려동물 입장 가능한 공원"
      - "강아지 데리고 나들이 가고 싶어요"
      - "고양이랑 같이 갈 수 있는 곳"
      - "반려견 동반 여행지 추천"
      - "강아지 뛰어놀 수 있는 야외 공간"
      - "펫 프렌들리 관광지"

  CAFE_DINING:
    domain: CAFE_DINING
    examples:
      - "강아지랑 같이 카페 가고 싶어요"
      - "반려동물 동반 가능한 식당"
      - "강아지 데리고 밥 먹을 곳"
      - "펫 카페 근처에 있어요?"
      - "고양이 카페 가고 싶어요"
      - "반려견 입장 가능한 브런치 카페"
      - "강아지랑 외식할 수 있는 곳"

  LODGING_TRAVEL:
    domain: LODGING_TRAVEL
    examples:
      - "강아지랑 같이 잘 수 있는 펜션"
      - "반려동물 동반 호텔 찾아요"
      - "고양이랑 여행 가려고요"
      - "펫 동반 숙박 가능한 곳"
      - "강아지 데리고 여행 숙소"
      - "반려견 동반 리조트"

  DAYCARE_BOARDING:
    domain: DAYCARE_BOARDING
    examples:
      - "강아지 잠깐 맡길 곳 찾아요"
      - "여행 가는 동안 고양이 맡길 곳"
      - "강아지 유치원 어디 있어요"
      - "반려동물 호텔 예약하고 싶어요"
      - "강아지 위탁 서비스"
      - "고양이 캣시터 구해요"
      - "강아지 데이케어 센터"

  CULTURE_SPACE:
    domain: CULTURE_SPACE
    examples:
      - "반려동물 동반 가능한 미술관"
      - "강아지랑 갈 수 있는 전시회"
      - "펫 프렌들리 문화 공간"
      - "고양이랑 박물관 가고 싶어요"
      - "반려동물 입장 가능한 문화 행사"
      - "강아지랑 공연 보러 가고 싶어요"
```

### `petory-nlp-server/app/data/intent_tags.yml`

```yaml
tags:
  MEDICAL:
    - ear
    - eye
    - skin
    - paw
    - tooth
    - vomit
    - diarrhea
    - itching
    - scratch
    - cough
    - limp
    - appetite_loss
    - discharge
    - swelling

  GROOMING:
    - fur
    - bath
    - nail
    - trim
    - shed
    - tangle
    - ear_cleaning
    - brushing

  SUPPLIES:
    - leash
    - collar
    - toy
    - bed
    - cage
    - clothes
    - accessories
    - carrier

  FOOD_SNACK:
    - kibble
    - wet_food
    - snack
    - supplement
    - natural_food
    - treat

  WALK_OUTING:
    - walk
    - park
    - outdoor
    - exercise
    - outing
    - nature

  CAFE_DINING:
    - cafe
    - restaurant
    - brunch
    - dining
    - pet_friendly

  LODGING_TRAVEL:
    - pension
    - hotel
    - travel
    - overnight
    - resort

  DAYCARE_BOARDING:
    - boarding
    - daycare
    - kindergarten
    - sitter
    - care

  CULTURE_SPACE:
    - museum
    - gallery
    - exhibition
    - culture
    - event
    - performance
```

## Acceptance Criteria

```bash
cd /Users/maknkkong/project/Petory/petory-nlp-server
source venv/bin/activate
python -c "
import yaml
with open('app/data/intent_examples.yml') as f:
    data = yaml.safe_load(f)
    print('intents loaded:', list(data['intents'].keys()))
with open('app/data/intent_tags.yml') as f:
    data = yaml.safe_load(f)
    print('tag domains loaded:', list(data['tags'].keys()))
"
# 기대: 9개 도메인 전부 출력
```
