# Recommendation 도메인

> 기준: 현재 `domain/petRecommendation`, `petory-nlp-server`, `frontend/src/api/petRecommendationApi.js`, `UnifiedMap` 코드.  
> 역할: 사용자의 반려생활 입력을 NLP signal 또는 주변 시설 추천으로 변환한다.

---

## 1. 도메인 책임

Recommendation 도메인은 새로운 장소 DB를 따로 만들지 않는다. 기존 Location 도메인의 장소 검색 위에 사용자 의도 분석 결과를 얹는다.

현재 책임은 두 갈래다.

1. **추천 카드 signal**
   - 게시글, 케어 요청, 위치 검색어를 비동기로 분석한다.
   - 분석 결과를 `user_pet_intent_signal`에 저장한다.
   - 주변서비스 탭에서 `/api/pet-recommend/signals`로 조회해 추천 카드를 표시한다.
   - 카드를 누르면 기존 Location 카테고리 검색으로 연결한다.

2. **즉시 시설 추천 API**
   - `/api/pet-recommend?lat=&lng=&text=&radius=&petType=`로 텍스트와 좌표를 받는다.
   - NLP 서버 분석 결과의 1순위 카테고리로 주변 장소를 조회한다.
   - 인기도, 태그 일치, 거리, 평점, 리뷰 수를 점수화해 상위 시설을 반환한다.

현재 프론트의 주 사용자 경로는 **추천 카드 signal** 중심이다. 즉시 시설 추천 API는 백엔드 API로 존재하지만, `frontend/src/api/petRecommendationApi.js`에는 아직 래퍼가 없다.

---

## 2. 주요 코드 위치

### 2.1 Spring Backend

| 영역 | 파일 |
|---|---|
| API | `domain/petRecommendation/controller/PetRecommendationController.java` |
| NLP HTTP client | `domain/petRecommendation/client/PetIntentClient.java` |
| signal 이벤트 리스너 | `domain/petRecommendation/service/PetIntentSignalEventListener.java` |
| signal 저장/조회 | `domain/petRecommendation/service/UserPetIntentSignalService.java` |
| 즉시 추천 서비스 | `domain/petRecommendation/service/PetRecommendationService.java` |
| 점수 계산 | `domain/petRecommendation/scoring/PetRecommendScoreCalculator.java` |
| 장소 상호작용 | `domain/petRecommendation/service/PlaceInteractionService.java` |
| 건강 알림 | `domain/petRecommendation/service/PetHealthAlertNotificationHandler.java` |
| 전용 async executor | `global/config/PetIntentAsyncConfig.java` |

### 2.2 Python NLP Server

| 영역 | 파일 |
|---|---|
| FastAPI 앱 | `petory-nlp-server/app/main.py` |
| 분석 API | `petory-nlp-server/app/api/pet_intent_router.py` |
| 요청/응답 스키마 | `petory-nlp-server/app/schemas/request.py`, `response.py` |
| 의도 분류 | `petory-nlp-server/app/nlp/intent_classifier.py` |
| 키워드 추출 | `petory-nlp-server/app/nlp/tokenizer.py` |
| 태그 추출 | `petory-nlp-server/app/nlp/tag_extractor.py` |
| 임베딩 모델 | `petory-nlp-server/app/nlp/embedding_model.py` |
| 카테고리/긴급도 규칙 | `petory-nlp-server/app/rules/category_rules.py`, `urgency_rules.py` |
| 학습 예시/태그 데이터 | `petory-nlp-server/app/data/intent_examples.yml`, `intent_tags.yml` |

### 2.3 Frontend

| 영역 | 파일 |
|---|---|
| signal API | `frontend/src/api/petRecommendationApi.js` |
| 주변서비스 화면 | `frontend/src/components/UnifiedMap/UnifiedPetMapPage.js` |
| 추천 카드 UI | `frontend/src/components/UnifiedMap/controls/LocationControls.js` |

---

## 3. API

### 3.1 즉시 추천 API

```http
GET /api/pet-recommend?lat={lat}&lng={lng}&text={text}&radius={radius}&petType={petType}
```

