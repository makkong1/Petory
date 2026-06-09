# PetRecommendation 의도 기반 액션 라우팅 확장안 (2026-06-09)

## 1. 배경

현재 Recommendation 도메인은 사용자 텍스트를 Python NLP 서버로 분석한 뒤, `intentDomain`을 Location 카테고리로 바꿔 주변서비스 추천 카드에 연결한다.

```text
사용자 텍스트
→ Python NLP: intentDomain / recommendedCategories / intentTags
→ Spring: user_pet_intent_signal 저장
→ Frontend: 주변서비스 탭에서 targetCategory 검색
```

이 구조는 "근처에 어디를 보여줄지"에는 적합하지만, 사용자의 실제 의도가 항상 장소 검색으로 끝나지는 않는다.

예를 들어 `말티즈인데 닭고기 알레르기가 있어서 간식 추천해줘`는 현재 `FOOD_SNACK → 반려동물용품`으로 처리된다. 이는 "근처 용품점" 추천에는 쓸 수 있지만, 알레르기 조건을 고려한 간식 자체 추천이나 성분 주의 안내까지는 처리하지 못한다.

## 2. 현재 한계

### 2.1 추천 대상이 Location에 고정됨

현재 `UserPetIntentSignalResponse`에는 이미 `targetTab`, `targetCategory`, `actionLabel` 필드가 있지만, 백엔드는 `targetTab = "location"`만 내려준다.

즉, 모든 signal이 주변서비스 탭의 카테고리 검색으로 귀결된다.

### 2.2 NLP 응답이 조건을 구조화하지 않음

현재 Python 응답은 다음 수준이다.

```json
{
  "intentDomain": "FOOD_SNACK",
  "intent": "FOOD_SNACK_NEED",
  "recommendedCategories": ["반려동물용품"],
  "intentTags": ["snack"],
  "urgency": "LOW"
}
```

품종, 알레르기, 나이, 질환, 시간 조건 같은 제약은 별도 필드로 분리되지 않는다.

### 2.3 제품 추천 데이터가 없음

알레르기 없는 간식 자체를 추천하려면 제품 데이터가 필요하다.

```text
pet_snack_product
- name
- targetPetType
- ingredients
- allergenTags
- proteinSource
- ageRange
- breedSize
- purchaseUrl
```

현재 LocationService DB는 장소 중심 데이터라 "이 매장이 닭고기 없는 간식을 판다" 같은 재고·성분 필터를 보장하지 못한다.

### 2.4 이벤트 기반 signal 경로는 petType을 잃는다

직접 추천 API(`GET /api/pet-recommend`)는 `petType`을 Python 요청까지 전달한다. 반면 커뮤니티/케어/위치검색 이벤트 기반 signal 경로는 `PetIntentSignalEventListener.analyze()`에서 항상 `petIntentClient.analyze(text, null)`을 호출한다.

```java
Optional<PetIntentAnalyzeResponse> result = petIntentClient.analyze(text, null);
```

따라서 Python `classify(text, pet_type)` 계약을 열어도 비동기 signal에는 `petType`이 전달되지 않는다. 단, 사용자가 여러 반려동물을 키울 수 있으므로 모든 이벤트에 무조건 petType을 넣는 것은 정책 결정이 필요하다.

권장:

- 케어 요청: 요청 DTO/연결된 pet 정보가 있으면 우선 전달
- 커뮤니티 게시글: 특정 pet 선택 기능이 없으면 null 유지
- 위치 검색: 검색어가 특정 pet과 연결되지 않으므로 null 유지 또는 대표 pet 정책 별도 검토

### 2.5 urgency가 signal 카드에 반영되지 않음

Python 응답에는 `urgency`가 있지만 `UserPetIntentSignal` 엔티티와 `UserPetIntentSignalResponse`에는 urgency 필드가 없다. 따라서 `MEDICAL + HIGH` 상황에서도 카드 메시지가 일반 건강 고민 문구로 표시된다.

