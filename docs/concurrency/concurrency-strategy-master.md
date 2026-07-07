# 동시성 제어 통합 전략 맵 (마스터)

> 작성: 2026-07-07 · 목적: Petory 백엔드의 동시성 작업을 **한 곳에 조립**한 소스오브트루스.
> 흩어진 8개 테스트 + 5개 문서를 통합해 (1) 기억 저장소, (2) 면접 준비, (3) 핵심성과·포트폴리오 파생의 기준으로 쓴다.
>
> 관련: [transaction-concurrency-cases.md](./transaction-concurrency-cases.md) · [../analysis/entity-schema/04-transaction-concurrency.md](../analysis/entity-schema/04-transaction-concurrency.md)

---

## 0. 한 줄 요약

동시성 문제를 **8개 시나리오에서 재현**하고, 시나리오 특성에 따라 **4가지 전략을 구분 적용**했다.
핵심은 개별 전략이 아니라 **"왜 시나리오마다 다른 전략을 골랐는가"** 라는 판단 기준이다.

---

## 1. 결정 프레임워크 (제일 중요)

전략 선택의 기준은 하나의 질문이다:

> ### "현재 값을 읽어서 검증/분기해야 하는가?"

| 상황                                                          | 고르는 전략                           | 이유                                                               |
| ------------------------------------------------------------- | ------------------------------------- | ------------------------------------------------------------------ |
| 현재 값을 읽고 **판단**해야 함 (잔액 부족, 두 주체 상태 조합) | **비관적 락** `SELECT … FOR UPDATE`   | read-modify-write를 직렬화해야 최신 커밋값을 보고 검증 가능        |
| **단순 조건부 증가/감소** (인원 < 최대, 카운터+1)             | **원자적 조건부 UPDATE** `WHERE 조건` | 체크+변경을 DB 한 문장으로 원자화. 락 대기 없어 고동시성·분산 유리 |
| **유일성 보증** (닉네임, 소셜계정, 중복신고)                  | **DB Unique 제약** (+예외처리)        | 앱 레벨 사전 체크는 TOCTOU라 못 막음. DB가 최종 보증자             |
| **부분 실패 시 전체 롤백** (게시글+댓글 cascade)              | **트랜잭션 경계** `@Transactional`    | 원자적 커밋/롤백으로 데이터 일관성                                 |

> 신입 대부분은 "락 걸었어요"에서 끝난다. 이 프레임워크는 **4전략을 상황 기준으로 갈라 쓴 근거**를 보여준다. 이게 이 문서의 핵심 무기다.

---

## 2. 전략 지도 (전체 8개)

| #   | 시나리오                             | 문제 유형                            | 선택 전략                                                     | 상태                                 | 근거 (테스트 / 문서)                                                                                         |
| --- | ------------------------------------ | ------------------------------------ | ------------------------------------------------------------- | ------------------------------------ | ------------------------------------------------------------------------------------------------------------ |
| 1   | **PetCoin 잔액** 차감/충전/지급/환불 | Lost Update                          | 비관적 락 `findByIdForUpdate`                                 | ✅ 해결                              | `PetCoinServiceRaceConditionTest` / [payment 문서](../refactoring/payment/petcoin-service-race-condition.md) |
| 2   | **Meetup 참가 인원**                 | Lost Update + 최대 인원 초과         | 원자적 조건부 UPDATE + CHECK 제약                             | ✅ 해결 (+벤치마크)                  | `MeetupServiceRaceConditionTest` / [meetup 문서](../troubleshooting/meetup/race-condition-participants.md)   |
| 3   | **Care 거래 확정**                   | Stuck State (격리수준으로 로직 skip) | 비관적 락 (Conversation)                                      | ✅ 해결                              | `CareDealConcurrencyTest` / [care 문서](../troubleshooting/care/care-deal-confirmation-race-condition.md)    |
| 4   | **경고 횟수 증가**                   | Lost Update                          | 원자적 UPDATE `warning_count+1`                               | ✅ 해결                              | `UserSanctionServiceConcurrencyTest`                                                                         |
| 5   | **닉네임/가입 중복**                 | 중복 생성                            | DB Unique 제약 (+예외처리)                                    | ✅ 무결성 보장 / 예외처리 개선 여지  | `UsersServiceConcurrencyTest`                                                                                |
| 6   | **소셜 로그인 중복 계정**            | 중복 생성                            | DB Unique 제약 `uk_socialuser_provider_providerid` (backstop) | 🟡 부분 (무결성 O, 패자 예외 미처리) | `OAuth2ServiceConcurrencyTest`                                                                               |
| 7   | **Refresh Token 동시 갱신**          | 토큰 회전 경합                       | (미확립)                                                      | 🔴 탐색/미해결                       | `AuthServiceConcurrencyTest`                                                                                 |
| 8   | **게시글 삭제 중 댓글 추가**         | 삭제 누락 (LAZY 로딩 시점 문제)      | (제안: repository 직접 조회 / bulk UPDATE)                    | 🔴 문제 식별됨/미해결                | `MissingPetBoardConcurrencyTest`                                                                             |