| 파라미터 | 필수 | 설명 |
|---|---:|---|
| `lat` | O | 사용자 기준 위도 |
| `lng` | O | 사용자 기준 경도 |
| `text` | O | 분석할 자연어 텍스트. 최대 500자 |
| `radius` | X | 검색 반경. 기본 3000m |
| `petType` | X | `DOG`, `CAT` 또는 기타. Java client에서 `DOG/CAT` 외 값은 `OTHER`로 정규화 |

흐름:

1. `PetIntentClient.analyze(text, petType)` 호출
2. NLP 실패 시 `fallbackRecommend()` 실행
3. 분석 성공 시 `recommendedCategories[0]`을 primary category로 사용
4. `LocationServiceRepository.findByRadius(..., category, "distance", 20)`
5. 최근 30일 장소 상호작용으로 popularity score 계산
6. `PetRecommendScoreCalculator`로 점수화
7. 점수 내림차순 상위 10개 반환

### 3.2 signal 조회 API

```http
GET /api/pet-recommend/signals
Authorization: Bearer <JWT>
```

로그인 사용자의 만료되지 않은 signal을 최신순 최대 10건 반환한다.

응답 예시:

```json
[
  {
    "intentDomain": "MEDICAL",
    "intent": "MEDICAL_CONCERN",
    "recommendedCategories": ["동물병원", "동물약국"],
    "confidence": 0.92,
    "urgency": "NORMAL",
    "intentTags": ["ear", "scratch"],
    "cardMessage": "최근 건강 관련 고민이 있어 보여요.",
    "actionLabel": "근처 동물병원 보기",
    "targetTab": "location",
    "targetCategory": "동물병원"
  }
]
```

### 3.3 장소 상호작용 API

```http
POST /api/pet-recommend/interact?locationIdx={locationIdx}&type={VIEW|NAVIGATE|FAVORITE}
Authorization: Bearer <JWT>
```

`PlaceInteractionLog`에 사용자-장소 행동을 저장한다. 즉시 추천 API의 popularity score 계산에 사용된다.

현재 프론트 추천 카드 클릭은 `SignalInteractionLog`에 저장되지 않는다. `SignalInteractionLog` 엔티티와 테이블은 준비되어 있지만 저장 API/서비스 호출은 아직 없다.

---

## 4. Signal 수집 흐름

### 4.1 입력 출처

| sourceType | 발행 위치 | 분석 텍스트 | 트랜잭션 |
|---|---|---|---|
| `COMMUNITY` | `BoardService.createBoard()` | `title + " " + content` | `AFTER_COMMIT` |
| `CARE` | `CareRequestService.createCareRequest()` | `title + " " + description`, `petType` 포함 | `AFTER_COMMIT` |
| `LOCATION_SEARCH` | `LocationServiceService.publishSearchEvent()` | 위치 검색 `keyword` | 일반 `@EventListener` |

커뮤니티/케어는 DB 커밋 이후 처리한다. 원 데이터가 롤백됐는데 signal만 남는 dangling signal을 막기 위해서다.

위치 검색은 조회성 이벤트라 트랜잭션 없이 발행된다. 비로그인 사용자는 signal 대상이 아니다.

### 4.2 Location 검색 NLP 호출 필터

위치 검색은 검색어가 남아 있는 상태에서 카테고리, 정렬, 반경 변경으로도 API가 반복 호출될 수 있다. 그래서 NLP 호출 전에 두 단계 필터를 적용한다.

1. 자연어 판단
   - 정규화 후 길이 7자 이상
   - 공백 포함
   - 예: `"강아지 귀 긁어요"`는 통과, `"동물병원"`과 `"귀 치료"`는 skip

2. Redis TTL dedup
   - key: `nlp:loc-dedup:{userIdx}:{normalizedKeyword}`
   - TTL: 10분
   - 같은 사용자와 같은 검색어가 10분 안에 다시 들어오면 Python 호출 skip
   - Redis 장애 시 fail-closed로 Python 호출도 skip

이 필터는 Location 검색에만 적용된다. 게시글/케어 이벤트는 Redis dedup을 사용하지 않는다.

### 4.3 비동기 실행 정책

`PetIntentSignalEventListener`의 세 핸들러는 `@Async("petIntentExecutor")`를 사용한다.

| 설정 | 값 |
|---|---:|
| core pool size | 2 |
| max pool size | 6 |
| queue capacity | 500 |
| thread prefix | `pet-intent-` |
| reject policy | warn 로그 후 폐기 |

