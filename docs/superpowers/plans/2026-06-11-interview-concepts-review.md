# Interview Concepts 전체 점검 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `docs/interview/concepts/` 14개 파일을 실제 코드와 대조해 면접 기술 노트를 최신 상태로 정확하게 갱신한다.

**Architecture:** 각 파일마다 동일한 3단계(Read doc → Find code → Update doc)를 반복한다. 코드 기반 사실 확인 후 문서를 수정하는 방식으로, 개념 설명이 틀리거나 빠진 부분을 코드 레벨에서 근거를 잡아 보완한다.

**Tech Stack:** Java 17, Spring Boot 3.5.7, JPA/Hibernate, MySQL 8.0(Spatial/FULLTEXT), Redis, WebSocket(STOMP), SSE, FastAPI(Python)

---

## 점검 원칙 — 매 파일 공통

각 Task에서 아래 4가지를 기준으로 확인한다.

| 체크 포인트 | 구체적으로 볼 것 |
|------------|----------------|
| **사실 오류** | 문서에 적힌 클래스명·메서드명·쿼리가 실제 코드와 다른가 |
| **누락** | 코드에 있는 핵심 패턴이 문서에 빠져 있는가 |
| **최신성** | 최근 커밋(petRecommendation, notification PET_HEALTH_ALERT 등)이 반영되어 있는가 |
| **면접 Q&A** | "왜?"를 설명하는 답변이 구체적인가, 트레이드오프가 있는가 |

**완료 기준:** 위 4가지를 확인하고, 수정이 필요하면 수정한 상태로 파일 저장 완료.

---

## 실행 순서 — 면접 중요도 기준

```
Phase 1 (★★★ 필수): 03 → 02 → 04
Phase 2 (★★ 차별화): 07 → 05 → 06
Phase 3 (★ 기본):    01 → 08 → 09
Phase 4 (★★★ 도메인): 11 → 12 → 13 → 14
Phase 5 (마무리):     10 (도메인별 공부법) + 새 도메인 신규 파일 필요 여부 판단
```

---

## Phase 1 — ★★★ 반드시 완벽히

### Task 1: 03_동시성_제어.md 점검

**코드 대상 파일:**
- `domain/payment/repository/` — `findByIdForUpdate` (비관적 락)
- `domain/meetup/repository/` — 원자적 UPDATE, `SpringDataJpaMeetupParticipantsRepository`
- `domain/care/repository/` — CareRequest 동시성
- `domain/user/repository/` — 경고 횟수 원자적 증가

- [ ] **Step 1: 현재 문서 전체 읽기**

  ```bash
  cat docs/interview/concepts/03_동시성_제어.md
  ```

- [ ] **Step 2: 실제 비관적 락 코드 확인**

  ```bash
  grep -rn "LockModeType\|@Lock\|FOR UPDATE\|findByIdForUpdate" \
    backend/main/java/com/linkup/Petory/domain/payment/ \
    backend/main/java/com/linkup/Petory/domain/care/
  ```
  확인 포인트: `@Lock(LockModeType.PESSIMISTIC_WRITE)` 어노테이션이 어느 Repository에 붙어 있는지, 파라미터 타입, 리턴 타입이 문서와 일치하는지.

- [ ] **Step 3: 원자적 UPDATE 쿼리 확인**

  ```bash
  grep -rn "@Modifying\|@Query.*UPDATE\|incrementWarning\|currentParticipants" \
    backend/main/java/com/linkup/Petory/domain/meetup/repository/ \
    backend/main/java/com/linkup/Petory/domain/user/repository/
  ```
  확인 포인트: `@Modifying(clearAutomatically = true)` 여부, 실제 JPQL/네이티브 쿼리 내용.

- [ ] **Step 4: 3중 방어 레이어 순서 코드 검증**

  ```bash
  grep -rn "synchronized\|@Transactional\|isolation\|SERIALIZABLE" \
    backend/main/java/com/linkup/Petory/domain/meetup/service/
  ```

- [ ] **Step 5: 문서 갱신 — 사실 오류/누락 수정**

  수정 시 지켜야 할 규칙:
  - 실제 메서드 시그니처를 코드에서 복사해서 삽입
  - "왜 낙관적 락이 아니라 비관적 락인가?" Q&A에 트레이드오프(재시도 비용 vs 충돌 확률) 구체화
  - petCoin 에스크로에서 이중 락 금지 이유 확인 후 반영

---

### Task 2: 02_공간쿼리_Haversine.md 점검

