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
| 2   | **Meetup 참가 인원**                 | **락 승격 데드락**(정상 경로) + 초과·Lost Update(트랜잭션 우회 시) | 비관적 락 `findByIdWithLock` + 조건부 원자 UPDATE + 복합 PK + CHECK **4겹**(§3.2) | ✅ 해결 (`58467b4e`에서 현재 형태 완성) | `MeetupServiceRaceConditionTest` / [meetup 문서](../troubleshooting/meetup/race-condition-participants.md)   |
| 3   | **Care 거래 확정**                   | Stuck State (격리수준으로 로직 skip) | 비관적 락 (Conversation)                                      | ✅ 해결                              | `CareDealConcurrencyTest` / [care 문서](../troubleshooting/care/care-deal-confirmation-race-condition.md)    |
| 4   | **경고 횟수 증가**                   | Lost Update + **락 승격 데드락** + bulk update 후 1차 캐시 stale | 원자적 UPDATE `warning_count+1` + **UPDATE를 FK INSERT보다 먼저**(X락 선점) + `entityManager.refresh(user)` | ✅ 해결 (결함 2건, `1f989b9f`·`294ea235`) | `UserSanctionServiceConcurrencyTest` · `UserSanctionAutoSuspensionTest`                                       |
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
- **검증**: 도입 커밋 `7611bb17`(2026-01-28) diff = `findById` → `findByIdForUpdate`. `testChargeCoins_RaceCondition_Fixed` — 적용 후 최종 잔액 == 예상(150) 일치. **2026-07-30 재실행 재확인**(성공 5건, 최종 150).
- ⚠️ **약점(보강 대상, 2026-07-30 재확인)**: `❌ 문제 상황: … Lost Update 재현` 이름의 테스트 **3개가 전부 150==150으로 통과**한다 — 락이 이미 있어 아무것도 재현하지 못한다. 특히 `chargeCoins` 테스트는 로그에 *"현재 chargeCoins는 findById 사용 → 락 없음 → Lost Update 가능"*을 출력하는데 **코드는 `7611bb17` 이후 `findByIdForUpdate`** — 테스트 이름·로그가 낡아 **잘못된 사실을 출력**하고 있다. 실제 재현 근거는 worktree before 커밋 측정(100→110)이다. Meetup식 결정론 재현(트랜잭션 우회 직접 repo) 이식 + 낡은 로그 정리가 필요하다.

### 3.2 Meetup 참가 인원 — 비관적 락 + 조건부 UPDATE + PK 계층 방어 ⭐

> **이건 성능 문제가 아니라 데이터 정합성(correctness) 문제다.** 비즈니스 제약(정원)이 깨지는 **잘못된 결과**를 막는 작업. 속도가 목적이 아니다.

