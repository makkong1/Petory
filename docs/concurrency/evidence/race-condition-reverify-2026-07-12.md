---
date: 2026-07-12
domains: [meetup, payment, care]
type: concurrency-evidence
problem: race-condition
status: verified
metric: "PetCoin before 100→110(Lost Update 3/3 재현)→after 100→150(3/3 해결). Meetup 진짜 최초버그(a549eb33) 재현 결과는 인원초과가 아니라 Deadlock으로 인한 요청 실패(성공1/실패2, 3/3 재현) — a5943b18은 이미 Pessimistic Lock 도입된 이후였음. Care는 기존 재실행만(§4)"
related: [docs/concurrency/concurrency-strategy-master.md]
---

# 동시성/Race Condition 재검증 — 테스트 재실행 (2026-07-12)

> 목적: 포트폴리오 페이지(`PetoryRefactoringPage.jsx` 02번 "동시성/Race Condition 해결")에 나온 Meetup·PetCoin·Care 3개 대표 사례를 실제 동시성 테스트로 재실행해 로그를 재확인한다. EXPLAIN/k6와는 성격이 다르다 — 여기서 "증거"는 실행계획이 아니라 **동시 스레드가 실제로 만든 최종 상태**다.

## 0. 방법론

- 재현 대상: 기존 테스트 3개(신규 작성 아님, 그대로 재실행)
  - `MeetupServiceRaceConditionTest` (6 tests)
  - `PetCoinServiceRaceConditionTest` (4 tests)
  - `CareDealConcurrencyTest` (1 test)
- 실행: `./gradlew test --tests ... --rerun --info`
- 환경: 로컬 MySQL 8, ExecutorService 기반 멀티스레드 동시 실행(CountDownLatch/트랜잭션 경계는 테스트마다 다름)
- 해결 커밋(`git log --oneline -- <경로>`로 확인, 문서 §5 참고): 사례마다 다른 시점에 해결됨

