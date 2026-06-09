# petory-nlp-server — NLP 품질 & 계약 이슈 (2026-06-09)

**도메인**: petRecommendation — petory-nlp-server (Python FastAPI)  
**점검 기준**: 코드 정적 분석 + Kiwi 형태소 분석기 동작 검증  
**상태 요약**:

| ID  | 위치                                                 | 문제 상황                                      | 심각도    | 상태         |
| --- | ---------------------------------------------------- | ---------------------------------------------- | --------- | ------------ |
| N1  | `intent_classifier.py:58`, `tag_extractor.py:39,43`  | 부분 문자열 매칭으로 의도·태그 오탐 발생       | 🔴 High   | ✅ 수정 완료 |
| N2  | `UserPetIntentSignalService.java:198`                | 4개 intentDomain 추천 카드 메시지 미구현       | 🟡 Medium | ✅ 수정 완료 |
| N3  | `docs/domains/recommendation.md`                     | 응답 예시/도메인 표가 코드와 불일치            | 🟡 Medium | ✅ 수정 완료 |
| N4  | `intent_classifier.py`, `config.py`                  | rule/embedding confidence 의미 불일치 미문서화 | 🟡 Medium | ✅ 수정 완료 |
| N5  | `pet_intent_router.py:21`, `intent_classifier.py:38` | petType 수신 후 classify()에 미전달            | 🟡 Medium | ✅ 수정 완료 |
| N6  | `main.py:13`                                         | lifespan에서 centroid preload 누락             | 🟢 Low    | ✅ 수정 완료 |

---

## N1 — NLP 부분 문자열 매칭 오탐

### 문제 상황

`extract_tags()` 단위 함수가 잘못된 태그를 반환한다.

```
입력: "귀신이 나타났어요"
기대: intentTags = []
실제: intentTags = ["ear"]  ← 오탐

입력: "강아지 눈사람 만들었어요"
기대: intentTags = []
실제: intentTags = ["eye"]  ← 오탐
```

의료 관련 없는 텍스트에 `ear`, `eye`, `paw` 같은 의료 태그가 붙어, Spring `UserPetIntentSignalService`에 잘못된 `intentTags`가 저장된다.

API 전체 흐름에서는 더 큰 문제가 생긴다. `intent_classifier._classify_by_rule()`도 raw substring 규칙을 쓰므로, tag extractor만 고쳐도 `/api/pet-intent/analyze` 오탐이 완전히 사라지지 않는다.

```
입력: "귀신이 나타났어요"
1. intent_classifier: "귀" in text → MEDICAL_CONCERN, confidence=0.92
2. tag_extractor: domain=MEDICAL인데 태그가 없으면 MEDICAL 기본 태그 ear 보완 가능
3. 결과: 의료 의도가 아닌데 MEDICAL signal 후보가 됨
```

### 원인 분석

**원인 1 — 형태소 키워드 부분 매칭 (line 39)**

```python
# tag_extractor.py:39
if ko in kw or kw in ko:   # ← 부분 문자열 매칭
```

`_KO_TAG_MAP` 키가 1~2음절 단어("귀", "눈", "발")라서, 해당 글자를 포함하는 다른 단어 토큰에 전부 매칭된다.

| 입력                  | Kiwi 토큰 (kw)   | 매칭 키 (ko) | 결과                              |
| --------------------- | ---------------- | ------------ | --------------------------------- |
| "귀신이 나타났어요"   | `"귀신"` (NNG)   | `"귀"`       | `"귀" in "귀신"` = True → ear ✗   |
| "발전소를 방문했어요" | `"발전소"` (NNG) | `"발"`       | `"발" in "발전소"` = True → paw ✗ |
| "눈사람 만들었어요"   | `"눈사람"` (NNG) | `"눈"`       | `"눈" in "눈사람"` = True → eye ✗ |

Kiwi는 복합명사("귀신", "발전소")를 단일 NNG 토큰으로 반환하므로, 부분 포함 검사가 오탐을 만든다.

**원인 2 — 원문 직접 매칭 (line 43)**

```python
# tag_extractor.py:42-44
for ko, en in _KO_TAG_MAP.items():
    if ko in text:            # ← 원문 전체에 부분 문자열 검사
        matched.add(en)
```

형태소 분석 없이 원문 전체에서 키워드를 찾는다. "귀하신 고객님"처럼 전혀 무관한 맥락에서도 `"귀" in text`가 True가 된다. 형태소 분석이 이미 원문을 커버하므로 이 블록은 중복이자 노이즈다.

**원인 3 — \_KO_TAG_MAP 키가 Kiwi lemma와 불일치**

