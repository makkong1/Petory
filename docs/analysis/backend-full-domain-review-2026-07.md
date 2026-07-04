# 백엔드 전체 도메인 코드 리뷰 (2026-07)

> **목적**: 신입 백엔드 개발자로서 프로젝트를 어필하기 위한 변별력 포인트(DB/동시성)를 찾기 위해, 13개 도메인 · 463개 Java 파일 전체를 룰 기반으로 점검한 기록.
> **범위**: JPA/쿼리, 트랜잭션/동시성, 보안, 데이터 정합성, 코드 품질 (5개 체크리스트 카테고리)

---

## 1. 점수판 (1~3차 누적)

| 카테고리 | Critical | Warning | Info  |
| -------- | -------- | ------- | ----- |
| JPA/쿼리 | 1        | 3       | 0     |
| 트랜잭션 | 2        | 2       | 0     |
| 보안     | 1        | 3       | 0     |
| 정합성   | 0        | 2       | 0     |
| 코드품질 | 0        | 0       | 5     |
| **합계** | **4**    | **10**  | **5** |

- 1차(룰 기반 전수 스캔): Critical 3 / Warning 4 / Info 2 → §2~4
- 2차(심화: 락 순서·핫 로우·인증 흐름·실시간 채널): Critical 1 / Warning 4 / Info 1 → §8
- 3차(설정 레이어·장애 내성·테스트 현황): Warning 2 / Info 2 → §9

**✅ Critical 4건 전부 조치 완료** (2026-07-05, `feat-review-critical-fixes` 브랜치, harness 태스크 `phases/review-critical-fixes/`). 세부 내용은 각 항목 하단 참고.

---

## 2. Critical

### [A1] `@ManyToOne` fetch 전략 미지정 → EAGER 기본값 (10곳)

- **파일**: `CareRequestComment.java:30,35` / `BoardReaction.java:38,42` / `CommentReaction.java:38,42` / `MissingPetComment.java:38,42` / `LocationServiceReview.java:41,45`
- **문제**: `@ManyToOne` 기본 fetch가 EAGER라 이 엔티티들을 목록 조회할 때마다 연관 엔티티(User, Board 등)를 무조건 추가 로딩. 반응(Reaction)류는 게시글당 수백 건씩 조회되는 테이블이라 N+1 직격탄.
- **원인**: 핵심 엔티티(Escrow, Meetup, CareRequest 등)에는 LAZY가 잘 붙어있는데, 부속 엔티티에서 누락됨.
- **개선 코드**:
  ```java
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_idx", nullable = false)
  private Users user;
  ```
- **✅ 조치 완료** (`perf: 부속 엔티티 @ManyToOne fetch=LAZY 전환 10곳`, 커밋 `77ec94ce`): 5개 엔티티 10곳 모두 LAZY 전환. OSIV off(`spring.jpa.open-in-view=false`) 환경이라 조회 경로를 전수 검증했고, 연관 접근이 전부 `@Transactional` 서비스 메서드 안에서 DTO 변환까지 완결되며 목록 쿼리는 기존 `JOIN FETCH`가 이미 있어 추가 쿼리 수정은 불필요했다. 관련 AC 테스트 54건 통과(잔여 실패 4건은 변경 전 베이스라인에서도 동일하게 실패해 무관함을 확인).

### [B1+B5] private 메서드에 `@Transactional` + self-invocation

- **파일**: `OAuth2Service.java:161` (`createOrLinkUser`)
- **문제**: `createOrLinkUser`가 `private`인데 `@Transactional`이 붙어있고, 같은 클래스의 `processOAuth2Login`(line 84)에서 `this` 호출. Spring 프록시는 private 메서드와 내부 호출을 가로채지 못하므로 이 어노테이션은 조용히 무시됨.
- **완화 요인**: 호출자인 `processOAuth2Login`(line 48)이 이미 `@Transactional`이라 현재는 외부 트랜잭션 안에서 실행됨 → 지금 동작은 안전. 다만 나중에 호출자의 `@Transactional`을 제거하면 트랜잭션 없이 유저 생성이 실행되는 함정이 남음.
- **개선 코드**: private 메서드의 어노테이션을 제거하고, 트랜잭션 경계는 public 진입점 하나로 명확히.
  ```java
  // @Transactional 제거 — 트랜잭션 경계는 processOAuth2Login이 소유
  private Users createOrLinkUser(...) {
  ```