**코드 대상 파일:**
- `domain/location/repository/` — ST_Within, ST_Distance_Sphere 쿼리
- `domain/location/util/` — Haversine 계산 유틸
- `domain/location/service/` — 2단계 전략 서비스 레이어

- [ ] **Step 1: 현재 문서 전체 읽기**

  ```bash
  cat docs/interview/concepts/02_공간쿼리_Haversine.md
  ```

- [ ] **Step 2: 실제 공간 쿼리 확인**

  ```bash
  grep -rn "ST_Within\|ST_Distance_Sphere\|MBR\|POINT\|ST_Buffer\|Haversine\|haversine" \
    backend/main/java/com/linkup/Petory/domain/location/
  ```
  확인 포인트: MBR 바운딩 박스 쿼리가 1차로 실행되고 Haversine 정밀 계산이 2차로 실행되는 순서가 코드에서 맞는지.

- [ ] **Step 3: 공간 인덱스 설정 확인**

  ```bash
  grep -rn "SPATIAL\|spatial\|@Column.*columnDefinition.*POINT\|SRID" \
    backend/main/java/com/linkup/Petory/domain/location/entity/
  ```

- [ ] **Step 4: 문서 갱신**

  수정 시 집중할 포인트:
  - 실제 `@Query` 어노테이션 내 JPQL/네이티브 SQL을 문서에 삽입
  - "왜 2단계 전략인가?" — ST_Within만 쓰면 원형 반경과 MBR 오차 설명 보강
  - 면접 Q: "공간 인덱스가 없으면 성능이 얼마나 차이 나는가?" 추가

---

### Task 3: 04_JPA_N+1.md 점검

**코드 대상 파일:**
- `domain/meetup/repository/SpringDataJpaMeetupRepository.java`
- `domain/board/repository/`
- `domain/care/repository/`

- [ ] **Step 1: 현재 문서 전체 읽기**

  ```bash
  cat docs/interview/concepts/04_JPA_N+1.md
  ```

- [ ] **Step 2: Fetch Join / EntityGraph 실제 사용 확인**

  ```bash
  grep -rn "EntityGraph\|JOIN FETCH\|fetchJoin\|@BatchSize\|default_batch_fetch_size" \
    backend/main/java/com/linkup/Petory/domain/meetup/ \
    backend/main/java/com/linkup/Petory/domain/board/ \
    backend/main/java/com/linkup/Petory/domain/care/
  ```

- [ ] **Step 3: 2-Query 패턴 실제 적용 확인**

  ```bash
  grep -rn "findAll\b\|findAllById\|In(" \
    backend/main/java/com/linkup/Petory/domain/meetup/repository/ \
    backend/main/java/com/linkup/Petory/domain/board/repository/
  ```

- [ ] **Step 4: Soft Delete 구현 확인**

  ```bash
  grep -rn "@SQLRestriction\|@Where\|deletedAt\|isDeleted\|@SQLDelete" \
    backend/main/java/com/linkup/Petory/domain/
  ```

- [ ] **Step 5: 문서 갱신**

  집중 포인트:
  - 문서에 "301 쿼리 → 3 쿼리" 사례가 있는데 실제 어느 조회에서 발생했는지 Repository 메서드명 명시
  - Soft Delete: `@SQLRestriction` 쓰는지 `@Where` 쓰는지 실제 어노테이션으로 확인 후 수정
  - 컬렉션 페이징 시 countQuery 분리 패턴 있으면 추가

---

## Phase 2 — ★★ 차별화 포인트

### Task 4: 07_이벤트_트랜잭션_배치.md 점검

**코드 대상 파일:**
- `domain/meetup/event/` — ApplicationEvent 클래스
- `domain/meetup/service/` — `@TransactionalEventListener(phase = AFTER_COMMIT)`
- `domain/statistics/service/` — Daily Summary 배치
- `global/aspect/` — AOP 관련

- [ ] **Step 1: 현재 문서 읽기**

  ```bash
  cat docs/interview/concepts/07_이벤트_트랜잭션_배치.md
  ```

- [ ] **Step 2: afterCommit() 이벤트 리스너 확인**

  ```bash
  grep -rn "afterCommit\|AFTER_COMMIT\|TransactionalEventListener\|ApplicationEventPublisher\|publishEvent" \
    backend/main/java/com/linkup/Petory/domain/
  ```
  확인: 어떤 이벤트가 `AFTER_COMMIT`으로 처리되는지 목록 작성 (채팅방 생성, 알림 등).