추천 signal은 부가 기능이다. executor 큐가 포화되면 일부 분석 작업은 폐기되고, 게시글 작성/케어 요청/위치 검색 자체는 실패하지 않는다.

---

## 5. Python NLP 서버 계약

### 5.1 Endpoint

```http
GET /health
POST /api/pet-intent/analyze
```

요청:

```json
{
  "text": "강아지가 귀를 자꾸 긁어요",
  "petType": "DOG"
}
```

응답:

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

confidence가 Python 1차 기준(`settings.confidence_threshold = 0.45`)보다 낮으면 `UNKNOWN`을 반환한다.

```json
{
  "intentDomain": "UNKNOWN",
  "intent": "UNKNOWN",
  "recommendedCategories": [],
  "confidence": 0.32,
  "keywords": [],
  "intentTags": [],
  "urgency": "NORMAL",
  "message": "입력하신 내용으로 적합한 서비스를 찾기 어렵습니다. 카테고리를 직접 선택해 주세요.",
  "suggestedCategories": ["동물병원", "미용", "반려동물용품"]
}
```

### 5.2 petType

Python schema의 `PetType`은 현재 `DOG`, `CAT`, `OTHER`만 허용한다.

Java `PetIntentClient`는 `DOG`, `CAT` 외 입력을 `OTHER`로 정규화한다. 예를 들어 `BIRD`, `RABBIT`, `HAMSTER`, `ETC`는 모두 Python에는 `OTHER`로 전달된다.

현재 Python `classify(text, pet_type=None)`는 petType을 받지만 분류 로직에는 아직 사용하지 않는다. DOG/CAT별 규칙 분기를 위한 확장 포인트다.

### 5.3 분석 방식

Python 서버는 아래 순서로 분석한다.

1. FastAPI lifespan에서 embedding model과 intent centroid warm-up
2. Kiwi 형태소 분석으로 명사/동사 어간/형용사 어간/XR 추출
3. `_RULES` 기반 규칙 매칭
   - 구문 키워드는 원문 substring
   - 짧은 한글 키워드는 형태소 exact match로 오탐 방지
4. rule miss 시 `intent_examples.yml` centroid와 문장 임베딩 dot product 비교
5. domain별 category 매핑
6. 형태소 기반 intent tag 추출
7. urgency rule 적용

`sentence-transformers`가 설치되어 있으면 `jhgan/ko-sroberta-multitask`를 사용한다. 설치되어 있지 않으면 문자 n-gram hash 기반 fallback embedding을 사용한다.

### 5.4 confidence 해석

| 경로 | 값 의미 | 특징 |
|---|---|---|
| rule hit | 규칙 매칭 강도 휴리스틱 | 0.88~0.92 고정. 코사인 유사도 아님 |
| embedding | 정규화 문장 벡터와 centroid의 dot product | 코사인 유사도 성격. Python 1차 기준 0.45 |

두 값은 직접 비교하면 안 된다. rule이 먼저 적용되고, rule miss일 때만 embedding 경로로 간다.

### 5.5 domain → category

| intentDomain | 추천 카테고리 |
|---|---|
| `MEDICAL` | `동물병원`, `동물약국` |
| `GROOMING` | `미용` |
| `SUPPLIES` | `반려동물용품` |
| `FOOD_SNACK` | `반려동물용품` |
| `WALK_OUTING` | `여행지`, `반려동반여행` |
| `CAFE_DINING` | `카페`, `식당` |
| `LODGING_TRAVEL` | `호텔`, `펜션` |
| `DAYCARE_BOARDING` | `위탁관리` |
| `CULTURE_SPACE` | `반려문화시설`, `미술관`, `박물관`, `문예회관` |

### 5.6 urgency

| 조건 | urgency |
|---|---|
| `SUPPLIES`, `FOOD_SNACK`, `WALK_OUTING`, `CAFE_DINING`, `LODGING_TRAVEL`, `CULTURE_SPACE` | `LOW` |
| MEDICAL 등에서 `응급`, `위급`, `쓰러`, `출혈`, `호흡`, `경련`, `발작`, `못 먹`, `며칠째`, `계속`, `심해` 등 포함 | `HIGH` |
| 그 외 | `NORMAL` |

---

## 6. Signal 저장 정책

### 6.1 테이블

```sql
user_pet_intent_signal
```