범례: ✅ 해결 · 🟡 부분 해결 · 🔴 미해결(정직하게 분리)

---

## 3. 해결된 케이스 상세 (1~6)

각 케이스: **문제 상황 → 원인 → 선택 전략 → 왜 이 전략 → 검증**

### 3.1 PetCoin 잔액 — 비관적 락

- **문제 상황**: 초기 잔액 100, 동시 충전 5건×10 = 예상 150. 5개 스레드가 모두 `findById`로 `balanceBefore=100`을 읽고 각자 110으로 덮어씀 → **실제 110** (40 분실). 테스트 로그상 Deadlock으로 1~2건만 커밋.
- **원인**: `chargeCoins/payoutCoins/refundCoins`가 락 없는 `findById` 사용 → read-modify-write 비원자.
- **전략**: `findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)` → `SELECT … FOR UPDATE`). 행 락으로 동일 사용자 요청 직렬화.
- **왜 이 전략**: 차감(`deductCoins`)은 **잔액 부족 검증**이 필수 → 현재값을 읽고 분기해야 함 → 원자적 UPDATE로는 "부족" 분기를 못 태움 → 비관적 락. (충전/지급/환불도 read-modify-write라 같은 락으로 통일)
- **검증**: `testChargeCoins_RaceCondition_Fixed` — 적용 후 최종 잔액 == 예상(150) 일치.
- ⚠️ **약점(보강 대상)**: `_ProblemOccurs` 테스트는 고쳐진 코드에선 결정론적으로 안 터짐(경고 로그만). Meetup식 결정론 재현(트랜잭션 우회 직접 repo) 추가 시 완성도↑.

### 3.2 Meetup 참가 인원 — 원자적 조건부 UPDATE ⭐ (가장 완성도 높음)

> **이건 성능 문제가 아니라 데이터 정합성(correctness) 문제다.** "최대 3명인데 4명 참가" = 비즈니스 제약이 깨지는 **잘못된 결과**를 막는 작업. 속도가 목적이 아니다.

- **문제 상황**: 최대 3명, 모임장 1명 참가 중. 동시에 3명이 참가 → 셋 다 `current(1) < max(3)` 통과 → **4명** 참가(초과). → **최대 인원 제약 위반(데이터 무결성 붕괴).**
- **원인**: `current >= max` 체크와 `setCurrentParticipants(current+1)`가 분리 → 사이에 다른 트랜잭션 개입.
- **전략**: 조건부 원자 UPDATE + DB CHECK 제약(이중 안전망).
  ```sql
  UPDATE meetup SET current_participants = current_participants + 1
  WHERE idx = :idx AND current_participants < max_participants
  ```
  `updated == 0`이면 "인원 가득 참" 예외. → 체크와 증가를 **DB 한 문장으로 원자화**해 초과를 구조적으로 불가능하게 만듦.