- [ ] **Step 3: 배치 스케줄러 확인**

  ```bash
  grep -rn "@Scheduled\|cron\|DailyStatistics\|@EnableScheduling" \
    backend/main/java/com/linkup/Petory/domain/statistics/ \
    backend/main/java/com/linkup/Petory/domain/
  ```

- [ ] **Step 4: AOP 트랜잭션 범위 확인**

  ```bash
  grep -rn "@Aspect\|@Around\|Pointcut\|@Timed\|RepositoryLogging" \
    backend/main/java/com/linkup/Petory/global/aspect/ \
    backend/main/java/com/linkup/Petory/domain/meetup/aspect/
  ```

- [ ] **Step 5: 문서 갱신**

  집중 포인트:
  - `AFTER_COMMIT` 쓰는 이유 (트랜잭션 롤백 시 채팅방 생성 방지)를 실제 이벤트 클래스명과 함께 명시
  - `@Scheduled` 스케줄러에서 `@Transactional` 사용 시 주의사항(Task 8 12번 파일과 연계)
  - 최신 커밋에 추가된 `petRecommendation` 시그널 이벤트 처리가 있으면 반영

---

### Task 5: 05_알고리즘_점수설계.md 점검

**코드 대상 파일:**
- `domain/petRecommendation/scoring/` — 점수 계산 로직 (최신 추가)
- `domain/meetup/service/` — 홈 랭킹 알고리즘
- `domain/petRecommendation/entity/` — urgency, threshold, TTL

- [ ] **Step 1: 현재 문서 읽기**

  ```bash
  cat docs/interview/concepts/05_알고리즘_점수설계.md
  ```

- [ ] **Step 2: 최신 petRecommendation 점수 로직 확인**

  ```bash
  find backend/main/java/com/linkup/Petory/domain/petRecommendation/scoring/ -name "*.java"
  cat backend/main/java/com/linkup/Petory/domain/petRecommendation/scoring/*.java
  ```
  확인: urgency별 가중치, threshold, TTL 세분화 로직이 문서에 없으면 추가.

- [ ] **Step 3: 홈 랭킹 가중치 쿼리 확인**

  ```bash
  grep -rn "score\|weight\|rank\|recommend\|log(" \
    backend/main/java/com/linkup/Petory/domain/meetup/service/ \
    backend/main/java/com/linkup/Petory/domain/petRecommendation/
  ```

- [ ] **Step 4: 로그 스케일 적용 부분 확인**

  ```bash
  grep -rn "Math.log\|Math.sqrt\|decay\|sigmoid" \
    backend/main/java/com/linkup/Petory/domain/
  ```

- [ ] **Step 5: 문서 갱신**

  집중 포인트:
  - 문서가 작성된 이후 추가된 petRecommendation Phase 0 (urgency/threshold/TTL) 섹션 추가
  - "가중치 값을 어떻게 결정했는가?" — 근거(클릭률, 케어 성사율 등) 구체화
  - SignalInteractionLog (클릭 로그) 패턴이 있으면 면접 포인트로 추가

---

### Task 6: 06_실시간통신_SSE_WebSocket.md 점검

**코드 대상 파일:**
- `domain/notification/service/` — SSE 구현
- `domain/chat/controller/ChatWebSocketController.java`
- `domain/chat/service/` — STOMP 메시지 처리

- [ ] **Step 1: 현재 문서 읽기**

  ```bash
  cat docs/interview/concepts/06_실시간통신_SSE_WebSocket.md
  ```

- [ ] **Step 2: SSE 구현 방식 확인**

  ```bash
  grep -rn "SseEmitter\|ConcurrentHashMap\|emitters\|subscribe\|sendEvent" \
    backend/main/java/com/linkup/Petory/domain/notification/
  ```
  확인: `ConcurrentHashMap<userId, SseEmitter>` 구조인지, 에러 시 remove 처리 있는지.

- [ ] **Step 3: PET_HEALTH_ALERT 최신 알림 타입 확인**

  ```bash
  grep -rn "PET_HEALTH_ALERT\|NotificationType\|MEDICAL\|HIGH.*signal" \
    backend/main/java/com/linkup/Petory/domain/notification/
  ```

- [ ] **Step 4: WebSocket STOMP 설정 확인**

  ```bash
  grep -rn "WebSocketMessageBrokerConfigurer\|configureMessageBroker\|registerStompEndpoints\|@MessageMapping" \
    backend/main/java/com/linkup/Petory/
  ```