일부 키가 Kiwi 출력 형태(lemma)가 아닌 활용형으로 저장돼 있다.

| 현재 키  | 입력 예시  | Kiwi 실제 출력           | 불일치            |
| -------- | ---------- | ------------------------ | ----------------- |
| `"가려"` | "가려워요" | `"가렵"` (VA, ㅂ 불규칙) | `"가려" ≠ "가렵"` |
| `"부어"` | "부었어요" | `"붓"` (VV, ㅅ 불규칙)   | `"부어" ≠ "붓"`   |
| `"맡길"` | "맡길 곳"  | `"맡기"` (VV 어간)       | `"맡길" ≠ "맡기"` |

exact match로 바꿔도 이 세 키는 여전히 매칭되지 않는다.

`"절뚝"`은 tokenizer가 XR(어근)을 포함하므로 Kiwi가 `"절뚝"` (XR)으로 반환할 가능성이 높으나, 테스트로 확인 필요.

**원인 4 — intent_classifier 규칙도 raw substring 매칭**

```python
# intent_classifier.py:58
if any(keyword in normalized for keyword in keywords):
    return intent, domain, confidence
```

`_RULES`에도 `"귀"`, `"털"`, `"옷"` 같은 짧은 키워드와 `"밥을 안"`, `"안 먹"`, `"여행 숙소"` 같은 구문 키워드가 섞여 있다. 모든 키워드를 raw substring으로 검사하면 `"귀신"`이 `MEDICAL_CONCERN`으로 분류된다.

이 오탐은 `tag_extractor` 수정만으로 막을 수 없다. router는 `classify()` 결과 domain을 그대로 `extract_tags(req.text, domain)`에 넘기고, `extract_tags()`는 태그가 없으면 domain의 첫 번째 기본 태그를 보완하기 때문이다.

### 해결 방법

**1. 형태소 exact match로 교체**

```python
# Before
for ko, en in _KO_TAG_MAP.items():
    if ko in kw or kw in ko:
        matched.add(en)

# After
for ko, en in _KO_TAG_MAP.items():
    if ko == kw:
        matched.add(en)
```

**2. 원문 직접 매칭 블록 제거**

```python
# 제거 대상 (line 42-44)
for ko, en in _KO_TAG_MAP.items():
    if ko in text:
        matched.add(en)
```

형태소 분석 결과만으로 충분하며, 원문 매칭은 오탐 경로다.

**3. \_KO_TAG_MAP 키를 Kiwi lemma로 수정**

```python
# Before → After
"가려": "itching"  →  "가렵": "itching"
"부어": "swelling" →  "붓":   "swelling"
"맡길": "boarding" →  "맡기": "boarding"
```

**4. 회귀 테스트 추가 (test_tag_extractor.py 신규 생성)**

```python
# 오탐 방지 테스트
def test_no_ear_tag_for_ghost():
    tags = extract_tags("귀신이 나타났어요", "UNKNOWN")
    assert "ear" not in tags

def test_no_eye_tag_for_snowman():
    tags = extract_tags("눈사람 만들었어요", "UNKNOWN")
    assert "eye" not in tags

def test_no_paw_tag_for_development():
    tags = extract_tags("강아지 발전이 빨라요", "UNKNOWN")
    assert "paw" not in tags

# 정상 매칭 테스트
def test_ear_tag_for_ear_scratch():
    tags = extract_tags("강아지가 귀를 자꾸 긁어요", "MEDICAL")
    assert "ear" in tags

def test_itching_tag_for_itch():
    tags = extract_tags("강아지가 가려워해요", "MEDICAL")
    assert "itching" in tags  # "가렵" lemma 매칭 확인

def test_swelling_tag():
    tags = extract_tags("발이 부었어요", "MEDICAL")
    assert "swelling" in tags  # "붓" lemma 매칭 확인

def test_절뚝_tag():
    tags = extract_tags("강아지가 절뚝거려요", "MEDICAL")
    assert "limp" in tags     # XR 포함 여부 확인
```

**5. intent_classifier rule 매칭도 분리**

짧은 단어 키워드는 tokenizer exact match로 검사하고, 구문 키워드는 raw substring 검사를 유지한다.

```python
# 방향성 예시
PHRASE_KEYWORDS = {"밥을 안", "안 먹", "여행 숙소", "1박"}

def _has_keyword(text: str, keywords: list[str]) -> bool:
    keyword_tokens = set(extract_keywords(text))
    for keyword in keywords:
        if keyword in PHRASE_KEYWORDS:
            if keyword in text:
                return True
        elif keyword in keyword_tokens:
            return True
    return False
```