| 사례 | 해결 커밋 | 날짜 | 그 직전 상태(before) |
|---|---|---|---|
| Meetup 인원 초과 | [`bf32d155`](https://github.com/makkong1/Petory/commit/bf32d155) | 2025-12-20 | [`a5943b18`](https://github.com/makkong1/Petory/commit/a5943b18) |
| PetCoin Lost Update | [`c2bb32f9`](https://github.com/makkong1/Petory/commit/c2bb32f9) | 2026-02-18 | [`60455169`](https://github.com/makkong1/Petory/commit/60455169) |
| Care 거래확정 stuck state | [`1b5df6f3`](https://github.com/makkong1/Petory/commit/1b5df6f3) | 2026-01-04 | [`e0eb851f`](https://github.com/makkong1/Petory/commit/e0eb851f) |

각 해결 커밋에 해당 사례의 재현 테스트(`*RaceConditionTest.java`/`*ConcurrencyTest.java`)가 같이 신규 추가돼 있다(`git show --stat <커밋>`로 확인). 다만 이 테스트들은 **해결 커밋에서** 추가된 것이지, "before 커밋에 이미 존재했다"는 뜻은 아니다 — §2.5/§3.5에서 `git worktree`로 각 before 커밋을 실제 checkout해 직접 검증했다.

## 1. 실행 결과 요약

| 테스트 클래스                   | 결과         |
| ------------------------------- | ------------ |
| MeetupServiceRaceConditionTest  | **6/6 통과** |
| PetCoinServiceRaceConditionTest | **4/4 통과** |
| CareDealConcurrencyTest         | **1/1 통과** |

총 11개 테스트 전부 통과. XML 결과(`build/test-results/test/TEST-*.xml`)의 `failures="0" errors="0"`으로 확인.

## 2. Meetup — 원자적 조건부 UPDATE로 인원 초과 차단

`Race Condition 재현 - 동시에 3명이 참가 시도하여 인원 초과 발생` 테스트 로그:

```
성공한 참가: 2명
실패한 참가: 1명
최종 currentParticipants: 3
실제 참가자 수 (DB): 3
최대 인원: 3
```

최대 3명 제한(기존 1명 + 신규 시도 3명)에서 신규 2명만 성공하고 1명은 실패해 `currentParticipants`가 정확히 3에서 멈춘다. `WHERE currentParticipants < maxParticipants` 조건부 UPDATE가 DB 레벨에서 원자적으로 동작함을 재확인했다.

## 2.5. worktree 검증 — Meetup의 before 커밋은 이미 안전했다

`git worktree`로 `a5943b18`(before, `bf32d155`의 부모)를 실제 checkout해서 `MeetupService.joinMeetup()`을 재구성 없이 직접 호출했다. **예상과 다른 결과가 나왔다**: 이 시점 코드를 열어보니 이미 `meetupRepository.findByIdWithLock()`(Pessimistic Lock)으로 모임을 조회하고 있었다.

동시 참가 시나리오(최대 3명, 기존 1명 + 신규 3명 동시 시도)를 이 실제 코드로 3회 반복 실행:

| 시행 | 성공 | 실패 | 최종 currentParticipants | 인원 초과 |
|---|---|---|---|---|
| 1~3회 모두 | 2 | 1 | 3 | **없음** |

dev(after, 원자적 조건부 UPDATE)로도 동일하게 3회 실행 — 결과는 성공2/실패1/초과없음으로 **완전히 동일**했다.

**해석**: `bf32d155`는 "버그를 처음 고친 커밋"이 아니라, **이미 Pessimistic Lock으로 안전하게 막혀 있던 것을 원자적 UPDATE 방식으로 교체한 커밋**으로 보인다. `concurrency-strategy-master.md`와 포트폴리오 문서가 이 사례의 verification에 "Meetup은 **트랜잭션을 우회해** 레이스를 결정론적으로 재현했다"고 이미 적어 둔 것과 일치한다 — 즉 원래 이 버그는 `@Transactional`이 걸린 서비스 메서드를 정상 호출해서는 재현되지 않고, 테스트가 트랜잭션 경계를 의도적으로 우회해야만 드러나는 종류였다. 오늘 워크트리 재검증은 "정상적인 서비스 호출 경로"로 진행했기 때문에, Pessimistic Lock이 있던 시점에서는 재현되지 않은 것이다.

## 2.6. worktree 검증 — 진짜 최초 버그 커밋을 찾았다 (2026-07-12, 후속)

`git log -- MeetupService.java`로 더 이전 이력을 훑어 진짜 최초 버그 커밋을 특정했다: [`a549eb33`](https://github.com/makkong1/Petory/commit/a549eb33) (2025-12-13, `feature: 이메일 인증 구현`). 이 커밋의 `joinMeetup()`을 열어보면 `meetupRepository.findById(meetupIdx)` — **정말로 락이 전혀 없다.**

시간순 재정리:
| 커밋 | 날짜 | 내용 |
|---|---|---|
| `a549eb33` | 2025-12-13 | `findById()` — 락 없음. **진짜 최초 버그 시점** |
| (트러블슈팅 문서 작성) | 2025-12-19 | `race-condition-participants.md` |
| `a5943b18` | 2025-12-19 | `findByIdWithLock()` 도입. 커밋 메시지 "트러블슈팅 파일 생성...해결방법 기반 실서비스 로직 수정"이 문서 작성일과 정확히 일치 |
| `bf32d155` | 2025-12-20 | Pessimistic Lock → 원자적 조건부 UPDATE로 전략 교체 |

`git worktree`로 `a549eb33`를 실제 checkout해서 동일한 동시 참가 시나리오(최대 3명, 기존 1명 + 신규 3명 동시 시도)를 3회 반복 실행했다.

**예상과 또 다른 결과가 나왔다.** 3번 모두 동일하게:

```
성공: 1, 실패: 2
최종 currentParticipants: 2
인원 초과 발생: NO
```

인원초과(Lost Update)가 아니라 **정반대** — 남은 자리가 2개인데 1명만 성공하고 2명이 부당하게 실패했다. 실패 예외를 캡처하면:

```
CannotAcquireLockException: Deadlock found when trying to get lock; try restarting transaction
[update meetup set ... where idx=?]
```

**해석**: `findById()`(SELECT) 자체는 락을 잡지 않지만, 이어지는 `meetupRepository.save(meetup)`이 발행하는 `UPDATE meetup SET ... WHERE idx=?`는 InnoDB가 자동으로 해당 행에 exclusive lock을 건다. 여러 스레드가 동시에 이 UPDATE를 시도하면 서로의 락을 기다리며 순환 대기가 발생하고, MySQL이 데드락을 감지해 한쪽 트랜잭션을 강제로 롤백시킨다. 즉 **"코드에 락이 없다" ≠ "자동으로 Lost Update가 조용히 발생한다"** — MySQL의 기본 트랜잭션 격리 수준과 InnoDB의 암묵적 행 잠금이 개입해서, 실제로는 Lost Update보다 먼저 Deadlock 예외로 요청이 실패하는 경향을 보였다. 이게 원 트러블슈팅 문서가 "트랜잭션을 우회해서 강제로 재현했다"고 명시한 이유로 보인다 — 정상적인 트랜잭션 경계 안에서 인원초과 자체를 재현하려면 이 Deadlock 방어선을 통과해야 하는데, 그러지 못하고 매번 여기서 막혔다.

**정리**: Meetup의 동시성 결함은 세 단계로 존재했다.
1. **`a549eb33`(락 없음)**: 사용자 경험 관점의 결함 — 정당한 참가 요청이 데드락으로 실패(인원초과는 아님)
2. **`a5943b18`(Pessimistic Lock)**: 데드락도 인원초과도 없이 안전, 다만 락 대기로 인한 처리량 저하 가능성
3. **`bf32d155`(원자적 UPDATE, 현재)**: 락 경합 없이 안전 + 처리량 유지

"인원초과 버그"라는 표현은 트랜잭션을 우회한 별도 테스트에서만 확인 가능한 이론적 위험이었고, 실제 서비스 호출 경로에서 최초 버그가 일으킨 관찰 가능한 증상은 **부당한 요청 실패(Deadlock)** 였다는 게 이번 재검증의 핵심 발견이다.

## 3. PetCoin — 재검증 중 발견: "문제 상황" 테스트가 더 이상 문제를 재현하지 못한다

`❌ 문제 상황: chargeCoins 동시 충전 시 Lost Update 재현 (findById 사용)` 테스트를 재실행한 결과, **테스트 이름·주석과 실제 실행된 SQL이 어긋나 있었다.**

테스트 로그:

```
현재 chargeCoins는 findById 사용 → 락 없음 → Lost Update 가능
```

하지만 실제 Hibernate 로그:

```sql
select u1_0.idx, ... from users u1_0 where u1_0.idx=? for update
```

5개 스레드가 동시에 충전을 시도했는데, `for update`(비관적 락)로 인해 스레드가 직렬화되어 잔액이 정확히 순차 누적됐다:

```
[충전-2] balanceBefore=100, balanceAfter=110
[충전-4] balanceBefore=110, balanceAfter=120
[충전-0] balanceBefore=120, balanceAfter=130
[충전-1] balanceBefore=130, balanceAfter=140
[충전-3] balanceBefore=140, balanceAfter=150
```

최종 잔액 150(예상과 정확히 일치), Lost Update 없음.

**해석**: 이 테스트가 "chargeCoins는 findById 사용 → 락 없음"이라는 주석으로 문제 상황을 재현하려 했지만, 실제 `PetCoinService.chargeCoins()`는 이미 `findByIdForUpdate`를 쓰도록 수정되어 있다. 즉 **"문제 재현용"과 "해결 검증용" 두 테스트가 사실상 같은 코드 경로를 테스트하고 있다** — 테스트 이름/주석이 리팩토링을 따라가지 못한 흔적이다. 실제 정합성(잔액 150)은 문제없이 보장되지만, 이 테스트 스위트 자체는 "Lost Update가 실제로 어떻게 발생하는지"를 더 이상 보여주지 못한다.

## 3.5. worktree 검증 — 진짜 Lost Update를 실제 코드로 재현했다

위 발견(현재 코드의 "문제상황" 테스트가 더 이상 문제를 재현 못함)을 확인하기 위해, `git worktree`로 `60455169`(before, `c2bb32f9`의 부모)를 실제 checkout했다. 이 시점의 `PetCoinService.chargeCoins()`를 열어보면:

```java
Users currentUser = usersRepository.findById(user.getIdx())  // 락 없음
        .orElseThrow(...);
Integer balanceAfter = currentUser.getPetCoinBalance() + amount;
currentUser.setPetCoinBalance(balanceAfter);
usersRepository.save(currentUser);
```

정말로 `findById()`(락 없음)다. 이 시점엔 동시성 테스트 자체가 없었으므로(`PetCoinServiceRaceConditionTest.java`는 해결 커밋에서 신규 추가됨), 신규로 짧은 동시성 테스트를 작성해 `chargeCoins()`를 재구성 없이 직접 호출했다: 초기 잔액 100에서 5개 스레드가 동시에 +10씩 충전.

**3회 반복 실행 결과**:

| 시행 | 예상 최종 잔액 | 실제 최종 잔액 | Lost Update |
|---|---|---|---|
| 1회 | 150 | **110** | 발생 |
| 2회 | 150 | **110** | 발생 |
| 3회 | 150 | **110** | 발생 |

**3번 모두 정확히 110** — 5건 중 4건의 충전이 유실되고 1건만 반영됐다(100+10=110). 완전히 결정론적으로 재현됐다.

같은 테스트를 dev(after, `findByIdForUpdate`)로 3회 반복 실행하면:

| 시행 | 예상 최종 잔액 | 실제 최종 잔액 | Lost Update |
|---|---|---|---|
| 1~3회 모두 | 150 | **150** | **없음** |

**이게 Care·Chat과 다른 결정적 지점이다**: N+1 재검증은 "코드가 몇 개의 쿼리를 만드는가"라는 정적인 성격이라 재구성해도 오차가 크지 않았지만, 동시성 버그는 "여러 스레드가 실제로 경합하는 동적 타이밍"이 핵심이라 재구성 코드로는 절대 같은 신뢰도를 낼 수 없다. 이번 워크트리 검증이 유일하게 "그 시점 진짜 코드가 진짜로 무너진다"를 직접 실행으로 증명한 사례다.

## 4. Care — 거래 확정 stuck state 없음

`동시 거래 확정 시도 시 Stuck State 없이 정상적으로 상태가 변경되어야 한다` 테스트 로그:

```
거래 확정 시 펫코인 처리 시작: careRequestIdx=1403, requesterId=24605, providerId=24606
거래 확정 완료: conversationIdx=11076, careRequestIdx=1403, providerId=24606, 상태 변경: OPEN -> IN_PROGRESS
```

두 참여자가 동시에 확정을 시도해도 `Conversation` 레벨 `PESSIMISTIC_WRITE`로 직렬화되어 `OPEN`에 멈추는 stuck state 없이 `IN_PROGRESS`로 정확히 전이됨을 재확인했다.

**Care 사례는 worktree 재검증을 생략했다.** Meetup·PetCoin 두 건에서 이미 "worktree로 실제 before 커밋을 checkout해 진짜 코드를 실행"하는 방법론 자체는 충분히 검증됐고(하나는 재현 성공, 하나는 예상 밖 결과 발견), Care 거래확정은 두 참여자 상태를 동시에 다루는 시나리오라 fixture 구성이 더 복잡해 시간 대비 추가로 얻을 정보가 제한적이라고 판단했다. §4의 재실행 결과(기존 `CareDealConcurrencyTest`를 dev 코드로 재실행)는 유효하지만, 이건 재구성이 아니라 원래도 dev 코드를 그대로 실행한 것이므로 별도 worktree 검증이 주는 한계는 없다.

## 5. 재현 방법

```bash
./gradlew test \
  --tests "com.linkup.Petory.domain.meetup.service.MeetupServiceRaceConditionTest" \
  --tests "com.linkup.Petory.domain.payment.service.PetCoinServiceRaceConditionTest" \
  --tests "com.linkup.Petory.domain.care.service.CareDealConcurrencyTest" \
  --rerun --info
```

## 6. 관련 문서

- 전략 프레임워크: [`concurrency/concurrency-strategy-master.md`](../concurrency-strategy-master.md)
- 포트폴리오 대표 사례: `makkong1-github.io` `PetoryRefactoringPage.jsx` 02번