- **예상한 문제**: 최대 3명, 모임장 1명 참가 중. 동시에 3명이 참가 → 셋 다 `current(1) < max(3)` 통과 → **4명** 참가(초과). 원인은 `current >= max` 체크와 `setCurrentParticipants(current+1)` 분리(TOCTOU).
- ⚠️ **정상 경로에서 관찰된 증상은 달랐다** (`race-condition-reverify-2026-07-12.md`, 커밋 `a04abaae`): 락이 전혀 없던 진짜 최초 버그 커밋 `a549eb33`(`joinMeetup`이 `findById`만 씀)을 worktree로 되돌려 3회 재현 → **인원 초과는 발생하지 않고**, 남은 자리 2개인데 1명만 성공하고 **2명이 `CannotAcquireLockException: Deadlock`으로 부당하게 실패**했다.
- ⚠️ **원인은 §3.4 경고와 문자 그대로 동일하다** (해당 커밋 코드로 확인): 그 시점 `joinMeetup`은 `meetupParticipantsRepository.save(participant)`를 **먼저** 실행해 FK로 meetup 행에 **S락**을 잡고, **그 뒤에** `setCurrentParticipants(+1)` → `save(meetup)`으로 같은 행에 **X락**을 요구한다. 즉 `INSERT(S) → UPDATE(X)` 락 승격 순환 대기 — 경고와 같은 패턴이다. ("암묵적 X락 때문"이라는 이전 설명은 메커니즘의 절반만 짚은 것이었다.)
- ✅ **정원 초과는 트랜잭션 경계를 우회하면 실제로 재현된다**(`testRaceConditionWithoutTransaction`). **실측 2026-07-30**: 3명 전원 성공, **DB 참가자 4명 vs `currentParticipants` 2**, 정원 3 → 초과와 카운터 Lost Update 동시 발생. 따라서 "이론적 위험일 뿐"이라는 서술도 **과소 표현**이다 — 조건이 갖춰지면 결정론적으로 재현되고, 정상 경로에서는 그 앞의 데드락 방어선에 막혔던 것이다.
- **이력(락을 넣었다 뺐다 다시 넣었다)** — 문서가 낡은 원인:

  | 커밋 | 날짜 | 상태 | 락 순서 |
  |---|---|---|---|
  | `a549eb33` | 2025-12-13 | 락 없음 + read-modify-write | INSERT(S) → UPDATE(X) = **데드락** |
  | `a5943b18` | 2025-12-19 | `findByIdWithLock` 도입 | 진입점 X락 선점 → 소멸 |
  | `bf32d155` | 2025-12-20 | **락 제거** + 조건부 원자 UPDATE | UPDATE(X) → INSERT (순서 역전) |
  | `58467b4e` | 2026-05-09 | **락 재도입** + 조건부 UPDATE 유지 + `refresh()` | 락 → UPDATE → INSERT **(현재)** |

  이 문서의 "Meetup은 락이 아니라 원자적 UPDATE" 서술은 `bf32d155` 시점엔 **맞았고**, `58467b4e`(락 재도입) 이후 낡았다. 그 5개월 창을 기준으로 쓰인 문구가 이후 여러 문서로 전파됐다. 또한 `bf32d155`의 원자적 UPDATE 전환은 **부수적으로 락 순서까지 뒤집어**(UPDATE를 INSERT 앞으로) 데드락을 해소했는데, 당시엔 그 효과를 인지하지 못했다 — 같은 원리를 §3.4 경고에서 2026-07-22에야 명시적으로 발견했다.
- **전략 (현재 코드 기준 계층 방어)** — `MeetupService.joinMeetup`:
  1. `findByIdWithLock`(`PESSIMISTIC_WRITE`)으로 **모임 행을 먼저 잠가** 참가 요청을 직렬화(TOCTOU 원천 차단). 이 락은 **참가자 INSERT를 지나 커밋까지 유지**된다.
  2. 조건부 원자 UPDATE로 DB가 정원을 재검사:
     ```sql
     UPDATE meetup SET current_participants = current_participants + 1
     WHERE idx = :idx AND current_participants < max_participants AND status = 'RECRUITING'
     ```
     `updated == 0`이면 "인원 가득 참"/"모집 마감" 예외. bulk update라 이후 `entityManager.refresh(meetup)`로 영속성 컨텍스트 동기화.
  3. `meetupparticipants (meetup_idx, user_idx)` **복합 PK**가 중복 참가 INSERT를 최종 차단(충돌 시 `decrementParticipantsIfPositive`로 카운터 되돌림).
  4. `meetup.chk_participants` **CHECK 제약**(`current <= max`)이 스키마 레벨 최후 방어.
- **왜 계층 방어**: 초과 방지는 비관적 락·낙관적 락·원자적 UPDATE 다 가능하고 락만으로도 대부분 충분하다. 그래도 겹친 이유는 **정원 초과가 치명적인 도메인**이라, 조건부 UPDATE로 정원 규칙을 DB에 명시적으로 남기고 PK·CHECK를 예상 못 한 경로의 최후 방어로 두려는 것. **속도 때문이 아니다**(§부록 참고, 그리고 그 벤치마크는 근거로 쓰지 않는다).
- **검증**: 재현(READ COMMITTED / 3명 / 5명 / **트랜잭션 우회 직접 repo = 결정론적**) → 해결 후 인원 == 최대 이하 **정합성 유지 확인**. 해결 후 테스트 이름도 `testRaceConditionFixedWithPessimisticLock`(서비스 메서드 호출)이다.

### 3.3 Care 거래 확정 — 비관적 락 (상위 엔티티) + Stuck State 통찰