- [ ] **Step 5: 문서 갱신**

  집중 포인트:
  - 최신 `PET_HEALTH_ALERT` SSE 타입 추가 (MEDICAL+HIGH signal → 알림 발송 플로우)
  - "SSE 연결이 끊어졌을 때" 재연결 처리 방법 코드 확인 후 Q&A 보완
  - 스케일 아웃 시 SSE 한계 (단일 서버 메모리 기반) — 면접 심화 질문 추가

---

## Phase 3 — ★ 기본 개념

### Task 7: 01_DB_인덱스.md 점검

**코드 대상 파일:**
- `domain/board/entity/` — FULLTEXT ngram 인덱스
- `domain/location/entity/` — Spatial 인덱스
- `domain/meetup/entity/` — 복합 인덱스

- [ ] **Step 1: 현재 문서 읽기**

  ```bash
  cat docs/interview/concepts/01_DB_인덱스.md
  ```

- [ ] **Step 2: 실제 인덱스 어노테이션 확인**

  ```bash
  grep -rn "@Index\|@Table.*indexes\|fulltext\|FULLTEXT\|SPATIAL\|USE INDEX" \
    backend/main/java/com/linkup/Petory/domain/ \
    backend/main/resources/ 2>/dev/null
  ```

- [ ] **Step 3: 인덱스 힌트(USE INDEX) 사용 여부 확인**

  ```bash
  grep -rn "USE INDEX\|FORCE INDEX\|nativeQuery = true" \
    backend/main/java/com/linkup/Petory/domain/
  ```

- [ ] **Step 4: 문서 갱신**

  집중: 실제 `@Table(indexes = {...})` 어노테이션 내용을 직접 인용, 카디널리티 기준 컬럼 순서 근거 보강.

---

### Task 8: 08_Redis_캐시.md 점검

**코드 대상 파일:**
- `domain/notification/service/` — 알림 캐시 최신 50개
- `domain/board/service/` — `@Cacheable` 게시글 캐시
- `domain/user/service/` — 이메일 인증 Redis 저장

- [ ] **Step 1: 현재 문서 읽기**

  ```bash
  cat docs/interview/concepts/08_Redis_캐시.md
  ```

- [ ] **Step 2: Redis 3가지 용도 실제 코드 확인**

  ```bash
  grep -rn "@Cacheable\|@CacheEvict\|redisTemplate\|ValueOperations\|opsForValue\|opsForList\|setIfAbsent" \
    backend/main/java/com/linkup/Petory/domain/
  ```

- [ ] **Step 3: TTL 설정 확인**

  ```bash
  grep -rn "expire\|TTL\|Duration\|TimeUnit\|HOURS\|DAYS" \
    backend/main/java/com/linkup/Petory/domain/notification/ \
    backend/main/java/com/linkup/Petory/domain/user/
  ```

- [ ] **Step 4: 문서 갱신**

  집중: 알림 캐시 키 포맷 및 TTL 값, `@Cacheable` cacheName 실제 값, 이메일 인증 key 포맷 확인 후 코드 인용 삽입.

---

### Task 9: 09_보안_JWT_인증.md 점검

**코드 대상 파일:**
- `filter/JwtAuthenticationFilter.java`
- `global/SecurityConfig.java`
- `util/JwtUtil.java`
- `domain/user/service/` — 로그인/토큰 재발급

- [ ] **Step 1: 현재 문서 읽기**

  ```bash
  cat docs/interview/concepts/09_보안_JWT_인증.md
  ```

- [ ] **Step 2: JWT 필터 체인 순서 확인**

  ```bash
  grep -rn "addFilterBefore\|UsernamePasswordAuthenticationFilter\|OncePerRequestFilter\|doFilterInternal" \
    backend/main/java/com/linkup/Petory/filter/ \
    backend/main/java/com/linkup/Petory/global/
  ```

- [ ] **Step 3: Refresh Token 저장 방식 확인**

  ```bash
  grep -rn "RefreshToken\|refreshToken\|saveRefreshToken\|findByToken" \
    backend/main/java/com/linkup/Petory/domain/user/
  ```

- [ ] **Step 4: @PreAuthorize Role 계층 확인**

  ```bash
  grep -rn "MASTER\|ADMIN\|SERVICE_PROVIDER\|RoleHierarchy\|@PreAuthorize" \
    backend/main/java/com/linkup/Petory/global/ \
    backend/main/java/com/linkup/Petory/domain/ | head -20
  ```

