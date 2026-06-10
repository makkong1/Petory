# PetRecommendation 사용자 활동 의도 분석 AI 전환 메모 (2026-06-10)

## 1. 배경

현재 Petory는 사용자의 활동 텍스트를 기반으로 반려생활 의도 signal을 만든다.

수집 대상은 세 가지다.

- 커뮤니티 글 작성: 제목 + 내용
- 케어 요청 작성: 제목 + 설명 + `petType`
- 주변서비스 위치 검색어: 자연어로 보이는 검색어

흐름은 다음과 같다.

```text
사용자 활동 text
-> Spring 이벤트 발행
-> PetIntentSignalEventListener
-> petory-nlp-server POST /api/pet-intent/analyze
-> intentDomain / intent / recommendedCategories / tags / urgency 반환
-> user_pet_intent_signal 저장
-> 추천 카드 / 주변서비스 추천에 사용
```

핵심 문제는 사용자 표현의 변수가 계속 생긴다는 점이다. 지금은 이 변수를 Python과 Java 코드에 하드코딩한 규칙으로 처리하고 있어, 새로운 표현이나 오탐 사례가 나올 때마다 코드를 직접 수정해야 한다.

이번 논의의 의미는 "AI가 룰 개선 PR을 만든다"보다 더 단순하다.

> 사용자의 자연어 활동을 해석하는 판단 자체를, 하드코딩 규칙 대신 AI/LLM에게 맡길 수 있는지 검토한다.

단, Java 이벤트 구조와 `user_pet_intent_signal` 저장 구조는 유지한다.

---

## 2. 현재 코드 기준 동작

### 2.1 Spring 이벤트 수집

`PetIntentSignalEventListener`가 도메인 이벤트를 받아 Python NLP 서버 호출을 트리거한다.

위치:

- `backend/main/java/com/linkup/Petory/domain/petRecommendation/service/PetIntentSignalEventListener.java`

처리:

- `CommunityPostCreatedEvent` -> `sourceType=COMMUNITY`
- `CareRequestCreatedEvent` -> `sourceType=CARE`
- `LocationSearchPerformedEvent` -> `sourceType=LOCATION_SEARCH`

위치 검색어는 과호출을 막기 위해 Java에서 먼저 필터링한다.

```text
normalize(keyword)
-> 길이 7자 이상
-> 공백 포함
-> Redis 10분 dedup 통과
-> Python NLP 호출
```

이 기준 때문에 `"강아지가귀를긁어요"`처럼 공백 없이 붙인 자연어는 현재 분석 대상에서 빠진다.

### 2.2 Python NLP 서버

위치:

- `petory-nlp-server/app/api/pet_intent_router.py`
- `petory-nlp-server/app/nlp/intent_classifier.py`
- `petory-nlp-server/app/nlp/tag_extractor.py`
- `petory-nlp-server/app/rules/urgency_rules.py`
- `petory-nlp-server/app/rules/category_rules.py`

분석 순서:

```text
text, petType 수신
-> Kiwi 형태소 분석으로 keywords 추출
-> intent_classifier._RULES 키워드 우선 매칭
-> rule miss 시 intent_examples.yml centroid와 embedding 유사도 비교
-> tag_extractor.py에서 intentTags 추출
-> urgency_rules.py에서 urgency 판단
-> category_rules.py에서 recommendedCategories 매핑
```

`petType`은 API 계약으로 받지만, 현재 Python 분류 로직에서는 실제로 사용하지 않는다.

---

## 3. 하드코딩된 판단 지점

### 3.1 Python intent rule

위치:

- `petory-nlp-server/app/nlp/intent_classifier.py`

현재 `_RULES`에 의도별 키워드가 코드로 박혀 있다.

예:

```python
("MEDICAL_CONCERN", "MEDICAL", 0.92, ["병원", "약국", "아프", "구토", "토하", ...])
("GROOMING_NEED", "GROOMING", 0.90, ["미용", "목욕", "털", "엉켰", ...])
```

문제:

- 사용자 표현이 늘어날수록 키워드를 계속 추가해야 한다.
- 짧은 키워드는 오탐 위험이 있다.
- rule confidence는 실제 모델 확률이 아니라 휴리스틱 값이다.

### 3.2 Python tag rule

위치:

- `petory-nlp-server/app/nlp/tag_extractor.py`

현재 `_KO_TAG_MAP`에 한국어 형태소와 영어 tag 매핑이 코드로 박혀 있다.

예:

```python
"귀": "ear"
"긁": "scratch"
"가렵": "itching"
"미용": "trim"
"간식": "snack"
```

문제:

- Kiwi lemma에 맞춰 계속 수정해야 한다.
- 새 증상, 새 상품군, 새 생활 표현이 나오면 코드 수정이 필요하다.

### 3.3 Python urgency rule

위치:

- `petory-nlp-server/app/rules/urgency_rules.py`

현재 긴급도는 문자열 포함 여부로 판단한다.

예:

```python
_HIGH_URGENCY_KEYWORDS = [
    "응급", "위급", "쓰러", "피", "출혈", "숨", "호흡", "경련", "발작",
    "못 먹", "이틀", "사흘", "며칠째", "계속", "심해"
]
```

문제:

- `"피"` 같은 짧은 표현은 맥락에 따라 오탐 가능성이 있다.
- 반대로 실제 위급한 표현이 목록에 없으면 놓친다.
- 의료/응급은 오탐과 미탐 비용이 모두 크다.

### 3.4 Java 자연어 판단

위치:

- `backend/main/java/com/linkup/Petory/domain/petRecommendation/service/PetIntentSignalEventListener.java`

현재 위치 검색어는 아래 조건을 만족해야 Python 분석까지 간다.

```java
return n.length() >= MIN_NL_LENGTH && n.contains(" ");
```

문제:

- 공백 없는 자연어를 놓친다.
- 짧지만 의미 있는 검색어를 놓친다.
- 자연어 여부를 Java 휴리스틱이 결정한다.

### 3.5 Java NLP 장애 fallback

위치:

- `backend/main/java/com/linkup/Petory/domain/petRecommendation/service/PetRecommendationService.java`

NLP 서버가 실패하면 Java 내부에서 다시 키워드 포함 여부로 카테고리를 추정한다.

```java
if (text.contains("병원") || text.contains("약국") || text.contains("아파") || text.contains("긁")) return "동물병원";
if (text.contains("미용") || text.contains("털") || text.contains("목욕")) return "미용";
```

문제:

- Python rule과 Java fallback rule이 따로 존재한다.
- 한쪽만 수정하면 동작이 달라질 수 있다.
- 표현 변화에 취약하다.

### 3.6 Java signal 저장 정책

위치:

- `backend/main/java/com/linkup/Petory/domain/petRecommendation/service/UserPetIntentSignalService.java`

현재 저장 threshold, TTL, 카드 문구가 Java 코드에 박혀 있다.

예:

- `MEDICAL` threshold: `0.65`
- `MEDICAL + HIGH` threshold: `0.55`
- `MEDICAL` TTL: `HIGH=1일`, 그 외 `3일`
- 카드 문구: `switch (domain)`

이 부분은 자연어 이해라기보다 서비스 정책이다. AI에게 넘기기보다는 코드/설정으로 명확히 유지하는 편이 맞다.

---

## 4. AI에게 넘길 수 있는 부분과 남겨야 하는 부분

### 4.1 AI에게 넘기기 좋은 부분

AI/LLM이 맡기 좋은 것은 "문장을 보고 구조화된 의도 JSON을 만드는 일"이다.

입력:

```json
{
  "text": "강아지가 어제부터 밥도 안 먹고 계속 토해요",
  "petType": "DOG"
}
```

출력:

```json
{
  "intentDomain": "MEDICAL",
  "intent": "MEDICAL_CONCERN",
  "recommendedCategories": ["동물병원", "동물약국"],
  "confidence": 0.86,
  "keywords": ["강아지", "밥", "안 먹", "토"],
  "intentTags": ["appetite_loss", "vomit"],
  "urgency": "HIGH",
  "message": "구토와 식욕 저하 표현이 감지되었습니다. 증상이 지속되면 가까운 동물병원에 문의하세요."
}
```

AI 대체 후보:

- intentDomain 판단
- intent 판단
- keywords 추출
- intentTags 추출
- urgency 판단
- 간단한 사용자 안내 message 생성

### 4.2 AI에게 바로 넘기면 안 되는 부분

서비스 정책과 안전장치는 코드에 남기는 편이 맞다.

- signal 저장 threshold
- signal TTL
- 중복 signal 저장 방지
- 추천 카드 target tab/category 결정
- 의료/응급 최소 안전 rule
- NLP 서버 장애 fallback
- 원문 저장 여부 같은 개인정보 정책

특히 의료/응급은 AI 판단만 믿으면 안 된다. AI가 `NORMAL`로 판단해도 코드 레벨에서 위험 키워드가 있으면 `HIGH`로 올리는 guard rule이 필요하다.

---

## 5. 권장 구조

기존 Java 이벤트 구조와 DB 저장 구조는 유지한다.

Python NLP 서버 내부만 다음처럼 바꾼다.

```text
Spring
-> POST /api/pet-intent/analyze
-> petory-nlp-server
   -> AI classifier 우선 시도
   -> JSON schema validation
   -> medical/urgency guard rule 적용
   -> 실패 시 기존 rule + embedding fallback
-> 기존 응답 계약 그대로 반환
```

권장 파일 구조:

```text
petory-nlp-server/app/nlp/
 ├─ ai_classifier.py          # LLM 호출 및 JSON 파싱
 ├─ intent_classifier.py      # 기존 rule + embedding fallback
 ├─ safety_guard.py           # 의료/응급 보정 rule
 ├─ tag_extractor.py          # fallback 또는 guard 보조
 └─ tokenizer.py
```

라우터 흐름 예시:

```python
try:
    analysis = classify_with_ai(req.text, pet_type=req.petType)
    analysis = apply_safety_guard(req.text, analysis)
except Exception:
    intent, domain, confidence = classify(req.text, pet_type=req.petType)
    keywords = extract_keywords(req.text)
    intent_tags = extract_tags(req.text, domain)
    urgency = judge_urgency(req.text, domain)
```

중요한 점은 API 응답 계약을 유지하는 것이다. Spring은 이미 `PetIntentAnalyzeResponse`를 기준으로 동작하므로, Python 내부 구현만 교체하면 Java 변경을 최소화할 수 있다.

---

## 6. MVP 구현 방향

### Phase 1 — AI classifier를 Python 서버 내부에 추가

목표:

- 기존 endpoint 유지
- 기존 응답 JSON 유지
- AI 호출 실패 시 기존 rule/embedding fallback 사용

변경 후보:

- `app/nlp/ai_classifier.py` 추가
- `app/nlp/safety_guard.py` 추가
- `app/api/pet_intent_router.py`에서 AI 우선 경로 추가

### Phase 2 — 의료/응급 guard rule 유지

AI 결과가 낮은 urgency를 반환하더라도, 아래 표현이 있으면 코드에서 `HIGH`로 보정한다.

예:

- 숨을 못 쉼
- 호흡 이상
- 피/출혈
- 경련/발작
- 계속 구토
- 쓰러짐
- 며칠째 못 먹음

이 guard는 AI를 대체하는 것이 아니라, AI 위에 얹는 안전장치다.

### Phase 3 — 기존 rule/embedding을 fallback으로 유지

초기에는 기존 `intent_classifier.py`, `tag_extractor.py`, `urgency_rules.py`를 제거하지 않는다.

이유:

- AI API 장애 시 기본 기능 유지
- 비용/timeout 문제 방어
- 테스트 기준 유지
- 점진 전환 가능

### Phase 4 — Java 위치 검색 자연어 필터 완화 검토