**6. 통합 회귀 테스트 추가**

`extract_tags()` 단위 테스트와 별도로 rule matcher 기준 테스트를 추가한다. `classify()`는 rule miss 후 embedding best intent를 반환하므로, rule 오탐 여부는 `_classify_by_rule()`을 직접 검증하는 편이 덜 흔들린다.

```python
from app.nlp.intent_classifier import _classify_by_rule

def test_ghost_is_not_medical_rule_hit():
    assert _classify_by_rule("귀신이 나타났어요") is None

def test_cute_is_not_medical_rule_hit():
    assert _classify_by_rule("강아지가 귀여워요") is None

def test_real_ear_symptom_is_medical_rule_hit():
    intent, domain, confidence = _classify_by_rule("강아지가 귀를 자꾸 긁어요")
    assert domain == "MEDICAL"
```

---

## N2 — buildCardMessage() 4개 도메인 메시지 미구현

### 문제 상황

추천 카드에서 FOOD_SNACK, WALK_OUTING, DAYCARE_BOARDING, CULTURE_SPACE 도메인의 signal이 저장돼도 카드 메시지가 "최근 입력을 바탕으로 추천합니다."로만 표시된다.

```
사용자 입력: "강아지 유치원 맡길 곳 찾아요"
intentDomain: DAYCARE_BOARDING
카드 메시지: "최근 입력을 바탕으로 추천합니다."  ← 의도와 무관한 기본 메시지
```

### 원인 분석

`UserPetIntentSignalService.buildCardMessage()` switch 문에 9개 도메인 중 5개만 케이스가 정의돼 있다.

```java
// UserPetIntentSignalService.java:198
return switch (domain != null ? domain : "") {
    case "MEDICAL"        -> "최근 건강 관련 고민이 있어 보여요.";
    case "GROOMING"       -> "반려동물 미용이 필요해 보여요.";
    case "CAFE_DINING"    -> "반려동물과 나들이 어떠세요?";
    case "LODGING_TRAVEL" -> "여행 계획 중이신가요?";
    case "SUPPLIES"       -> "반려동물 용품이 필요해 보여요.";
    default               -> "최근 입력을 바탕으로 추천합니다.";  // ← 4개 도메인이 여기로
};
```

누락된 도메인: `FOOD_SNACK`, `WALK_OUTING`, `DAYCARE_BOARDING`, `CULTURE_SPACE`

### 해결 방법

```java
return switch (domain != null ? domain : "") {
    case "MEDICAL"          -> "최근 건강 관련 고민이 있어 보여요.";
    case "GROOMING"         -> "반려동물 미용이 필요해 보여요.";
    case "CAFE_DINING"      -> "반려동물과 나들이 어떠세요?";
    case "LODGING_TRAVEL"   -> "여행 계획 중이신가요?";
    case "SUPPLIES"         -> "반려동물 용품이 필요해 보여요.";
    case "FOOD_SNACK"       -> "반려동물 먹거리가 필요해 보여요.";
    case "WALK_OUTING"      -> "반려동물과 산책하기 좋은 곳을 찾아드릴게요.";
    case "DAYCARE_BOARDING" -> "반려동물 돌봄 서비스가 필요해 보여요.";
    case "CULTURE_SPACE"    -> "반려동물과 함께하는 문화 공간을 찾아보세요.";
    default                 -> "최근 입력을 바탕으로 추천합니다.";
};
```

---

## N3 — recommendation.md 응답 예시 불일치

### 문제 상황

`docs/domains/recommendation.md`의 응답 예시가 실제 코드와 다르다. 잘못된 예시를 참고하면 연동 오류나 혼란이 생긴다.

### 원인 분석

문서가 초기 설계 단계에서 작성된 후 코드가 변경됐을 때 동기화되지 않았다.

**불일치 목록**

| 항목                            | 문서 (line)                                | 실제 코드             | 근거                                           |
| ------------------------------- | ------------------------------------------ | --------------------- | ---------------------------------------------- |
| `intent` 값                     | `"HEALTH_SYMPTOM"` (line 137)              | `"MEDICAL_CONCERN"`   | `intent_examples.yml:1`, `_RULES line 14`      |
| `intentTags`에 `"medical"` 포함 | `["medical", "ear", "itching"]` (line 141) | `"medical"` 태그 없음 | `_KO_TAG_MAP`, `intent_tags.yml` 어디에도 없음 |
| domain 명칭                     | `CARE_SERVICE` (line 183 표)               | `DAYCARE_BOARDING`    | `category_rules.py:11`, `_RULES line 18`       |
| 응답 필드 누락                  | `keywords`, `message` 필드 없음            | 실제 응답에 존재      | `response.py`, `pet_intent_router.py:49-57`    |