### 2.6 signal 클릭 로그는 장소 상호작용 로그와 다르다

현재 `PlaceInteractionLog`는 `location_idx`가 필수다. signal 카드는 특정 장소가 아니라 카테고리나 다음 행동을 클릭하는 구조이므로, 이를 그대로 재사용하기 어렵다.

signal 클릭률을 수집하려면 `signal_interaction_log` 같은 별도 로그 테이블이 더 적합하다.

### 2.7 WALK_OUTING 카테고리 매핑이 산책 의도와 어긋남

현재 `category_rules.py`는 `WALK_OUTING`을 `["여행지", "반려동반여행"]`으로 매핑한다. `강아지 산책 공원 찾아요` 같은 입력도 `여행지` 카테고리 카드로 연결될 수 있다.

다만 현재 Location 카테고리 트리에는 `공원`, `산책로`, `반려동물공원` 계열이 없다. 프론트 카테고리 트리와 DB 카테고리 기준으로는 `반려동반여행`, `여행지`가 임시 fallback 역할을 한다.

권장:

- Location 데이터에 `공원`/`산책로`/`반려동물공원` 계열 카테고리 추가 전까지는 이 매핑을 임시 fallback으로 문서화한다.
- 산책 의도는 Location 카테고리 검색보다 `meetup` 라우팅 또는 `keyword="공원"` 보조 검색을 함께 검토한다.
- 카테고리 데이터가 확장되면 `WALK_OUTING → ["공원", "산책로", "반려동물공원"]`으로 교체한다.

## 3. 확장 방향

### 3.1 targetAction 도입

`intentDomain`을 단순 Location 카테고리 변환용으로만 쓰지 않고, 사용자의 다음 행동을 추천하는 개념을 추가한다.

```json
{
  "targetTab": "care",
  "targetAction": "CREATE_CARE_REQUEST",
  "actionLabel": "돌봄 요청 작성하기"
}
```

가능한 target 예시:

| 입력 예시 | 현재 처리 | 확장 후 target |
| --- | --- | --- |
| `출장 가는데 강아지 맡길 곳 필요해요` | `위탁관리` 장소 추천 | `care` / 돌봄 요청 작성 |
| `강아지 산책 친구 구해요` | `여행지` 장소 추천 | `meetup` / 산책 모임 찾기 |
| `강아지가 계속 토하고 숨을 이상하게 쉬어요` | `동물병원` 장소 추천 | `location` / 긴급 병원 안내 |
| `강아지를 잃어버렸어요` | 미지원 | `missingPet` / 실종 제보 작성 |
| `말티즈 닭고기 알레르기 간식 추천` | `반려동물용품` 장소 추천 | `location` + 조건 안내, 추후 `product` |

### 3.1.1 DAYCARE_BOARDING 라우팅 기준

`DAYCARE_BOARDING`은 Location 시설 추천과 Care 요청 작성 양쪽으로 갈 수 있다. 분기 조건을 명시하지 않으면 구현이 `if/else`가 아닌 감으로 흘러갈 수 있다.

권장 분기:

| 라우팅 | 키워드/상황 | 예시 |
| --- | --- | --- |
| `care` | `출장`, `여행 중`, `며칠`, `하루 종일`, `장시간`, `집 비움`, `돌봐줄`, `맡아줄`, `방문 돌봄` | `출장 가는데 강아지 맡아줄 사람 필요해요` |
| `location` | `유치원`, `데이케어`, `호텔`, `위탁관리`, `근처`, `가까운 곳`, `시설`, `센터` | `집 근처 강아지 유치원 찾아요` |

애매하면 `location` fallback을 우선한다. 케어 요청 작성은 사용자 부담이 더 큰 행동이므로, 확실한 돌봄 요청 의도가 있을 때만 `care`로 보낸다.

### 3.2 constraints 필드 추가

NLP가 사용자의 조건을 구조화해서 반환한다.