- **✅ 조치 완료** (`refactor(user): OAuth2Service 무시되는 private @Transactional 제거`, 커밋 `6ce0e39b`): 어노테이션 제거 + 경계 소유자 주석 추가. 클래스 내 동일 패턴(private + `@Transactional`) 추가 검출 0건. OAuth2 테스트 전체 통과.

### [B2] 트랜잭션 안에서 외부 I/O 호출 (FCM 푸시)

- **파일**: `NotificationService.java:65~91` (`createNotification`)
- **문제**: `createNotification`이 `@Transactional`인데 그 안에서 Redis 저장 → SSE 전송 → FCM HTTP 호출(`fcmService.sendToUser`, `@Async` 아님)까지 수행. FCM 응답이 느리면 그 시간만큼 DB 커넥션과 트랜잭션을 점유. 알림은 모든 도메인에서 호출되는 공통 경로라 커넥션 풀 고갈 시나리오의 진원지가 될 수 있음.
- **참고**: `EmailService.sendVerificationEmail`은 `@Async`로 잘 분리해놨음 — 같은 패턴을 FCM에도 적용하면 됨.
- **개선 코드** (프로젝트에 이미 있는 이벤트 패턴 활용):

  ```java
  // 트랜잭션 안: DB 저장만
  Notification saved = notificationRepository.save(notification);
  eventPublisher.publishEvent(new NotificationCreatedEvent(dto));

  // 리스너: 커밋 후 외부 전송
  @TransactionalEventListener(phase = AFTER_COMMIT)
  @Async
  public void onNotificationCreated(NotificationCreatedEvent e) {
      saveToRedis(...); sseService.send(...); fcmService.sendToUser(...);
  }
  ```

  이렇게 하면 롤백됐는데 푸시가 나가는 부작용도 함께 사라짐.

- **✅ 조치 완료** (`refactor(notification): 알림 발송을 AFTER_COMMIT 비동기 리스너로 분리`, 커밋 `5a690aa3`): `NotificationCreatedEvent` + `NotificationDispatchListener`(`@Async` + `@TransactionalEventListener(AFTER_COMMIT)`, 채널별 try-catch) 신설. `createNotification`은 이벤트 발행만 하고 Redis/SSE/FCM 호출과 관련 필드는 서비스에서 제거(`saveToRedis` → `public cacheToRedis`로 리스너에 노출). 리스너 단위테스트 3건(채널 격리 검증 포함) + Notification 테스트 전체 통과. §9 [W-NOTI]도 이 조치로 함께 해결됨.

---

## 3. Warning

### [A3] `LIKE '%kw%'` 잔존 (인덱스 무용)

- **파일**: `SpringDataJpaUsersRepository.java:128`(관리자 회원 검색), `SpringDataJpaAttachmentFileRepository.java:32`, `SpringDataJpaMeetupRepository.java:167`(location 컬럼), `SpringDataJpaLocationServiceRepository.java:83,110,127`
- **문제**: 중간 일치 LIKE는 풀스캔. Board와 Meetup 제목/본문은 이미 FULLTEXT ngram으로 전환해놓고, 나머지가 남아있음.
- **판단**: 관리자 검색은 트래픽이 낮아 Warning. 다만 `locationservice` 키워드 검색은 사용자 노출 경로라 데이터 증가 시 FULLTEXT 전환 후보 1순위.

### [A4] `findAll()` 전체 로드

- **파일**: `LocationServiceScoreScheduler.java:30`
- **문제**: 스케줄러가 locationservice 전체 테이블을 메모리에 로드. 지금 데이터 규모면 돌아가지만, 장소 데이터는 계속 늘어나는 테이블 → 페이징 배치(`Pageable` 루프 또는 `Stream` + fetch size)로 전환 필요.

### [D2] 에스크로 생성의 check-then-act 레이스