- **문제 상황**: 요청자·제공자 둘 다 "거래 확정"을 눌러야 `CareRequest`가 IN_PROGRESS로 전환. 거의 동시에 누르면 **아무도 전환 못 시키고 OPEN에 멈춤**(stuck state) — 사용자에겐 "완료" 표시되는데 진행 안 됨.
- **원인 (핵심 통찰)**: "중복 실행"이 아니라 **격리수준(REPEATABLE READ)** 때문에 **skip**. Tx A는 B의 미커밋 확정을 못 봐서 `allConfirmed=false`, Tx B도 A를 못 봐서 `false` → 둘 다 후속 로직 안 탐. 커밋 후엔 둘 다 true지만 트리거는 이미 지나감. → check-then-act에서 "상대 상태" 읽는 시점의 일관성 미보장.
- **전략**: 상위 엔티티 `Conversation`에 `PESSIMISTIC_WRITE` 락 → 한 명 처리 끝날 때까지 다른 한 명 대기 → 대기 해제 후 **커밋된 최신값**을 읽음. + 자식 엔티티 생성 시 `getReferenceById`(프록시) + `saveAndFlush`로 `TransientObjectException` 방지.
- **왜 이 전략**: 두 참여자 상태를 **함께 판단**해야 하는 check-then-act → 원자적 UPDATE로 표현 불가 → 상위 엔티티 락으로 직렬화.
- **검증**: `CareDealConcurrencyTest` — 동시 확정 시 둘 다 true & `CareRequest` OPEN→IN_PROGRESS 정상 전환. (초기 Deadlock/TransientObject 이슈는 saveAndFlush로 해결)

### 3.4 경고 횟수 증가 — 원자적 UPDATE + 락 획득 순서

- **문제 상황**: ① 여러 관리자가 동시에 같은 사용자에게 경고 → 같은 `warningCount` 읽고 +1 덮어씀(Lost Update). ② 재검증서 **별개의 락 승격 데드락**이 드러남 — `addWarning`이 한 트랜잭션에서 경고 기록 INSERT(FK로 users 행 **S락**) → warningCount UPDATE(같은 행 **X락 승격**) 순서라, 동시 요청이 모두 S락을 쥔 채 X락 승격을 노려 순환 대기 → **5개 중 4개 Deadlock 롤백**으로 경고 유실.
- **전략**: `UPDATE users SET warning_count = warning_count + 1 WHERE idx = :id` 원자적 증가로 Lost Update 차단. 데드락은 **UPDATE(X락)를 FK INSERT(S락)보다 먼저 실행**해 X락을 선점 → 순차 처리로 소멸(같은 트랜잭션이라 순서만 바꿔도 정합성 동일). 이후 재조회해 임계(3회) 도달 시 자동 이용제한.
- **왜 이 전략**: 카운터 증가 자체는 검증 불필요 → 원자적 UPDATE로 충분. 단 **락 획득 순서**가 데드락을 좌우하므로 X락 선점이 핵심(원자적 UPDATE만으론 데드락이 안 풀렸음).
- ✅ **`1f989b9f` diff로 확인된 사실**: 이 커밋 **이전에도 `incrementWarningCount`(원자적 UPDATE)는 이미 있었다** — 단지 `sanctionRepository.save()` **뒤**에 있었다. 커밋이 한 일은 그 한 줄을 INSERT 앞으로 옮긴 것뿐이다. 즉 "원자적 UPDATE로도 데드락이 남았다"는 서술이 diff로 입증된다(값을 바꾼 게 아니라 **순서만** 바꿔 해결). 테스트 diff도 항진명제 assert 1개에서 `successCount == adminCount` + `warningCount == adminCount` 2개 추가로 강화된 것이 확인된다.
- 🔗 **§3.2 Meetup과 동일 메커니즘**: `INSERT(FK S락) → UPDATE(X락)` 순서가 양쪽의 공통 원인이다. 경고는 순서를 직접 뒤집어, 모임은 진입점에서 X락을 선점(`findByIdWithLock`)해 각각 해결했다.
- 🟠 **같은 메서드의 두 번째 결함 — 자동 이용제한이 발동하지 않았다 (2026-07-30 발견 → `294ea235`로 해결)**: `addWarning`의 임계 검사가 **증가 전 값**을 읽고 있었다. `incrementWarningCount`는 `@Modifying` JPQL bulk update인데 `clearAutomatically`가 없어 영속성 컨텍스트를 비우지 않고, 앞서 `findById`로 적재된 `Users`가 그대로 남아 있어서 이후 `findById` 재조회가 **1차 캐시에 히트**했다(해당 구간에 재조회 SQL이 나가지 않는 것으로 확인). 그래서 `warningCount >= 3` 검사가 낡은 값과 비교됐다.
  - **실측 (수정 전, `294ea235^`)**: 경고 2회 사용자에게 동시 3회 부여 → UPDATE 3건 모두 실행됐으나 **최종 상태 `ACTIVE`, 이용제한 0회**, `log.info("경고 N회 도달…")` 미출력.
  - **테스트가 못 잡은 이유**: `testConcurrentWarningAutoSuspension`의 단언이 `if (status == SUSPENDED) { assertNotNull(...) }` **조건부**라 제재가 아예 안 걸리면 통과했다. §3.1 PetCoin false-green, 경고 항진명제와 같은 계열.
  - **해결 (`294ea235`, 2026-07-30)**: Meetup이 동일 함정을 이미 `entityManager.refresh()`로 막고 있었으므로(§3.2) 같은 방식으로 맞췄다 — `sanctionRepository.save(warning)` 직후 `entityManager.refresh(user)`. `@Modifying(clearAutomatically = true)`는 컨텍스트를 통째로 비워 앞서 로드한 `user`·`admin`이 detach 되므로 채택하지 않았다.
  - **회귀 방지**: 조건부가 아니라 **무조건 단언**하는 `UserSanctionAutoSuspensionTest` 신설(3회 도달 시 `SUSPENDED` + 경계값 2회는 `ACTIVE` 유지, 2건). TDD RED(`expected SUSPENDED but was ACTIVE`) → GREEN 확인. 2026-07-31 재실행에서도 2건 통과.
  - ⚠️ **이 항목은 "미해결"로 적혀 있었다.** 그 서술을 쓴 **같은 날 저녁에 수정이 들어갔는데** 문서가 안 따라왔다. 결함 상태도 수치와 마찬가지로 **커밋 시점을 붙여야 한다.**