```json
{
  "intentDomain": "FOOD_SNACK",
  "recommendedCategories": ["반려동물용품"],
  "constraints": {
    "petType": "DOG",
    "breed": "말티즈",
    "avoidIngredients": ["닭고기", "밀"],
    "hasAllergyConcern": true,
    "needProductAdvice": true
  }
}
```

초기에는 추천 결과 필터링보다 메시지 품질 개선에 사용한다.

```text
알레르기 정보가 있어 성분 확인이 필요해 보여요.
근처 반려동물용품점을 추천하지만, 구매 전 닭고기/밀 포함 여부를 확인하세요.
```

제품 DB가 생기면 같은 `constraints`를 product filtering에 재사용한다.

추출 위치:

```text
petory-nlp-server/app/nlp/constraint_extractor.py
```

`classify()`는 의도 분류만 담당하고, 품종/알레르기/나이/시간 조건은 별도 `extract_constraints(text, pet_type=None)` 함수로 분리한다.

```python
intent, domain, confidence = classify(req.text, pet_type=req.petType)
constraints = extract_constraints(req.text, pet_type=req.petType)
intent_tags = extract_tags(req.text, domain)
urgency = judge_urgency(req.text, domain)
```

이렇게 분리하면 알레르기/품종 추출을 독립 테스트할 수 있고, 이후 제품 추천 도메인으로 재사용하기 쉽다.

### 3.3 고위험 intent는 threshold 분리

의료, 실종, 응급성 signal은 오탐 비용이 높다. 전역 confidence 기준만 쓰지 말고 intentDomain별 하한을 둔다.

| domain | 권장 threshold | 이유 |
| --- | --- | --- |
| `MEDICAL` | 0.65 (NORMAL) / 0.55 (HIGH) | Phase 0.3에서 urgency 조합으로 세분화 |
| `MISSING_PET` | 0.70 이상 | 실종 제보 라우팅 오탐 방지 |
| `SUPPLIES` / `FOOD_SNACK` | 0.45 | Python 1차 필터(0.45)와 동일 — 오탐 비용 낮음 |
| `WALK_OUTING` / `CAFE_DINING` | 0.45 | 추천 카드 수준이면 허용 가능 |

### 3.4 MISSING_PET 우선순위

`MISSING_PET` domain을 추가할 경우 `_RULES` 배열 순서가 실제 우선순위가 된다. 실종 의도는 `MEDICAL`보다 먼저 매칭해야 한다.

권장:

- `MISSING_PET` rule은 `_RULES` 최상단에 둔다.
- `MISSING_PET` 감지 시 `urgency=HIGH`, `targetTab=missingPet`을 기본값으로 둔다.
- 의료 표현이 동시에 감지되면 병원 안내를 primary action으로 빼앗지 말고 secondary action으로 제공한다.

예:

```json
{
  "intentDomain": "MISSING_PET",
  "targetTab": "missingPet",
  "urgency": "HIGH",
  "secondaryActions": [
    {
      "targetTab": "location",
      "targetCategory": "동물병원",
      "reason": "다친 상태일 수 있음"
    }
  ]
}
```

## 4. 단계별 구현안

### Phase 0 — 현재 계약/저장 구조 정리

본 확장 전에 먼저 현재 코드의 계약과 저장 정책을 정리한다.

#### 0.1 petType 전달 정책

- 직접 추천 API는 이미 `petType`을 Python까지 전달한다.
- 이벤트 기반 signal은 현재 `petType=null`로 고정된다.
- 우선 케어 요청 경로부터 petType 전달을 검토한다.
- 커뮤니티/위치검색은 특정 pet 선택 UX가 없으면 null 유지한다.

검증 기준:

- 기존 이벤트 생성 코드가 깨지지 않음
- 케어 요청에서 petType을 확인할 수 있는 경우 Python request body에 포함
- Python은 petType이 null이어도 기존처럼 동작

#### 0.2 urgency 저장 및 카드 반영