- **파일**: `PetCoinEscrowService.java:53~56`
- **문제**: `findByCareRequest().ifPresent(throw)` 후 save — 동시 요청 2개가 둘 다 체크를 통과 가능. `care_request_idx unique=true` 제약 덕분에 돈은 안전(한쪽 INSERT 실패 → 코인 차감도 같은 트랜잭션이라 롤백)하지만, 사용자에겐 500이 떨어짐.
- **개선**: `DataIntegrityViolationException` catch → `PaymentConflictException.escrowAlreadyExists()`로 변환 (`MeetupService`에서 이미 쓰는 패턴 그대로).

### [C1] 변경 API의 `@PreAuthorize` 부재 (4개 컨트롤러)

- **파일**: `PetController`(0/4), `UserProfileController`(0/6), `FcmTokenController`(0/2), `CareRequestCommentController`(0/2)
- **문제**: `SecurityConfig`의 `/api/** authenticated()` 캐치올 덕분에 인증은 걸리지만, 메서드 레벨 방어선이 없음. 서비스 레이어에서 소유권 검증을 하고 있다면 동작은 안전하나, 컨트롤러만 봐서는 권한 정책이 보이지 않음 → `@PreAuthorize("isAuthenticated()")`라도 명시해 의도를 드러내는 게 defense-in-depth.

---

## 4. Info

- **낙관적 락(`@Version`) 미사용**: 프로젝트 전체가 비관적 락 + 원자적 UPDATE 전략으로 통일됨. 나쁜 게 아니라 일관된 선택인데, 이 선택의 근거(충돌 빈도 높은 돈/정원 도메인이라 재시도 비용 > 대기 비용)를 문서에 남기면 면접 무기가 됨.
- **인덱스의 이원화**: 실제 인덱스는 SQL 마이그레이션에 있고 `@Table(indexes=...)` 선언은 6개 엔티티뿐. `ddl-auto=none`이라 동작엔 문제없지만, 엔티티만 보면 인덱스 설계가 안 보임 → 마이그레이션이 진실의 원천(source of truth)임을 README에 명시 권장.

---

## 5. 잘된 점

1. **동시성 제어가 신입 수준을 넘음** — 이 프로젝트의 최대 강점.
   - 펫코인/에스크로: `findByIdForUpdate` 비관적 락 (`PetCoinService`, `PetCoinEscrowService`)
   - 모임 정원: 비관적 락 + 조건부 원자적 UPDATE(`currentParticipants < maxParticipants`를 WHERE에 넣어 TOCTOU 원천 차단) + 유니크 충돌 시 보상 감소 쿼리까지 (`MeetupService.joinMeetup`)
   - 조회수/좋아요/미읽음/경고 횟수: 전부 `@Modifying` 원자적 UPDATE (`GREATEST(0, ...)` 음수 방어 포함)
2. **MySQL 기능을 제대로 씀**: FULLTEXT ngram 인덱스(board, meetup), SRID 4326 SPATIAL 인덱스 + 동기화 트리거(meetup, locationservice), 중복 인덱스 제거 마이그레이션에 제거 사유 주석까지.
3. **N+1 관리**: JOIN FETCH 103곳, 중첩 컬렉션은 Hibernate 제약을 이해하고 `@BatchSize`로 우회(주석으로 이유 명시).
4. **`ddl-auto=none` + 수동 SQL 마이그레이션**: 스키마를 Hibernate에 맡기지 않는 운영 감각.
5. **비즈니스 유니크 제약이 DB 레벨에 존재**: 반응 중복, 리뷰 중복, 에스크로 1:1 등 10개 엔티티.
6. **이벤트 기반 도메인 분리**: 제재(Sanction) 이벤트를 meetup/care가 각자 리스닝 — 도메인 간 직접 의존 없음.
7. `System.out.println` 0건, 비밀번호 노출 DTO 0건.

---

## 6. DB 변별력 전략 (신입 어필 관점)

결론: 재료는 이미 충분한데, 스토리와 수치가 없는 상태. 신입 변별력은 "뭘 썼냐"가 아니라 "왜 그걸 골랐고 얼마나 좋아졌는지 숫자로 말할 수 있냐"에서 갈린다.