| 컬럼 | 설명 |
|---|---|
| `user_idx` | signal 대상 사용자 |
| `source_type` | `COMMUNITY`, `CARE`, `LOCATION_SEARCH` |
| `source_id` | 원천 엔티티 ID. 위치 검색은 null |
| `intent_domain` | NLP domain |
| `intent` | NLP intent |
| `recommended_categories` | 추천 카테고리 JSON |
| `confidence` | 분석 confidence |
| `urgency` | `HIGH`, `NORMAL`, `LOW`, null |
| `intent_tags` | 태그 JSON |
| `expires_at` | signal 만료 시각 |

원문 텍스트는 저장하지 않는다. 저장되는 것은 분석 결과와 source 메타데이터뿐이다.

### 6.2 저장 조건

`UserPetIntentSignalService.saveIfConfident()`는 다음 조건을 만족해야 저장한다.

1. 분석 결과가 null이 아님
2. confidence가 Spring 2차 threshold 이상
3. 같은 사용자에게 같은 `intentDomain`의 유효 signal이 아직 없음
4. JSON 직렬화 성공

Spring 2차 threshold:

| domain | urgency | threshold |
|---|---|---:|
| `MEDICAL` | `HIGH` | 0.55 |
| `MEDICAL` | 그 외 | 0.65 |
| `FOOD_SNACK`, `SUPPLIES`, `WALK_OUTING`, `CAFE_DINING` | any | 0.45 |
| 그 외 | any | 0.60 |

TTL:

| 조건 | TTL |
|---|---:|
| `MEDICAL` + `HIGH` | 1일 |
| `MEDICAL` 그 외 | 3일 |
| `LODGING_TRAVEL` | 14일 |
| 그 외 | 7일 |

### 6.3 signal 조회와 카드 문구

`getActiveSignals()`는 만료되지 않은 signal을 최신순 최대 10건 가져온다.

응답 변환 시:

- `recommended_categories` JSON을 리스트로 변환한다.
- `intent_tags` JSON을 리스트로 변환한다.
- 첫 번째 추천 카테고리를 `targetCategory`로 사용한다.
- `targetTab`은 현재 항상 `location`이다.
- `actionLabel`은 `"근처 {targetCategory} 보기"`다.

대표 카드 메시지:

| domain | message |
|---|---|
| `MEDICAL` + `HIGH` | `위급할 수 있어요. 가까운 동물병원에 바로 문의하세요.` |
| `MEDICAL` | `최근 건강 관련 고민이 있어 보여요.` |
| `GROOMING` | `반려동물 미용이 필요해 보여요.` |
| `SUPPLIES` | `반려동물 용품이 필요해 보여요.` |
| `FOOD_SNACK` | `반려동물 먹거리가 필요해 보여요.` |
| `WALK_OUTING` | `반려동물과 산책하기 좋은 곳을 찾아드릴게요.` |
| `CAFE_DINING` | `반려동물과 나들이 어떠세요?` |
| `LODGING_TRAVEL` | `여행 계획 중이신가요?` |
| `DAYCARE_BOARDING` | `반려동물 돌봄 서비스가 필요해 보여요.` |
| `CULTURE_SPACE` | `반려동물과 함께하는 문화 공간을 찾아보세요.` |

---

## 7. 추천 점수 계산

즉시 추천 API의 시설 점수는 `PetRecommendScoreCalculator`가 계산한다.

| 항목 | 가중치 | 계산 |
|---|---:|---|
| 인기도 | 0.35 | 최근 30일 `PlaceInteractionLog` 건수를 로그 스케일로 0~1 정규화 |
| 태그 일치 | 0.30 | `intentTags` 중 `locationTags`에 포함된 비율 |
| 거리 | 0.20 | `1 - distanceM / radiusM`, 반경 밖은 0 |
| 평점 | 0.10 | `rating / 5.0` |
| 리뷰 수 | 0.05 | `log10(reviewCount + 1) / log10(1001)`, 1.0 상한 |

최종 점수:

```text
finalScore = round(weightedScore * 1000) / 10
```

0.0~100.0 범위의 소수점 한 자리 점수다.

매칭 이유:

- `nearby`: 거리 점수 0.7 이상
- `high_rating`: 평점 점수 0.8 이상
- `many_reviews`: 리뷰 50개 이상
- `popular`: popularity 0.5 이상
- `tag_match:{tag}`: 사용자 의도 태그와 시설 태그 일치
- 아무 조건도 없으면 `in_radius`