- `user_pet_intent_signal`에 `urgency` 컬럼 추가
- `UserPetIntentSignalResponse`에 `urgency` 추가
- `buildCardMessage(domain, urgency, categories)`로 확장
- 주의: Phase 1에서 constraints 반영 시 `buildCardMessage(domain, urgency, constraints, categories)`로 최종 확장 예정. Phase 0.2에서는 urgency까지만 추가한다.

예:

```text
MEDICAL + HIGH
→ "위급할 수 있어요. 가까운 동물병원에 바로 문의하세요."
```

#### 0.3 domain + urgency 기반 confidence threshold

현재 Spring signal 저장 기준은 단일 `CONFIDENCE_THRESHOLD = 0.6`이다. domain별 오탐 비용이 다르므로 Map으로 분리한다.

```java
private static final Map<String, Double> DOMAIN_THRESHOLDS = Map.of(
    "MEDICAL",          0.65,  // urgency=HIGH 시 thresholdFor()에서 0.55로 완화
    "FOOD_SNACK",       0.45,  // Python 1차 필터와 동일 — 오탐 비용 낮음
    "SUPPLIES",         0.45,
    "WALK_OUTING",      0.45,
    "CAFE_DINING",      0.45
);
private static final double DEFAULT_THRESHOLD = 0.60;
```

`MISSING_PET`은 domain 추가 이후 0.70 이상으로 별도 설정한다.

urgency 저장 이후에는 `thresholdFor(domain, urgency)` 형태로 확장한다.

urgency × threshold 방향:

| 조건 | threshold | 근거 |
| --- | --- | --- |
| `MEDICAL + HIGH` | 0.55 (완화) | 위급 signal 누락이 오탐보다 비용 큼 — 낮춰서 더 잘 저장 |
| `MEDICAL + NORMAL` | 0.65 (강화) | 일반 건강 고민 오탐 방지 |
| `FOOD_SNACK` / `WALK_OUTING` / `CAFE_DINING` | 0.45 (urgency 무관) | Python 1차 필터와 동일 — urgency=HIGH 발생 안 함 |

`thresholdFor(domain, urgency)` 구현: MEDICAL+HIGH 예외만 적용하고 나머지는 domain Map 기본값 반환.

#### 0.4 domain + urgency 기반 TTL

현재 signal TTL은 7일 고정이다.

권장:

| 조건 | TTL | 이유 |
| --- | --- | --- |
| `MEDICAL + HIGH` | 1일 | 위급 signal stale 방지 |
| `MEDICAL + NORMAL` | 3일 | 증상 signal stale 방지 |
| `FOOD_SNACK` / `SUPPLIES` | 7일 | 구매 니즈 유지 가능 |
| `LODGING_TRAVEL` | 14일 | 여행 계획은 비교적 장기 |
| `CAFE_DINING` / `WALK_OUTING` | 7일 | 가벼운 추천 카드 |

구현은 `ttlDaysFor(domain, urgency)` 함수로 분리한다.

#### 0.5 signal 클릭 로그는 별도 테이블로 설계

`PlaceInteractionLog`는 `location_idx`가 필수라 signal 카드 클릭에 맞지 않는다.

후보:

```text
signal_interaction_log
- id
- user_idx
- signal_id
- intent_domain
- target_tab
- target_category
- interaction_type
- created_at
```

초기 interaction type:

- `CLICKED`: 사용자가 추천 카드를 눌렀다.
- `DISMISSED`: 사용자가 추천 카드를 닫거나 숨겼다.

추후 작성/검색 완료까지 추적하려면 `CONVERTED`를 추가한다.

이 로그는 threshold 튜닝과 카드 문구 개선의 근거로 사용한다.

### Phase 1 — 현재 구조 유지 + constraints 메시지 개선

- Python 응답에 `constraints` 추가
- Spring DTO에 `constraints` 필드 추가
- Python이 `constraints`를 추가해도 Spring `PetIntentAnalyzeResponse`는 unknown field를 무시하므로 하위 호환은 유지된다
- Spring에서 실제로 사용하려면 Java DTO에 `constraints` 필드를 추가해야 한다
- 초기에는 DB 저장 없이 카드 메시지 생성에만 사용하고, 장기 개인화가 필요해지면 JSON 컬럼 저장을 검토한다
- 알레르기/품종/시간 조건은 추천 필터가 아니라 안내 문구에 반영