### 해결 방법

`recommendation.md` 4.3절 응답 예시를 실제 코드 기준으로 교체:

```json
{
  "intentDomain": "MEDICAL",
  "intent": "MEDICAL_CONCERN",
  "recommendedCategories": ["동물병원", "동물약국"],
  "confidence": 0.92,
  "keywords": ["강아지", "귀", "긁"],
  "intentTags": ["ear", "itching"],
  "urgency": "NORMAL",
  "message": "ear, scratch 불편 표현이 감지되었습니다. 정확한 진단은 수의사 상담이 필요합니다. 증상이 심하거나 지속된다면 가까운 동물병원에 문의하세요."
}
```

4.5절 도메인 표에서 `CARE_SERVICE` → `DAYCARE_BOARDING` 수정.

---

## N4 — rule/embedding confidence 의미 불일치 미문서화

### 문제 상황

`confidence` 값이 두 가지 다른 방식으로 계산되지만, 코드와 문서 어디에도 이 차이가 명시돼 있지 않다. API 소비자(Spring, 문서 독자)가 confidence 값을 단일 척도로 오해할 수 있다.

```
입력: "강아지 귀를 긁어요"    → rule hit  → confidence = 0.92  (하드코딩)
입력: "반려동물 케어 서비스"  → embedding → confidence = 0.61  (cosine similarity)
```

두 값의 의미는 다르지만, API 응답 형식은 동일하다.

### 원인 분석

**rule confidence (intent_classifier.py:13-23)**

```python
_RULES = [
    ("MEDICAL_CONCERN", "MEDICAL", 0.92, ["병원", ...]),
    ...
]
```

0.88~0.92는 "이 규칙이 얼마나 강한 신호인지"에 대한 설계자의 주관적 판단값이다. 코사인 유사도와 수치 범위가 비슷해 보이지만 다른 의미다.

**embedding confidence (intent_classifier.py:48-52)**

```python
query_vec = encode([text])[0]          # L2 정규화됨
scores = {
    intent: float(np.dot(query_vec, centroid))   # = 코사인 유사도
    ...
}
```

`embedding_model.py:25`에서 `normalize_embeddings=True`로 인코딩하므로 dot product = 코사인 유사도 ∈ [-1, 1]. 동일 도메인 유사 문장은 실제로 약 0.4~0.75 범위.

**Spring 2차 필터와의 관계**

rule path는 항상 0.88+ → Spring `CONFIDENCE_THRESHOLD = 0.60` 무조건 통과.  
embedding path는 0.45~1.0 → Spring 2차 필터가 실질적으로 작동하는 경로.

Python `config.py`에는 `# R9` 주석만 있고, Spring과의 2단계 필터 관계가 설명되지 않는다.

### 해결 방법

**1. intent_classifier.py 주석 추가**

```python
_RULES = [
    # (intent, domain, rule_confidence, keywords)
    # rule_confidence: 규칙 매칭 강도를 나타내는 휴리스틱 값 (0.88~0.92).
    # embedding path의 코사인 유사도([-1,1])와 수치 범위는 비슷하지만 의미가 다르다.
    # rule hit 시 embedding과 비교 없이 즉시 반환되므로 두 값이 직접 경쟁하지는 않는다.
    ("MEDICAL_CONCERN", "MEDICAL", 0.92, [...]),
    ...
]
```

**2. config.py 주석 보강**

```python
# R9: Python 1차 필터 (0.45 미만 → UNKNOWN 반환)
# - rule path: confidence 항상 0.88~0.92 → 이 필터 통과 보장
# - embedding path: 코사인 유사도 기반, 실제 범위 약 0.4~0.75
# Spring UserPetIntentSignalService의 2차 필터(0.60 미만 → signal 저장 거부)와 조합.
# embedding path에서만 두 필터가 모두 실질적으로 작동한다.
confidence_threshold: float = 0.45
```

**3. recommendation.md 4.4절 문서 추가**

```
#### confidence 값 해석

confidence 값은 분류 경로에 따라 의미가 다르다.

| 분류 경로 | confidence 계산 방식 | 범위 |
|----------|---------------------|------|
| rule 매칭 | 규칙 설계자 지정 휴리스틱 | 0.88~0.92 고정 |
| embedding 분류 | 쿼리 벡터와 centroid 간 코사인 유사도 | 약 0.4~0.75 |

두 경로는 직접 경쟁하지 않는다. rule이 먼저 적용되고, miss일 때만 embedding이 실행된다.

**2단계 필터 구조**:
- Python 1차: confidence < 0.45 → UNKNOWN 반환 (embedding path에서 주로 작동)
- Spring 2차: confidence < 0.60 → signal 저장 거부 (embedding path에서 주로 작동)
```