- [ ] **Step 5: 문서 갱신**

  집중: Access Token 만료 시 재발급 플로우 코드 경로 (Controller 엔드포인트 포함) 명시, OAuth2와 로컬 인증 분기 처리 방식 확인.

---

## Phase 4 — ★★★ 도메인 심화

### Task 10: 11_PG결제_PetCoin_Escrow.md 점검

**코드 대상 파일:**
- `domain/payment/repository/` — 비관적 락
- `domain/payment/service/` — 에스크로 상태 전이
- `domain/payment/entity/` — TransactionType, EscrowStatus

- [ ] **Step 1: 현재 문서 읽기**

  ```bash
  cat docs/interview/concepts/11_PG결제_PetCoin_Escrow.md
  ```

- [ ] **Step 2: 에스크로 상태 전이 enum 확인**

  ```bash
  find backend/main/java/com/linkup/Petory/domain/payment/entity/ -name "*.java" | xargs grep -l "enum\|PENDING\|HELD\|RELEASED\|REFUNDED"
  grep -rn "enum\|ESCROW\|TransactionType" backend/main/java/com/linkup/Petory/domain/payment/entity/
  ```

- [ ] **Step 3: 이중 락 금지 패턴 확인**

  ```bash
  grep -rn "findByIdForUpdate\|@Lock\|@Transactional" \
    backend/main/java/com/linkup/Petory/domain/payment/service/
  ```

- [ ] **Step 4: 문서 갱신**

  집중: 실제 `EscrowStatus` enum 값, 상태 전이 허용/불가 조합 코드 확인 후 문서 다이어그램과 대조, 이중 락 금지 이유(데드락 예시) 보강.

---

### Task 11: 12_주변서비스_CareRequest.md 점검

**코드 대상 파일:**
- `domain/care/entity/` — CareRequestStatus
- `domain/care/service/` — 스케줄러, completedAt
- `domain/care/repository/` — FULLTEXT 검색

- [ ] **Step 1: 현재 문서 읽기**

  ```bash
  cat docs/interview/concepts/12_주변서비스_CareRequest.md
  ```

- [ ] **Step 2: 스케줄러 @Transactional 패턴 확인**

  ```bash
  grep -rn "@Scheduled\|@Transactional\|autoExpire\|PENDING.*expire\|completedAt" \
    backend/main/java/com/linkup/Petory/domain/care/
  ```
  확인: 스케줄러 메서드에 `@Transactional`이 **없는** 이유 코드로 확인.

- [ ] **Step 3: CareRequest 상태 전이 확인**

  ```bash
  grep -rn "CareRequestStatus\|enum\|WAITING\|MATCHED\|COMPLETED\|CANCELLED" \
    backend/main/java/com/linkup/Petory/domain/care/entity/
  ```

- [ ] **Step 4: FULLTEXT 검색 쿼리 확인**

  ```bash
  grep -rn "MATCH\|AGAINST\|fulltext\|FULLTEXT\|IN BOOLEAN MODE" \
    backend/main/java/com/linkup/Petory/domain/care/
  ```

- [ ] **Step 5: 문서 갱신**

  집중: `completedAt` 별도 컬럼 이유(상태 전이 시점 감사), 스케줄러 `@Transactional` 금지 이유(긴 배치 트랜잭션 + Connection Pool 고갈 위험) 코드 레벨로 보강.

---

### Task 12: 13_모임_Meetup.md 점검

**코드 대상 파일:**
- `domain/meetup/repository/` — 원자적 UPDATE, 비관적 락
- `domain/meetup/service/MeetupService.java` — 3중 동시성 방어
- `domain/meetup/event/` — afterCommit 채팅
- `domain/meetup/aspect/` — @Timed AOP

- [ ] **Step 1: 현재 문서 읽기**

  ```bash
  cat docs/interview/concepts/13_모임_Meetup.md
  ```

- [ ] **Step 2: 3중 동시성 방어 실제 레이어 확인**

  ```bash
  grep -rn "findByIdForUpdate\|incrementParticipants\|@Modifying\|currentParticipants\|maxParticipants" \
    backend/main/java/com/linkup/Petory/domain/meetup/
  ```

- [ ] **Step 3: afterCommit 채팅방 생성 이벤트 확인**

  ```bash
  find backend/main/java/com/linkup/Petory/domain/meetup/event/ -name "*.java"
  grep -rn "AFTER_COMMIT\|ChatRoom\|createChatRoom\|publishEvent" \
    backend/main/java/com/linkup/Petory/domain/meetup/
  ```