검증 기준:

- 기존 `/api/pet-intent/analyze` 응답 하위 호환 유지
- 기존 `/api/pet-recommend/signals` 카드 표시 깨지지 않음
- 알레르기 입력에서 `hasAllergyConcern=true`, `avoidIngredients` 추출

### Phase 2 — targetTab 실제 활용

- `targetTab`을 `location` 외 `care`, `meetup`, `missingPet`까지 확장
- 프론트 `LocationControls`는 현재 `targetCategory`만 처리하므로, signal 클릭 handler를 tab/action 기반으로 확장
- `DAYCARE_BOARDING`은 3.1.1의 키워드 기준에 따라 `location` 또는 `care`로 분기

검증 기준:

- 주변서비스 추천 카드는 기존처럼 동작
- care/meetup/missingPet target signal은 해당 탭 또는 작성 플로우로 이동
- target 미지원 클라이언트는 기존 `targetCategory` fallback 사용

### Phase 3 — 신규 intentDomain 추가

> **DTO 설계 예약**: `secondaryActions` 필드는 Phase 3 진입 시 별도 DTO로 설계한다.
> Spring 측 예상 타입: `List<SecondaryAction>` (record 또는 inner class).
> `@JsonIgnoreProperties(ignoreUnknown = true)`가 이미 적용돼 있으므로 Python 응답에 필드 추가는 Spring 하위 호환 유지됨. Java DTO에 별도 추가 필요.

후보:

- `MISSING_PET`
- `PET_PRODUCT_ADVICE`
- `CARE_REQUEST_INTENT`
- `MEETUP_INTENT`

주의:

- 신규 domain은 Python `IntentDomain` enum, `category_rules.py`, Spring DTO/문서, signal card message를 같이 수정해야 한다.
- `MISSING_PET`은 오탐 비용이 크므로 rule/embedding 예시와 threshold를 보수적으로 잡는다.
- `MISSING_PET`이 감지되면 `urgency=HIGH`, `targetTab=missingPet`을 기본값으로 둔다.
- `MEDICAL`과 `MISSING_PET`이 동시에 감지되는 문장은 3.4 기준대로 `MISSING_PET`을 primary action으로 두고, 병원 안내는 secondary action으로 제공한다.

### Phase 4 — 제품 추천 도메인 분리

간식/사료 자체 추천은 LocationService가 아니라 별도 product catalog가 필요하다.

이 단계 전까지는 "제품 추천"이라는 표현을 피하고, "성분 확인이 필요한 상황을 인식해 근처 용품점과 주의 문구를 제공"하는 수준으로 제한한다.

## 5. 추천 우선순위

1. 이벤트 기반 signal의 petType 전달 정책 정리
2. urgency 저장 및 카드 메시지 반영
3. domain + urgency 기반 confidence threshold 적용
4. `constraints` 스키마 초안 작성
5. 알레르기/품종/나이/시간 조건 추출 규칙 추가
6. signal card message에 constraints 반영
7. domain + urgency 기반 TTL 적용
8. WALK_OUTING 카테고리 한계와 대안 확정
9. DAYCARE_BOARDING `care`/`location` 분기 기준 적용
10. `targetTab`을 실제 라우팅에 사용
11. DOG/CAT 조건부 규칙 소량 추가
12. signal 클릭 로그 별도 테이블 설계
13. 신규 domain 및 product catalog는 별도 단계로 분리

현재 코드 구조상 바로 손대기 좋은 것은 Phase 0의 petType 정책, urgency 저장, threshold/TTL 정리다. `constraints`와 `targetTab` 확장은 그 다음 단계로 진행하는 편이 안전하다. 제품 추천은 데이터 모델이 생긴 뒤 진행한다.