- **실측 재확인(2026-07-30) — before/after 양방향 실행**:
  - **Before**: `git worktree`로 fix 직전 커밋 `e937b823`을 checkout 후 **강화된 테스트를 그 코드에 얹어** 실행 → **4/5 Deadlock, 성공 1/5**, warningCount 1, 중간값 `[1]`, 테스트 `expected: <5> but was: <1>` 실패. 실패 SQL = `update users u1_0 set warning_count=(u1_0.warning_count+1) where u1_0.idx=?` → **원자적 UPDATE 문장에서 데드락**이 확인됨(S락 보유 중 X락 승격의 직접 증거).
  - **After**: 성공 5/5, warningCount 5, 기록 5건, **중간값 `[1,2,3,4,5]`**(순차 처리 증거), Deadlock 0.
  - → "4/5 실패 → 5/5 성공"은 **커밋 메시지 인용이 아니라 재실행으로 입증된 수치**다.
- **검증**: `UserSanctionServiceConcurrencyTest` — 항진명제(경고수==기록수)에서 **'성공 콜 수 == 최종 경고수'로 강화**해 실제 결함을 검출. 데드락 수정 후 **4/5 실패 → 5/5 성공, warningCount 1→5, Deadlock 0** (`petory_test`, git `1f989b9f`).

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

> ⚠️ **이 벤치마크는 Meetup 작업의 "이유"가 아니고, 근거로 인용하지도 않는다.** Meetup 동시성은 §3.2대로 **정합성** 문제다. 아래는 참고 기록으로만 남긴다.
>
> 🔴 **인용 금지 사유 — 신뢰성이 아니라 타당성 문제다**:
> 0. **재현은 된다**: 2026-07-30 재실행 결과 **비관적 락 2.60ms(1~6) vs 원자적 UPDATE 8.80ms(7~14)** — 3주 전과 같은 방향·크기. 따라서 "노이즈라 못 믿는다"는 기각 논리는 **틀렸다.** 아래가 진짜 이유다.
> 1. 비교한 두 경로(`testPessimisticLockApproach` / `testAtomicUpdateApproach`)는 **테스트 안에서 재구현한 코드**이고 운영 `joinMeetup`이 아니다. 운영은 §3.2대로 **락과 조건부 UPDATE를 함께** 쓴다 — 즉 이 벤치마크의 "둘 중 하나" 전제 자체가 코드와 다르다.
> 2. 원자적 UPDATE arm만 `findById` → UPDATE → **`findById` 재조회**로 SELECT가 1회 더 나간다. 차이가 전략 차이가 아니라 **왕복 1회 차이**로 설명될 수 있다(운영 코드는 재조회 대신 `entityManager.refresh`). 느린 쪽 최소값(7ms)이 빠른 쪽 최대값(6ms)보다 큰 것도 **고정 비용**의 존재와 방향이 맞는다.
> 3. 두 helper 모두 예외를 전부 삼켜서(`// 실패 무시`) 실패한 시도의 시간도 합산된다.
> 4. `System.currentTimeMillis` ms 단위라 절대값 정밀도가 낮다. 테스트도 상대 속도를 assert하지 않는다.