---

## 8. 건강 알림 연동

signal 저장에 성공하면 `UserPetIntentSignalService`가 `SignalSavedEvent`를 발행한다.

`PetHealthAlertNotificationHandler`는 커밋 이후 비동기로 이벤트를 받고, `intentDomain=MEDICAL`, `urgency=HIGH`인 경우에만 알림을 생성한다.

알림 내용:

- type: `PET_HEALTH_ALERT`
- title: `반려동물 건강 알림`
- content: `위급할 수 있어요. 가까운 동물병원에 바로 문의하세요.`
- relatedType: `PET_INTENT_SIGNAL`
- relatedId: signal id

알림은 Notification 도메인의 SSE 흐름으로 전달된다. 프론트 `Navigation.js`는 `PET_HEALTH_ALERT` 알림 클릭 시 `navigateToHealthAlert` 이벤트를 발생시키고, `UnifiedPetMapPage`는 주변서비스 탭을 열어 `동물병원` 카테고리로 이동한다.

---

## 9. 장애 처리

- Python 서버 호출 실패, timeout, 4xx/5xx는 `PetIntentClient`에서 `Optional.empty()`로 변환한다.
- signal 수집 흐름에서는 분석 실패가 원 액션에 영향을 주지 않는다.
- 즉시 추천 API는 NLP 실패 시 keyword fallback category를 추정해 Location 검색을 수행한다.
- Location 검색 dedup 중 Redis 장애가 발생하면 Python 호출을 생략한다.
- signal 저장 중 DB 오류나 JSON 직렬화 실패가 발생하면 warn 로그 후 skip한다.
- 프론트 `petRecommendationApi.getSignals()`는 토큰이 없거나 API 실패 시 빈 배열을 반환한다.

---

## 10. 현재 한계와 주의사항

- 프론트 주 경로는 `/signals` 추천 카드다. `/api/pet-recommend` 즉시 추천 API는 백엔드에 있지만 프론트 래퍼가 없다.
- `petType`은 Python까지 전달되지만 현재 분류 로직에는 사용되지 않는다.
- Location 자연어 판단은 `길이 >= 7 && 공백 포함` MVP 휴리스틱이라 `"강아지가귀를긁어요"` 같은 붙여쓰기 자연어는 놓친다.
- `SignalInteractionLog`는 엔티티/테이블/레포만 있고 추천 카드 클릭 저장 로직은 아직 없다.
- NLP confidence는 rule 경로와 embedding 경로의 의미가 다르다.
- 현재 `UNKNOWN` 응답은 Python confidence가 0.45 미만일 때만 나오고 Spring 기본 threshold가 0.60이라 저장되지 않는다. 다만 별도의 `intentDomain=UNKNOWN` 차단 조건은 없다.
- 즉시 추천 API의 `fallbackRecommend()`는 점수 계산 없이 거리순 조회 DTO를 그대로 반환한다.
- Python fallback embedding은 운영 품질용 모델이 아니라 dependency-light 테스트/로컬 fallback이다.
- Simple keyword fallback과 rule 기반 분류는 의료 진단이 아니다. MEDICAL 문구도 수의사 상담을 안내하는 수준이다.

---

## 11. DomainV2 페이지에 넣을 포인트

- 추천 도메인은 장소 추천 자체보다 “사용자 자연어 → Location 카테고리 진입점”을 만드는 구조다.
- Spring은 사용자/DB/장소 검색/저장을 담당하고, Python은 의도 분석만 담당한다.
- 커뮤니티/케어는 `AFTER_COMMIT`, 위치 검색은 자연어 필터+Redis dedup으로 NLP 호출을 제어한다.
- NLP 작업은 `petIntentExecutor` 전용 bounded executor로 격리해 핵심 기능을 보호한다.
- 원문 텍스트를 저장하지 않고 intent, category, confidence, tag, urgency만 저장한다.
- confidence는 Python 1차 0.45, Spring 2차 domain×urgency threshold로 이중 필터링한다.
- MEDICAL+HIGH signal은 `SignalSavedEvent`를 통해 건강 알림과 SSE로 연결된다.
- `SignalInteractionLog`와 action routing은 확장 포인트이며, 현재 카드 클릭 저장은 미구현이다.