- **왜 이 전략** (여러 정답 중 선택): 초과 방지는 비관적 락·낙관적 락·원자적 UPDATE 다 가능. 그중 원자적 UPDATE는 **락/데드락 관리 불필요 + 코드 단순 + 락을 참가자 INSERT까지 잡지 않음**이라 골랐다. (문서에 **5전략 비교**: 세마포어=분산 미지원, 비관적 락=대기/데드락, 낙관적 락=재시도, Redis 분산락=인프라 의존, **원자적 UPDATE=균형 최선**) — **속도 때문이 아니다** (§부록 실측상 저경합에선 비관적 락이 오히려 빨랐음).
- **검증**: 재현(READ COMMITTED / 3명 / 5명 / **트랜잭션 우회 직접 repo = 결정론적**) → 해결 후 인원 == 최대 이하 **정합성 유지 확인**.

### 3.3 Care 거래 확정 — 비관적 락 (상위 엔티티) + Stuck State 통찰

- **문제 상황**: 요청자·제공자 둘 다 "거래 확정"을 눌러야 `CareRequest`가 IN_PROGRESS로 전환. 거의 동시에 누르면 **아무도 전환 못 시키고 OPEN에 멈춤**(stuck state) — 사용자에겐 "완료" 표시되는데 진행 안 됨.
- **원인 (핵심 통찰)**: "중복 실행"이 아니라 **격리수준(REPEATABLE READ)** 때문에 **skip**. Tx A는 B의 미커밋 확정을 못 봐서 `allConfirmed=false`, Tx B도 A를 못 봐서 `false` → 둘 다 후속 로직 안 탐. 커밋 후엔 둘 다 true지만 트리거는 이미 지나감. → check-then-act에서 "상대 상태" 읽는 시점의 일관성 미보장.
- **전략**: 상위 엔티티 `Conversation`에 `PESSIMISTIC_WRITE` 락 → 한 명 처리 끝날 때까지 다른 한 명 대기 → 대기 해제 후 **커밋된 최신값**을 읽음. + 자식 엔티티 생성 시 `getReferenceById`(프록시) + `saveAndFlush`로 `TransientObjectException` 방지.
- **왜 이 전략**: 두 참여자 상태를 **함께 판단**해야 하는 check-then-act → 원자적 UPDATE로 표현 불가 → 상위 엔티티 락으로 직렬화.
- **검증**: `CareDealConcurrencyTest` — 동시 확정 시 둘 다 true & `CareRequest` OPEN→IN_PROGRESS 정상 전환. (초기 Deadlock/TransientObject 이슈는 saveAndFlush로 해결)

### 3.4 경고 횟수 증가 — 원자적 UPDATE

- **문제 상황**: 여러 관리자가 동시에 같은 사용자에게 경고 → 같은 `warningCount` 읽고 +1 덮어씀(Lost Update).
- **전략**: `UPDATE users SET warning_count = warning_count + 1 WHERE idx = :id` 원자적 증가. 이후 재조회해 임계(3회) 도달 시 자동 이용제한.
- **왜 이 전략**: 검증 없이 **증가만** 하는 카운터 → 원자적 UPDATE로 충분(락 불필요).
- **검증**: `UserSanctionServiceConcurrencyTest` — 동시 증가 후 최종 count == 실제 성공 건수. 자동 제재 중복 적용 방지도 검증.

### 3.5 닉네임/가입 중복 — DB Unique 제약

- **문제 상황**: 동시에 같은 닉네임으로 가입 시도.
- **전략**: `users` 테이블 Unique 제약(nickname/username/email) → DB가 유일성 최종 보증. 위반 시 `DataIntegrityViolationException`.
- **왜 이 전략**: "가입 전 조회로 중복 체크"는 TOCTOU(조회~저장 사이 개입)라 못 막음 → DB 제약이 유일하게 확실.
- **검증**: `UsersServiceConcurrencyTest` — 동시 가입 시 같은 닉네임 사용자 ≤ 1명.
- ⚠️ **개선 여지**: 무결성은 보장되나, 패자 스레드의 `DataIntegrityViolationException`을 사용자 친화 메시지로 우아하게 처리하는 부분은 확인/보강 대상. (탈퇴 후 닉네임/username/email 재사용 제약도 같은 테스트에서 다룸)

### 3.6 소셜 로그인 중복 계정 — DB Unique 제약 (backstop) 🟡