### 이미 가진 무기 (대부분의 신입 포트폴리오에 없는 것)

- 비관적 락 + 원자적 UPDATE + 유니크 제약 + 보상 쿼리의 4중 방어 조합을 도메인 성격별로 골라 쓴 것
- SPATIAL 인덱스, FULLTEXT ngram — "인덱스 걸어봤어요" 수준이 아니라 특수 인덱스 활용
- 중복 인덱스를 제거한 이력 — 인덱스는 추가만 하는 게 아니라는 걸 아는 증거

### 부족한 것 → 해야 할 일 3가지

1. **수치를 만들어라 (최우선)**. `EXPLAIN ANALYZE`로 LIKE→FULLTEXT 전환 전후, 인덱스 추가 전후를 측정해서 "10만 건 기준 1.2s → 8ms" 같은 문장을 만든다. 측정 스크립트와 결과를 `docs/performance/`에 남기면 그 자체가 포트폴리오 페이지가 됨. 지금 마이그레이션 파일들은 결과물만 있고 개선 폭 증거가 없음.

2. **동시성 테스트를 증거로 활용해라**. ~~테스트를 작성해라~~ → **3차 리뷰에서 정정: 이미 7개 존재** (`PetCoinServiceRaceConditionTest`, `MeetupServiceRaceConditionTest` — 격리수준별/무TX 대조군 시나리오까지, `CareDealConcurrencyTest`, `AuthServiceConcurrencyTest` 등, §9 참고). 남은 일은 두 가지: ① 이 테스트들을 포트폴리오 표면으로 끌어올려라 — README/블로그에 "락 제거 시 실패, 적용 후 통과" 결과를 수치와 함께 기록. 존재만 하고 아무도 안 보는 테스트는 변별력이 안 된다. ② 커버리지 구멍 메우기 — 에스크로 생성 레이스([D2](#3-warning))와 WS 구독 인가(§8 [C-WS])는 테스트가 없다.

3. **Flyway 도입**. 지금 SQL 파일 29개(`resources/sql/migration/`)가 수동 적용 방식인데, 적용 순서/이력이 코드로 관리되지 않는다. Flyway로 전환하면 "스키마 변경 이력을 버전 관리했다"는 실운영 스토리가 하나 더 생기고, 전환 과정 자체가 블로그 글감.

### 면접용 대표 스토리 3개

1. "모임 정원 동시성: 조건부 UPDATE로 TOCTOU를 막고, 유니크 충돌 시 보상 쿼리로 정합성 유지"
2. "에스크로: 애플리케이션 체크 + DB 유니크 제약 이중 방어, 비관적 락으로 이중 지급 차단"
3. "검색 성능: LIKE 풀스캔 → FULLTEXT ngram 전환, EXPLAIN으로 검증" (수치 보강 필요)

---

## 8. 2차 심화 리뷰 (락 순서 · 핫 로우 · 인증 흐름 · 실시간 채널)

1차 룰 기반 스캔이 못 본 영역을 깊게 판 결과. 데드락 가능성, 통계 결합, 토큰 수명주기, WebSocket/SSE 채널을 점검했다.

### 🔴 Critical

#### [C-WS] WebSocket SUBSCRIBE에 목적지(destination) 인가 부재 → 채팅 도청 가능

- **파일**: `WebSocketAuthChannelInterceptor.java:48~` (인증만 수행), `ChatWebSocketController.java:75` (브로드캐스트 대상 `/topic/conversation/{idx}`)
- **문제**: 인터셉터가 CONNECT/SUBSCRIBE/SEND에서 JWT **인증**은 하지만, SUBSCRIBE의 **destination 검증이 없음**. 인증된 사용자라면 자신이 참여하지 않은 대화방의 `/topic/conversation/{다른방idx}`를 구독할 수 있고, simple broker는 구독자 전원에게 브로드캐스트하므로 **타인의 1:1 채팅 내용을 실시간 수신 가능**.
- **대비**: SEND 경로는 `ChatMessageService.sendMessage`(line 45~52)에서 참여자 + ACTIVE 상태를 검증함 — 쓰기는 막혀있고 **읽기(구독)만 뚫려있는** 비대칭 상태.
- **개선**: 인터셉터에서 `StompCommand.SUBSCRIBE`일 때 `accessor.getDestination()`을 파싱해 `/topic/conversation/{idx}` 패턴이면 `participantRepository.existsByConversationIdxAndUserIdx(...)` 검증 후 불통과 시 `return null`.
  ```java
  if (command == StompCommand.SUBSCRIBE) {
      Long convIdx = parseConversationIdx(accessor.getDestination());
      if (convIdx != null && !participantService.isActiveParticipant(convIdx, userId)) {
          log.warn("구독 인가 실패: userId={}, destination={}", userId, accessor.getDestination());
          return null; // 구독 차단
      }
  }
  ```
- **✅ 조치 완료** (`fix(security): WebSocket SUBSCRIBE 목적지 인가 추가로 대화방 도청 차단`, 커밋 `adf342fe`): 인터셉터에 `isSubscriptionAuthorized` 추가 — `/topic/conversation/{idx}`는 `ConversationParticipantRepository.findByConversationIdxAndUserIdx`로 ACTIVE·비삭제 참여자만 허용, `/user/{loginId}/...`는 본인 큐만 허용, idx 파싱 불가 시 차단. 단위테스트 6건(도청 차단·LEFT 참여자 차단·타인 큐 차단 포함) + 채팅 회귀 테스트 통과.

### 🟡 Warning

#### [W-AUTH] Refresh Token 원문 저장 + 회전(rotation) 없음

- **파일**: `Users.java:72` (`private String refreshToken` — 평문 컬럼), `AuthService.java:84~125` (`refreshAccessToken`)
- **문제**: ① 토큰 원문이 users 테이블에 그대로 저장 — DB/백업 유출 시 전 사용자 세션 탈취 가능. ② 갱신 시 기존 Refresh Token을 그대로 반환(`기존 Refresh Token 유지` 주석) — 탈취된 토큰이 만료(1일)까지 계속 유효하고, 탈취 감지 수단이 없음.
- **개선**: 저장 시 SHA-256 해시로 저장하고 조회는 해시 비교. 갱신 시 새 Refresh Token 발급(회전) — 구 토큰 재사용이 감지되면 세션 전체 무효화(토큰 재사용 감지 패턴).
- **잘한 부분**: `ensureRefreshAllowed`가 BANNED/SUSPENDED 사용자의 refresh를 차단하고 토큰을 즉시 폐기하는 로직은 제재 도메인과 잘 연결돼 있음.

#### [W-IDX] `refresh_token` 컬럼 인덱스 부재 → 토큰 갱신마다 users 풀스캔

- **파일**: `SpringDataJpaUsersRepository.java:73` (`findActiveByRefreshToken`)
- **문제**: refresh_token 컬럼에 인덱스가 없음(엔티티 `@Table` 선언에도, `resources/sql/migration/`에도 없음). 모든 클라이언트가 Access Token 만료(15분)마다 호출하는 고빈도 경로가 **users 테이블 풀스캔**. 사용자 수에 비례해 나빠지는 구조.
- **개선**: 위 [W-AUTH] 해시 저장과 묶어서 해결 — `refresh_token_hash CHAR(64)` 컬럼 + 인덱스 추가. (원문 컬럼에 직접 인덱스를 걸어도 되지만, 어차피 해시 전환이 필요하므로 한 번에.)
- **비고**: "고빈도 조회 경로인데 실행계획을 확인 안 한 컬럼" — EXPLAIN 벤치마크(§6-1) 대상으로 추가하기 좋은 소재.

#### [W-STAT] 결제 트랜잭션에 통계 upsert 동기 결합 → 핫 로우 직렬화 + 실패 전파

- **파일**: `PetCoinEscrowService.java:105` (`releaseToProvider` 내부 `statisticsService.recordPayment`), `StatisticsService.java:82~85` (`upsertPayment(LocalDate.now(), amount)`)
- **문제**: ① 모든 결제 완료가 **같은 날짜의 daily_statistics 단일 로우**를 UPDATE — 그 로우의 락을 에스크로 트랜잭션 커밋까지 보유하므로, 동시 결제 완료가 전부 이 로우에서 직렬화됨(핫 로우). ② 통계 upsert가 실패하면 **결제(에스크로 지급)까지 롤백** — 부가 기능이 핵심 기능을 물귀신처럼 끌고 내려가는 결합.
- **개선**: 프로젝트에 이미 있는 패턴 재사용 — `@TransactionalEventListener(AFTER_COMMIT)` + `REQUIRES_NEW`로 분리(제재 이벤트 리스너와 동일 구조). 통계는 최악의 경우 배치 backfill(`StatisticsService.backfill`)로 복구 가능하므로 결제와 생사를 같이할 이유가 없음.
- **비고**: "핫 로우 경합"은 면접에서 인덱스/락 다음으로 나오는 단골 주제 — 이 사례 자체가 스토리가 됨.

#### [W-EXH] `DataIntegrityViolationException` 전역 핸들러 부재 → 유니크 충돌이 500으로 노출

- **파일**: `GlobalExceptionHandler.java` (핸들러 목록에 없음 확인)
- **문제**: DB 유니크 제약을 10개 엔티티에 잘 깔아놨는데(§5-5), 제약 위반이 서비스에서 catch되지 않으면(1차 [D2]의 에스크로 등) generic `Exception` 핸들러로 떨어져 **500 + 내부 메시지 노출**. 유니크 제약은 "정상적인 동시 요청"에서도 터질 수 있는 예외라 4xx가 맞음.
- **개선**: `@ExceptionHandler(DataIntegrityViolationException.class)` → 409 Conflict 매핑 추가. 개별 서비스의 catch(모임 참가처럼 보상 로직이 필요한 곳)는 그대로 두고, 전역 핸들러는 마지막 안전망으로.

### 🟢 Info

- **스케줄러 중복 실행 방지 없음**: `@Scheduled` 잡 9개(통계/케어/인기글/장소점수/제재/모임 등)가 ShedLock 같은 분산 락 없이 동작. 현재 단일 인스턴스라 문제없지만, 스케일아웃 시 같은 배치가 인스턴스 수만큼 중복 실행됨 — 통계는 upsert라 멱등하지만 제재 해제/상태 변경류는 검토 필요. "스케일아웃 시 무엇이 깨지는가"를 알고 있다는 것 자체가 어필 포인트.

### ✅ 2차에서 확인된 잘된 점 (1차 §5에 추가)

8. **락 획득 순서가 일관적** — 에스크로 흐름 전체가 `escrow 락 → user 락 → (stats)` 순서를 지킴(`releaseToProvider`/`refundToRequester` 모두). 서로 다른 순서로 잡는 경로가 없어 락 순서 역전 데드락 여지가 없음. `chargeCoins`/`deductCoins`는 단일 user 락이라 안전.
9. **이벤트 리스너 트랜잭션 경계 정확** — 제재 이벤트가 `@TransactionalEventListener(AFTER_COMMIT)` + REQUIRES_NEW 규약으로 통일(이벤트 클래스 주석에 규약 명시까지).
10. **파일 업로드 경로 순회 방어** — `FileStorageService`: 절대경로 normalize → `relativize` 검증 → `../` 시 예외 + 날짜/UUID 파일명 생성. 신입 프로젝트에서 자주 뚫리는 부분인데 제대로 막혀있음.
11. **SSE emitter 수명주기 관리** — `onCompletion`/`onTimeout`/`onError` 전부에서 `ConcurrentHashMap` 제거 → 누수 없음.
12. **페이징+fetch join 함정 회피** — 컬렉션 fetch join과 Pageable을 섞지 않음. Meetup 목록은 to-one만 `@EntityGraph`로 가져와 in-memory 페이징(HHH90003004) 이슈 없음. 주석에 이유까지 적어놓음.

---

## 9. 3차 리뷰 (설정 레이어 · 장애 내성 · 테스트 현황)

애플리케이션 코드가 아닌 층위 — 설정, 스레드 풀, 외부 시스템 장애 시 동작, 테스트 자산 — 를 점검한 결과.

### 🟡 Warning

#### [W-CORS] `allowedOriginPatterns("*")` + `allowCredentials(true)`

- **파일**: `SecurityConfig.java:110~118`
- **문제**: 모든 origin에 자격증명 포함 요청을 허용. JWT를 Authorization 헤더로 쓰는 구조라 쿠키 기반 CSRF보다는 위험이 낮지만, 임의 origin의 악성 페이지가 API·WebSocket에 자유롭게 접근할 수 있는 상태. `// TODO(운영)` 주석으로 인지는 하고 있음 — **배포 전 필수 수정 목록에 올려야 함**.
- **개선**: 운영 도메인 화이트리스트로 교체(주석의 예시 코드 그대로). 로컬/운영을 프로파일별 프로퍼티로 분리하면 실수 여지도 제거됨.

#### [W-NOTI] 알림 부가 채널 장애가 도메인 트랜잭션을 롤백시킴

- **파일**: `NotificationService.java` (클래스 전체에 try-catch 0건)
- **문제**: `createNotification` 안의 Redis 저장·SSE 전송·FCM 발송 중 하나라도 예외를 던지면(예: Redis 다운) 알림 생성이 실패하고, **알림을 발생시킨 원래 작업(댓글 작성, 케어 매칭 등)의 트랜잭션까지 롤백**된다. 알림은 부가 기능인데 핵심 기능의 가용성을 좌우하는 역결합.
- **개선**: 1차 [B2]의 AFTER_COMMIT 이벤트 분리로 함께 해결됨 — 커밋 후 리스너에서 채널별 예외를 각각 삼키고 로그만 남기면, Redis가 죽어도 DB 알림은 남고 댓글은 정상 작성된다. **[B2]를 고칠 때 이 관점(장애 격리)까지 한 커밋에 담으면 스토리가 두 배가 됨.**

### 🟢 Info

- **`@Async` 기본 executor 사용**: `PetIntentAsyncConfig`는 전용 풀(core 2, queue 500, 거부 정책까지 구현)을 잘 만들어놨는데, `EmailService`의 `@Async`는 executor 미지정이라 Spring Boot 기본 `applicationTaskExecutor`(무한 큐) 사용. 메일 서버 응답 불능 시 태스크가 큐에 무한 적재됨. petIntent처럼 bounded queue + 거부 정책을 가진 전용 executor 지정 권장.
- **Pageable 최대 size 미설정**: `spring.data.web.pageable.max-page-size` 기본값 2000에 의존. `?size=2000` 요청이 그대로 통과하므로, 공개 목록 API는 명시적 상한(예: 100)을 거는 게 안전.

### ✅ 3차에서 확인된 잘된 점 (§5, §8에 이어서)

13. **OSIV off** — `spring.jpa.open-in-view=false` (dev/prod 모두). 요청 내내 커넥션을 잡는 기본값을 끈 것으로, 커넥션 풀 관점에서 가장 효과 큰 설정. 신입 프로젝트에서 보기 드묾.
14. **HikariCP 명시 설정 + 누수 감지** — pool size 20, `leak-detection-threshold=60000`까지. 커넥션 누수를 감시하겠다는 운영 감각.
15. **동시성 테스트 자산 7개** — `ExecutorService`/`CountDownLatch` 기반: 펫코인 레이스, 모임 참가(격리수준 변경·무트랜잭션 대조군 시나리오 포함), 거래 확정, 인증/제재 동시성. 특히 격리수준을 바꿔가며 검증하는 테스트는 명확한 차별화 요소 — §6-2에서 이걸 표면화하는 게 과제.
16. **기본기**: BCrypt 패스워드 인코딩, prod `show-sql=false`, `ddl-auto=none` 프로파일 분리, 전용 async executor의 거부 정책(`DiscardWithWarnPolicy`) 직접 구현.

---

## 10. 참고

- 리뷰 기준: `.claude/skills/review.md` 체크리스트 A~E (1차) + 심화 관점 리뷰 (2차) + 설정/장애 내성/테스트 (3차)
- 관련 문서: `docs/performance/` (수치화 작업 시 결과 기록 위치), `docs/interview/` (면접 스토리 정리 시 참고)
