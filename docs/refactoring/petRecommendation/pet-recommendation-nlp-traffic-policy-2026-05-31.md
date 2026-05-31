# petRecommendation 도메인 — NLP 호출 정책과 버스트 트래픽 대응 (2026-05-31)

**도메인**: petRecommendation (의도 분석 기반 추천)  
**범위**: Spring 서버가 Python NLP 서버를 호출하는 시점, 비용 성격, 버스트 대응 설계  
**핵심 결론**: Location 검색은 "트래픽 버스트"보다 **호출 정책 부족** 문제이고, 게시글/케어 작성은 **순간 피크 시 비동기 백로그 관리** 문제다.

---

## 1. 용어 정리

| 용어 | 의미 | 이 문서에서의 적용 |
|------|------|------------------|
| 트래픽 버스트 | 짧은 시간에 요청이 몰리는 현상 | 게시글/케어 작성 이벤트가 순간적으로 대량 발생 |
| 피크 트래픽 | 특정 순간의 최대 부하 | 이벤트성 유입, 테스트, 악성/반복 요청 |
| 백로그 | 처리 속도보다 유입 속도가 빨라 큐에 쌓인 작업 | `@Async` NLP 분석 작업 큐 |
| Backpressure | 시스템이 감당 가능한 만큼만 받아들이도록 제한하는 설계 | bounded executor, rate limit, rejection 정책 |
| Graceful degradation | 부가 기능 실패 시 핵심 기능은 유지하는 설계 | 추천 signal 저장은 생략해도 게시글/케어 작성은 성공 |

---

## 2. Python NLP 호출 지점

모든 Python 호출은 Spring의 `PetIntentClient.analyze()`로 모인다.

```text
Spring
  -> PetIntentClient.analyze(text, petType)
  -> POST http://localhost:8000/api/pet-intent/analyze
  -> Python NLP 서버
```

현재 호출 진입점은 크게 네 가지다.

| 진입점 | 발생 조건 | 처리 방식 | 비용 성격 |
|--------|----------|----------|----------|
| 게시글 작성 | 커뮤니티 게시글 생성 성공 후 | `AFTER_COMMIT` + `@Async` | 버스트 시 백로그 문제 |
| 케어 요청 작성 | 케어 요청 생성 성공 후 | `AFTER_COMMIT` + `@Async` | 버스트 시 백로그 문제 |
| 주변서비스 검색 | 로그인 사용자가 keyword 있는 검색 실행 | `@EventListener` + `@Async` | 호출 정책 부족 문제 |
| `/api/pet-recommend` 직접 호출 | 추천 API 직접 요청 | 동기 호출 | 응답 지연 문제 |

---

## 3. 두 문제는 성격이 다르다

### 3.1 Location 검색: 호출 정책 부족

Location 검색은 대규모 트래픽이 아니어도 불필요한 Python 호출이 발생할 수 있다.

현재 흐름:

```text
사용자 주변서비스 검색
  -> /api/location-services/search
  -> LocationServiceService.searchLocationServices()
  -> publishSearchEvent(keyword)
  -> LocationSearchPerformedEvent
  -> @Async Python NLP 호출
  -> confidence 통과 시 signal 저장
```

`publishSearchEvent(keyword)`는 다음 조건이면 이벤트를 발행한다.

- keyword가 비어 있지 않음
- 인증된 사용자임

즉, 문제는 "사용자가 많아서"라기보다 **어떤 검색을 추천 의도 분석 대상으로 볼 것인가**가 명확하지 않은 점이다.

예를 들어 검색어가 남아 있는 상태에서 카테고리, 정렬, 반경 변경으로 검색 API가 다시 호출되면 같은 keyword로 NLP 분석이 반복될 수 있다. 이 경우 `UserPetIntentSignalService`에서 중복 signal 저장을 막아도 Python 호출 비용은 이미 발생한 뒤다.

#### Location 검색의 개선 방향

Location 검색은 트래픽 버스트 대응보다 먼저 호출 조건을 좁혀야 한다.

권장 정책:

```text
주변서비스 검색 이벤트
  -> keyword 없음: 분석하지 않음
  -> 너무 짧은 keyword: 분석하지 않음
  -> 단순 카테고리/정렬/반경 변경: 분석하지 않음
  -> 같은 user + normalized keyword 최근 분석됨: 분석하지 않음
  -> 통과한 자연어성 검색어만 Python 분석
```

적용 후보:

- user + normalized keyword 기준 TTL dedup
- keyword 최소 길이 또는 형태 조건
- 카테고리/정렬/반경 변경과 사용자 검색 제출 이벤트 분리
- `LOCATION_SEARCH` source는 게시글/케어보다 낮은 우선순위로 처리

---

### 3.2 게시글/케어 작성: 버스트 트래픽과 백로그 관리

게시글/케어 작성은 Location 검색과 다르게 사용자가 명시적으로 콘텐츠를 생성하는 저빈도 액션이다. 하루 총량이 수천 건이어도 시간에 고르게 분산되면 큰 문제가 아니다.

문제는 짧은 시간에 작성 이벤트가 몰리는 순간 피크다.

현재 흐름:

```text
사용자 게시글/케어 작성
  -> Tomcat 요청 스레드
  -> DB connection 획득
  -> 트랜잭션 안에서 데이터 저장
  -> commit 성공
  -> AFTER_COMMIT 이벤트 처리
  -> @Async NLP 작업 큐 적재
  -> worker thread가 Python HTTP 호출
  -> signal 저장
```

여기서 비동기는 사용자 응답 경로에서 Python 호출을 분리할 뿐, 서버 비용을 없애지 않는다.

```text
비동기 = 게시글/케어 작성 응답을 Python 지연으로부터 보호
비동기 != Python 호출 비용 제거
```

#### 서버/OS/네트워크 관점 병목

수천 명이 동시에 작성하는 경우 실제 병목은 단계별로 생긴다.

| 계층 | 병목 요소 | 설명 |
|------|----------|------|
| OS | TCP 연결, file descriptor, accept queue | 짧은 순간 연결이 몰리면 커널 큐와 FD 한도 영향 |
| WAS | Tomcat request thread | 요청을 처리할 수 있는 Java 요청 스레드 수 제한 |
| DB | Hikari connection pool | 작성 트랜잭션은 DB connection 수만큼만 동시 진행 |
| JVM | `@Async` executor queue | commit 후 NLP 작업이 큐에 누적 |
| Network | Java -> Python HTTP socket | 동시 Python 호출 수만큼 소켓 사용 |
| Python | uvicorn worker, anyio threadpool, CPU | NLP 추론이 CPU-bound이면 처리량 제한 |

따라서 "수천 명이 동시에 작성"한다고 해서 Python 호출 수천 개가 동시에 실행되는 것은 아니다. Tomcat, DB pool, async executor가 앞에서 순차적으로 병목을 만든다.

하지만 유입량이 처리량보다 크면 `@Async` 큐에 NLP 작업이 계속 쌓이고, 오래된 분석이 지연되거나 timeout으로 실패할 수 있다.

---

## 4. 현재 구조의 위험 지점

### 4.1 기본 `@Async` executor 의존

현재 애플리케이션에는 `@EnableAsync`가 있지만 petRecommendation 전용 executor 설정은 없다.

이 경우 NLP 작업은 Spring Boot 기본 async executor 정책에 의존한다. 기본 executor는 운영 의도를 코드로 드러내지 못하고, 다른 `@Async` 작업과 자원을 공유할 수 있다.

문제:

- NLP 작업이 이메일, 감사 로그, 모임 채팅방 생성 등 다른 비동기 작업을 밀 수 있음
- 큐가 길어졌을 때 어떤 작업을 버릴지 정책이 없음
- Python 장애 시 timeout 작업이 누적될 수 있음
- 처리 지연이 관측되지 않으면 추천 카드가 늦게 뜨는 원인을 파악하기 어려움

### 4.2 Python 서버는 외부 의존성이다

Python 서버가 같은 머신의 localhost에 있더라도 Spring 입장에서는 HTTP 외부 의존성이다.

장애 형태:

- Python 서버 다운
- 모델 lazy loading으로 첫 요청 지연
- CPU 포화로 응답 지연
- Java read timeout 발생
- Java는 timeout 처리했지만 Python은 이미 받은 요청을 계속 처리

따라서 추천 분석은 핵심 트랜잭션과 분리하고, 감당 가능한 만큼만 처리해야 한다.

---

## 5. 대응 설계

### 5.1 게시글/케어: bounded executor

게시글/케어 작성에서 발생하는 NLP 분석은 전용 executor로 분리한다.

설계 원칙:

```text
핵심 기능: 게시글/케어 작성 성공
부가 기능: 추천 signal 생성

부가 기능이 밀리거나 실패해도 핵심 기능은 실패시키지 않는다.
```

권장 구조:

```text
CommunityPostCreatedEvent / CareRequestCreatedEvent
  -> AFTER_COMMIT
  -> @Async("petIntentExecutor")
  -> bounded queue
  -> Python NLP 호출
  -> signal 저장
```

권장 설정 예시:

| 설정 | 예시 | 의도 |
|------|------|------|
| core pool size | 2~4 | 평시 처리 worker |
| max pool size | 4~8 | 짧은 피크 처리 |
| queue capacity | 500~2000 | 무제한 backlog 방지 |
| rejection policy | discard + warn log 또는 metric | 추천 분석 생략, 핵심 요청 보호 |
| thread name prefix | `pet-intent-` | 로그/스레드 덤프 추적 |

추천 signal은 부가 기능이므로 큐가 포화되면 일부 분석을 포기하는 정책이 합리적이다.

### 5.2 Location 검색: 호출 조건 축소

Location 검색은 executor보다 먼저 분석 대상 자체를 줄인다.

권장 구조:

```text
Location 검색 요청
  -> keyword 정규화
  -> 분석 대상 여부 판단
  -> 최근 동일 user + keyword 분석 여부 확인
  -> 통과한 요청만 LocationSearchPerformedEvent 발행
```

예시 정책:

| 조건 | 처리 |
|------|------|
| keyword 없음 | 분석 안 함 |
| keyword 길이 1~2자 | 분석 안 함 |
| category/sort/radius만 변경 | 분석 안 함 |
| 같은 user + keyword가 최근 10분 내 분석됨 | 분석 안 함 |
| 자연어성 keyword | 분석 후보 |

이 정책은 "트래픽 버스트 방어"라기보다 기능 자체의 호출 정확도를 높이는 작업이다.

### 5.3 공통 보호 장치

게시글/케어와 Location 검색 모두에 적용 가능한 보호 장치:

- Python 호출 timeout 유지
- timeout/실패 시 원 액션에는 영향 없음
- 실패 로그에 sourceType 포함
- user + text hash 기준 단기 dedup
- Python 장애율이 높으면 일정 시간 호출 중단하는 circuit breaker 검토
- executor queue size, rejection count, timeout count 관측

---

## 6. 포트폴리오 표현

이 작업은 단순히 "AI 추천 기능을 붙였다"가 아니라, 느린 외부 의존성을 서비스 안정성과 분리한 설계로 설명할 수 있다.

추천 제목:

> NLP 추천 파이프라인의 비동기 분리와 버스트 트래픽 보호 설계

핵심 설명:

```text
게시글/케어 작성 후 NLP 분석을 AFTER_COMMIT 비동기 이벤트로 분리해
핵심 트랜잭션과 Python 서버 의존성을 분리했다.

또한 전용 bounded executor와 rejection 정책을 통해 순간 피크 시
JVM queue가 무제한 증가하지 않도록 backpressure를 설계했다.

Location 검색은 트래픽 문제가 아니라 호출 정책 문제로 분리해,
사용자 검색 의도 분석 대상만 선별하도록 TTL dedup과 조건 기반 발행 정책을 설계했다.
```

강조 키워드:

- `AFTER_COMMIT` event
- async decoupling
- bounded queue
- backpressure
- graceful degradation
- timeout isolation
- TTL dedup

---

## 7. 우선순위

| 우선순위 | 작업 | 이유 |
|----------|------|------|
| 1 | petRecommendation 전용 bounded executor | 버스트 시 JVM queue 무제한 누적 방지 |
| 2 | Location 검색 keyword dedup/호출 조건 축소 | 불필요한 Python 호출 자체 감소 |
| 3 | executor rejection/timeout metric | 운영 중 병목 관측 |
| 4 | Python 모델 warmup | 첫 요청 timeout 완화 |
| 5 | circuit breaker | Python 장애 장기화 시 Spring 보호 |

---

## 8. 최종 판단

Location 검색과 게시글/케어 작성은 같은 Python NLP 서버를 쓰지만 문제 성격이 다르다.

```text
Location 검색
  = 호출 정책 부족
  = 언제 분석할지 더 엄격히 정해야 함

게시글/케어 작성
  = 트래픽 버스트/백로그 관리
  = 순간 피크 때 얼마나 받아들이고 언제 포기할지 정해야 함
```

따라서 두 문제를 한꺼번에 "대규모 트래픽 문제"로 묶기보다, Location은 기능 정책 개선으로, 게시글/케어는 backpressure 설계로 분리해 다루는 것이 맞다.