`MeetupServiceRaceConditionTest.testPerformanceComparison` — **비관적 락 vs 원자적 UPDATE**(둘 다 이미 정합성은 보장) 속도 참고 비교.

- 조건: 동시 참가 10명, 각 방식 5회 반복

**실측 결과 (2026-07-07, 로컬 MySQL 8):**

| 방식          | 평균       | 최소 | 최대 |
| ------------- | ---------- | ---- | ---- |
| 비관적 락     | **2.40ms** | 1ms  | 7ms  |
| 원자적 UPDATE | **8.40ms** | 6ms  | 13ms |

> ⚠️ **실측이 통념을 뒤집음**: 이 규모(저경합·소량 데이터)에선 **비관적 락이 오히려 더 빨랐다** (2.4ms vs 8.4ms, 재실행 2.6 vs 8.8). "원자적 UPDATE가 락 대기 없어 더 빠르다"는 통념은 **여기선 성립 안 함.** 단 위 §0~2의 타당성 한계 때문에 이 결과를 "어느 전략이 빠르다"의 근거로는 쓰지 않으며, 테스트도 상대 속도를 assert하지 않는다.

**여기서 끌어낼 수 있는 것 / 없는 것:**

- ❌ **"그래서 원자적 UPDATE를 골랐다"는 서술은 폐기.** 코드는 둘 중 하나를 고른 게 아니라 §3.2대로 **둘을 함께** 쓴다.
- ❌ **"락을 참가자 INSERT까지 잡고 있지 않다"도 폐기.** `joinMeetup`은 `findByIdWithLock` 이후 참가자 INSERT를 지나 커밋까지 행 락을 보유한다.
- ❌ **"고경합 확장성 우위"** 는 이 벤치마크로 입증 안 됨 → 단정하지 않는다.
- ⭕ 남는 것 하나: **"락이 느리다"는 통념이 이 규모에서 성립하지 않았다**는 관찰. 그래서 전략 선택 근거를 속도에 두지 않고 정합성·계층 방어에 뒀다.

> 면접에서 물으면: "속도 비교를 해본 기록은 있는데 근거로 쓰지 않습니다. 비교한 두 경로가 테스트 안에서 재구현한 코드라 실제 서비스 경로가 아니고, 느리게 나온 쪽만 조회를 한 번 더 해서 전략 차이인지 왕복 차이인지 분리가 안 됩니다. 다만 '락이 느리다'는 통념이 이 규모에선 성립하지 않는다는 건 봤고, 그래서 정원 방어는 속도가 아니라 정합성 기준으로 락·조건부 UPDATE·PK를 계층으로 뒀습니다." → **자기 측정의 한계를 아는 쪽이 수치를 내세우는 것보다 강하다.**

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
> 잔액처럼 값을 읽고 검증해야 하면 **비관적 락**(PetCoin), 유일성이면 **DB Unique 제약**(닉네임·소셜), 부분 실패 롤백이면 **트랜잭션 경계**를 썼습니다. 인원은 단순 조건부 증가라 **원자적 조건부 UPDATE**가 중심이지만, Meetup은 초과가 치명적이라 **비관적 락 + 조건부 UPDATE + 복합 PK + CHECK로 계층 방어**를 뒀습니다.
> 여기에 축이 하나 더 있는데, **카운터 UPDATE 앞에 같은 행을 참조하는 FK INSERT가 있으면 락 획득 순서까지 봐야 한다**는 것입니다 — 경고와 Meetup 둘 다 예상은 Lost Update였는데 실제 증상은 락 승격 데드락이었습니다. Care 거래 확정은 중복 실행이 아니라 **격리수준 때문에 로직이 skip되는 stuck state**라는 걸 파악해 상위 엔티티 락으로 풀었습니다.
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