- [ ] **Step 4: @Timed AOP 확인**

  ```bash
  cat backend/main/java/com/linkup/Petory/domain/meetup/aspect/*.java 2>/dev/null || \
  grep -rn "@Timed\|Timed\|aspect" backend/main/java/com/linkup/Petory/domain/meetup/
  ```

- [ ] **Step 5: 문서 갱신**

  집중: 3중 방어 레이어를 "① DB 원자적 UPDATE → ② 비관적 락 → ③ 서비스 레벨 검증" 순서로 실제 메서드명과 함께 명시, afterCommit 이유(트랜잭션 롤백 시 채팅방 고아 방지) 구체화.

---

### Task 13: 14_NLP_서버_FastAPI.md 점검

**코드 대상 파일:**
- `domain/petRecommendation/client/` — FastAPI 호출 클라이언트
- `domain/petRecommendation/service/` — NLP 결과 처리

- [ ] **Step 1: 현재 문서 읽기**

  ```bash
  cat docs/interview/concepts/14_NLP_서버_FastAPI.md
  ```

- [ ] **Step 2: Spring → FastAPI 호출 방식 확인**

  ```bash
  find backend/main/java/com/linkup/Petory/domain/petRecommendation/client/ -name "*.java"
  grep -rn "WebClient\|RestTemplate\|HttpClient\|nlp\|fastapi\|predict\|classify" \
    backend/main/java/com/linkup/Petory/domain/petRecommendation/
  ```

- [ ] **Step 3: NLP 실패 시 폴백 처리 확인**

  ```bash
  grep -rn "fallback\|Fallback\|timeout\|ConnectException\|catch\|Optional.empty" \
    backend/main/java/com/linkup/Petory/domain/petRecommendation/
  ```

- [ ] **Step 4: petType 전달 로직 확인 (최신 수정 반영)**

  ```bash
  grep -rn "petType\|BIRD\|RABBIT\|422\|silent" \
    backend/main/java/com/linkup/Petory/domain/petRecommendation/
  ```
  확인: `BIRD/RABBIT 422 silent drop` 수정 내용이 문서에 반영되어 있는지.

- [ ] **Step 5: 문서 갱신**

  집중: Spring → FastAPI 호출 방식(WebClient vs RestTemplate), 422 에러 처리 추가, Warm-up 엔드포인트 실제 URL/메서드 확인 후 명시.

---

## Phase 5 — 마무리

### Task 14: 10_도메인별_공부법.md 갱신

- [ ] **Step 1: 현재 문서 읽기**

  ```bash
  cat docs/interview/concepts/10_도메인별_공부법.md
  ```

- [ ] **Step 2: activity 도메인 추가 여부 판단**

  ```bash
  find backend/main/java/com/linkup/Petory/domain/activity/ -name "*.java" | head -10
  ```
  확인: `activity` 도메인이 충분히 복잡하면 새 개념 파일 `15_활동_petRecommendation.md` 필요 여부 판단.

- [ ] **Step 3: 8그룹 우선순위 현행화**

  최신 도메인 현황(petRecommendation Phase 0, PET_HEALTH_ALERT 알림 플로우)을 반영해 도메인 그룹 설명 업데이트.

- [ ] **Step 4: 00_목차.md 갱신**

  새로 추가된 내용이나 파일이 있으면 목차에 반영.

  ```bash
  # 목차 현재 상태 확인
  cat docs/interview/concepts/00_목차.md
  ```

---

## 완료 체크리스트

모든 Task 완료 후 아래를 확인한다.

- [ ] 14개 파일 모두 코드 대조 완료
- [ ] 각 파일에 "마지막 점검: 2026-06-11" 주석 또는 헤더 추가
- [ ] 면접 Q&A 섹션의 모든 "왜?" 답변에 트레이드오프 또는 코드 근거 포함
- [ ] 최신 커밋(petRecommendation Phase 0, PET_HEALTH_ALERT) 반영 여부 확인
- [ ] 00_목차.md 면접 중요도 순서 현행화

---

## 시간 추정

| Phase | 파일 수 | 예상 시간 |
|-------|--------|----------|
| Phase 1 (★★★) | 3개 | 45분 |
| Phase 2 (★★) | 3개 | 45분 |
| Phase 3 (★) | 3개 | 30분 |
| Phase 4 (도메인) | 4개 | 60분 |
| Phase 5 (마무리) | 1개+목차 | 20분 |
| **합계** | **14개** | **~3시간 20분** |