- **문제 상황**: 같은 소셜 계정으로 동시 최초 로그인 N건 → find-or-create의 "없으면 생성" 분기를 여럿이 통과 → 중복 계정 위험.
- **전략**: `OAuth2Service.processOAuth2Login`은 `findByProviderAndProviderId` → 있으면 사용, 없으면 `createOrLinkUser`. 락은 없고, **`socialuser` Unique 제약 `uk_socialuser_provider_providerid` + `users.email` Unique**가 실제 backstop → 패자는 제약 위반으로 실패, 계정은 1개만.
- **왜 부분 해결(🟡)**: 무결성은 DB 제약으로 보장(테스트: 계정/소셜계정 정확히 1개). 하지만 앱 로직에 **패자 예외를 잡아 기존 계정으로 재조회·연결하는 우아한 처리가 없음** → 동시 최초 로그인 패자에게 에러 전파 가능. 개선하려면 제약 위반 catch 후 재조회(read-after-conflict) 패턴 필요.
- **검증**: `OAuth2ServiceConcurrencyTest` — 동시 로그인 후 같은 이메일 사용자 1명, 소셜계정 1개.

---

## 4. 부록 — 전략 선택 근거 (성능이 목적이 아님)

> ⚠️ **이 벤치마크는 Meetup 작업의 "이유"가 아니다.** Meetup 동시성은 §3.2대로 **정합성(인원 초과 방지)** 문제다. 아래 비교는 "초과를 막는 여러 정답 중
> 왜 원자적 UPDATE를 골랐나"의 부차적 근거일 뿐, 속도를 성과로 내세우려는 게 아니다.

`MeetupServiceRaceConditionTest.testPerformanceComparison` — **비관적 락 vs 원자적 UPDATE**(둘 다 이미 정합성은 보장) 속도 참고 비교.

- 조건: 동시 참가 10명, 각 방식 5회 반복

**실측 결과 (2026-07-07, 로컬 MySQL 8):**

| 방식          | 평균       | 최소 | 최대 |
| ------------- | ---------- | ---- | ---- |
| 비관적 락     | **2.40ms** | 1ms  | 7ms  |
| 원자적 UPDATE | **8.40ms** | 6ms  | 13ms |

> ⚠️ **실측이 통념을 뒤집음**: 이 규모(저경합·소량 데이터)에선 **비관적 락이 오히려 더 빨랐다** (2.4ms vs 8.4ms). "원자적 UPDATE가 락 대기 없어 더 빠르다"는 통념은 **여기선 성립 안 함.** 값이 1~13ms로 작고 노이즈가 커서 테스트도 상대 속도를 assert하지 않는다.

**그래서 왜 원자적 UPDATE를 골랐나 (정직한 근거):**

- 단일 연산 latency가 빨라서가 **아니다** (실측상 오히려 느림).
- **락/데드락 관리 불필요 + 코드 단순 + 락을 참가자 INSERT까지 잡고 있지 않음.** 비관적 락은 조회~커밋 구간 내내 행 락 보유.
- **고경합에서의 확장성**은 이론적 근거(락 대기 누적 회피)이며 이 벤치마크로는 입증 안 됨 → 단정하지 않는다.

> 면접 포인트: "두 전략을 같은 조건에서 **실측 비교**했더니 저경합에선 비관적 락이 오히려 빨랐습니다. 그래서 원자적 UPDATE를 고른 이유는 latency가 아니라 **락/데드락 관리 부담 제거와 코드 단순성**이라는 걸 데이터로 확인했고, ms는 노이즈가 커서 상대 속도를 단정하지 않았습니다." → **측정으로 자기 통념을 반증한 사례**라 훨씬 강함.

---

## 5. 미해결/탐색 (정직하게 분리)

포트폴리오·핵심성과 **본문에는 넣지 않는다.** 면접에서 물으면 "여기까지 파악했고 이렇게 해결할 계획"으로 성숙함을 보이는 용도.

### 5.1 Refresh Token 동시 갱신 🔴