---

## N5 — classify() petType 계약 미완성

### 문제 상황

Spring이 `petType`을 전달하고 Python 스키마도 수신하지만, 실제 분류 로직에 전달되지 않는다.

```java
// PetIntentClient.java:101
public Optional<PetIntentAnalyzeResponse> analyze(String text, String petType)
// petType을 request body에 포함해서 전송
```

```python
# pet_intent_router.py:21
intent, domain, confidence = classify(req.text)  # ← req.petType 누락
```

DOG/CAT 특이 증상("고양이 털뭉치 토함", "강아지 귀진드기")이 있어도 petType이 분류에 반영되지 않는다.

### 원인 분석

`intent_classifier.py:38-45` 주석:

```python
def classify(text: str) -> Tuple[str, str, float]:
    """
    petType은 MVP에서 사용하지 않는다.
    이후 petType별 규칙/모델 분기 시 파라미터 추가 예정.
    """
```

의도적으로 남겨둔 MVP 미완성 사항이다. Spring ↔ Python 계약은 열려 있지만, classify() 시그니처가 닫혀 있어 확장이 막혀 있다.

### 해결 방법

**1단계: 시그니처 계약 열기 (이번 작업 범위)**

```python
# intent_classifier.py
from typing import Optional

def classify(text: str, pet_type: Optional[str] = None) -> Tuple[str, str, float]:
    """
    pet_type: "DOG" | "CAT" | "OTHER" | None
    현재는 분류 로직에 미사용. 시그니처만 열어두어 이후 DOG/CAT 규칙 추가를 준비한다.
    """
    rule_result = _classify_by_rule(text)
    ...
```

```python
# pet_intent_router.py
intent, domain, confidence = classify(req.text, pet_type=req.petType)
```

**2단계: DOG/CAT 소량 규칙 추가 (이후 작업)**

```python
# 예시 — 추후 추가 방향
_DOG_RULES = [
    ("MEDICAL_CONCERN", "MEDICAL", 0.93, ["귀진드기", "슬개골", "디스크"]),
]
_CAT_RULES = [
    ("MEDICAL_CONCERN", "MEDICAL", 0.93, ["털뭉치", "고양이 구내염", "신부전"]),
]
```

---

## N6 — lifespan centroid preload 누락

### 문제 상황

서버 시작 후 **첫 번째 embedding path 요청**이 후속 요청보다 느리다.

```
# 서버 시작 후 첫 요청 처리 시간 (예상)
1번째 요청: ~400ms  (모델 로드 완료 + centroid 계산 포함)
2번째 요청: ~150ms  (centroid 캐시됨)
```

### 원인 분석

`main.py:13`의 lifespan이 embedding 모델만 preload한다.

```python
# main.py:8-14
async def lifespan(app: FastAPI):
    await loop.run_in_executor(None, get_model)   # 모델 로드 ✓
    # _load() 미호출 → centroid 계산은 첫 classify() 호출 시 lazy 실행
    yield
```

`intent_classifier._load()`는 `intent_examples.yml` 파싱 + 모든 예문 임베딩 계산 + centroid 산출을 수행한다. rule hit 시에는 `_load()`가 호출되지 않으므로, rule miss인 첫 요청에서만 발생하는 지연이다.

### 해결 방법

`intent_classifier.py`에 public `warm_up()` 래퍼 추가 후 lifespan에서 호출:

```python
# intent_classifier.py
def warm_up() -> None:
    """서버 시작 시 centroid를 미리 계산해 첫 요청 지연을 방지한다."""
    _load()
```

```python
# main.py
from app.nlp.intent_classifier import warm_up

async def lifespan(app: FastAPI):
    loop = asyncio.get_event_loop()
    await loop.run_in_executor(None, get_model)   # 1. 모델 로드
    await loop.run_in_executor(None, warm_up)      # 2. centroid 계산 (모델 로드 후 실행)
    yield
```

순서가 중요하다. `warm_up()`은 내부적으로 `encode()`를 호출하므로 반드시 `get_model()` 완료 후 실행해야 한다.

---

_작성 기준: `petory-nlp-server/app/**`, `domain/petRecommendation/**` 코드 정적 분석 2026-06-09_
