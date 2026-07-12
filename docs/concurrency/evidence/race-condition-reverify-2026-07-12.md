---
date: 2026-07-12
domains: [meetup, payment, care]
type: concurrency-evidence
problem: race-condition
status: verified
metric: "Meetup 3명 제한 정확히 지켜짐(2성공+1실패), PetCoin 100→150 정확히 누적(Lost Update 없음), Care stuck state 없이 OPEN→IN_PROGRESS 정상 전이"
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

## 1. 실행 결과 요약

| 테스트 클래스 | 결과 |
|---|---|
| MeetupServiceRaceConditionTest | **6/6 통과** |
| PetCoinServiceRaceConditionTest | **4/4 통과** |
| CareDealConcurrencyTest | **1/1 통과** |

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

## 4. Care — 거래 확정 stuck state 없음

`동시 거래 확정 시도 시 Stuck State 없이 정상적으로 상태가 변경되어야 한다` 테스트 로그:

```
거래 확정 시 펫코인 처리 시작: careRequestIdx=1403, requesterId=24605, providerId=24606
거래 확정 완료: conversationIdx=11076, careRequestIdx=1403, providerId=24606, 상태 변경: OPEN -> IN_PROGRESS
```

두 참여자가 동시에 확정을 시도해도 `Conversation` 레벨 `PESSIMISTIC_WRITE`로 직렬화되어 `OPEN`에 멈추는 stuck state 없이 `IN_PROGRESS`로 정확히 전이됨을 재확인했다.

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