AI가 자연어 판단을 더 잘한다면 Java의 `length >= 7 && contains(" ")` 필터는 너무 강할 수 있다.

다만 무조건 제거하면 Python/AI 호출량이 늘어난다.

MVP에서는 다음 정도가 현실적이다.

```text
기존 조건 유지
+ 의료/돌봄/증상 관련 짧은 검색어 whitelist 추가
+ Redis dedup 유지
```

예:

- `귀 긁음`
- `토해요`
- `밥 안먹음`
- `강아지호텔`

### Phase 5 — petType 계약 정리

현재 Java와 Python의 `petType` 계약이 다르다.

Java 케어 이벤트 주석:

```text
DOG | CAT | BIRD | RABBIT | HAMSTER | ETC | null
```

Python request schema:

```text
DOG | CAT | OTHER
```

따라서 `BIRD`, `RABBIT`, `HAMSTER`, `ETC`가 Python으로 넘어가면 422 응답이 날 수 있다.

AI 전환 전에 아래 중 하나로 정리해야 한다.

- Python `PetType` enum을 Java와 맞춘다.
- Java에서 DOG/CAT 외 값을 `OTHER`로 매핑해서 보낸다.
- Python에서 `petType: Optional[str]`로 받고 unknown value를 허용한다.

---

## 7. Claude/Codex에 물어볼 질문

아래처럼 질문하면 의도를 더 정확히 전달할 수 있다.

```text
현재 Petory는 사용자 활동(커뮤니티 글, 케어 요청, 위치 검색어)을 Java 이벤트로 잡고,
petory-nlp-server에 text/petType을 보내 intentDomain, tags, urgency, recommendedCategories를 받아
user_pet_intent_signal에 저장하는 구조야.

그런데 지금 petory-nlp-server는 intent_classifier.py의 키워드 룰,
tag_extractor.py의 태그 매핑, urgency_rules.py의 긴급도 키워드처럼
파이썬 코드에 하드코딩된 규칙이 많아.

문제는 사용자 표현이 계속 달라져서 룰을 계속 수정해야 한다는 거야.
그래서 이런 자연어 판단 부분을 LLM/AI에게 넘기고 싶어.

단, Java의 이벤트 구조와 user_pet_intent_signal 저장 구조는 유지하고 싶어.
AI는 text와 petType을 받아 아래 JSON을 안정적으로 반환해야 해.

- intentDomain
- intent
- recommendedCategories
- confidence
- keywords
- intentTags
- urgency
- message

질문:
1. 현재 코드 구조에서 어떤 하드코딩 판단을 AI로 대체할 수 있는지 분석해줘.
2. 기존 rule/embedding 방식은 완전히 제거하는 게 좋은지, AI 실패 시 fallback으로 두는 게 좋은지 판단해줘.
3. 의료/응급 intent는 안전하게 처리해야 하는데 어떤 guard rule을 남겨야 하는지 알려줘.
4. petory-nlp-server에 ai_classifier.py 같은 레이어를 추가한다면 구조를 어떻게 잡는 게 좋은지 알려줘.
5. 가장 작은 MVP 구현 순서를 제안해줘.
```

---

## 8. 결론

AI에게 넘길 대상은 "사용자 활동 수집"이 아니다. 사용자 활동 수집은 Java 이벤트 구조로 이미 잘 분리되어 있다.

AI에게 넘길 대상은 그 다음 단계인 "활동 텍스트를 어떤 의도/태그/긴급도로 해석할지"이다.

권장 방향:

```text
Java 이벤트 수집/저장 구조 유지
-> Python NLP 서버 내부에 AI classifier 추가
-> 기존 rule/embedding은 fallback으로 유지
-> 의료/응급 safety guard는 코드로 유지
-> 응답 JSON 계약은 유지
```

이 방식이 가장 작은 변경으로 하드코딩 자연어 판단 문제를 줄이면서, 기존 추천 signal 구조와 운영 안정성을 유지할 수 있다.