- `AuthServiceConcurrencyTest`는 `@Transactional`이라 스레드가 서로의 커밋을 못 보고, 두 번째 테스트는 assert가 없음 → **재현·해결이 확립되지 않은 탐색 테스트.**
- 본질: RT를 DB 컬럼(`refresh_token`)에 저장하는 단일 값 회전 방식이라 동시 갱신 시 어느 토큰이 유효한지 경합. 해결하려면 RT 회전 정책(사용 즉시 무효화 + 재사용 감지) 또는 저장 구조 재설계가 선행. → **설계 결정 필요.**

### 5.2 게시글 삭제 중 댓글 추가 🔴

- `MissingPetBoardConcurrencyTest`는 문제를 **재현·설명**하는 테스트. `deleteBoard()`가 `board.getComments()`(영속성 컨텍스트/LAZY 로딩 시점 의존)로 순회 삭제 → 트랜잭션 중간에 추가된 댓글은 누락.
- 제안된 해결(미적용): repository로 댓글 직접 조회 후 삭제, 또는 `@Query`로 `UPDATE … SET is_deleted=true WHERE board=:board` bulk 처리.

---

## 6. 공통 원칙 · 격리수준

- **격리수준**: MySQL InnoDB 기본 `REPEATABLE READ`. Care stuck state는 이 격리수준의 "다른 트랜잭션 미커밋 변경 안 보임" 특성에서 비롯 → 락으로 해결.
- **핵심/파생 도메인 분리**: 모임 생성(핵심) 성공 후 채팅방 생성(파생)은 `@TransactionalEventListener` + `REQUIRES_NEW` 비동기 → 파생 실패가 핵심을 롤백하지 않음.
- **DB 제약을 최종 안전망으로**: 앱 레벨 방어(락/UPDATE) + DB 제약(Unique/CHECK) 이중화.
- **로깅 전략**: INFO=정상, WARN=예상 가능 실패(인원 초과 등), ERROR=정합성 문제.

---

## 7. 면접 1분 스크립트

> "동시성을 8개 시나리오에서 재현했고, **'현재 값 검증이 필요한가'** 기준으로 전략을 갈랐습니다.
> 잔액처럼 값을 읽고 검증해야 하면 **비관적 락**(PetCoin), 인원처럼 단순 조건부 증가면 **원자적 조건부 UPDATE**(Meetup), 유일성이면 **DB Unique 제약**(닉네임·소셜), 부분 실패 롤백이면 **트랜잭션 경계**를 썼습니다.
> Meetup은 비관적 락과 원자적 UPDATE를 **같은 조건에서 실측 비교**까지 했고, Care 거래 확정은 중복 실행이 아니라 **격리수준 때문에 로직이 skip되는 stuck state**라는 걸 파악해 상위 엔티티 락으로 풀었습니다.
> 리프레시 토큰 회전과 삭제-댓글 경합은 문제를 재현·식별한 단계까지 갔고, 해결은 설계 결정이 필요해 분리해 뒀습니다."

---

## 8. 근거 파일 인덱스

| 케이스           | 테스트                                            | 문서                                                            |
| ---------------- | ------------------------------------------------- | --------------------------------------------------------------- |
| PetCoin          | `payment/service/PetCoinServiceRaceConditionTest` | `refactoring/payment/petcoin-service-race-condition.md`         |
| Meetup           | `meetup/service/MeetupServiceRaceConditionTest`   | `troubleshooting/meetup/race-condition-participants.md`         |
| Care             | `care/service/CareDealConcurrencyTest`            | `troubleshooting/care/care-deal-confirmation-race-condition.md` |
| 경고 횟수        | `user/service/UserSanctionServiceConcurrencyTest` | `concurrency/transaction-concurrency-cases.md` §경고 횟수       |
| 닉네임           | `user/service/UsersServiceConcurrencyTest`        | `analysis/entity-schema/04-transaction-concurrency.md`          |
| 소셜 로그인      | `user/service/OAuth2ServiceConcurrencyTest`       | (엔티티 `SocialUser` uk 제약)                                   |
| Refresh Token 🔴 | `user/service/AuthServiceConcurrencyTest`         | (미해결)                                                        |
| 삭제-댓글 🔴     | `board/service/MissingPetBoardConcurrencyTest`    | (미해결, 해결책 제안 단계)                                      |
