---
date: 2026-09-10
domains: [payment]
type: refactoring
problem: parameter-reassignment-and-redundant-save
status: verified
metric: "파라미터 재할당 2곳 제거 · 불필요한 save() 4곳 제거(영속 엔티티라 더티 체킹으로 반영). 동작 변화 없음 — 관련 테스트 35/35 통과"
before_commit: 26f07397
related: [docs/refactoring/board/board-search-redundant-users-join-2026-09-09.md]
---

# 에스크로 서비스 정리 — 파라미터 재할당 · 불필요한 `save()`

> 2026-09-10. §3-1(에스크로 락 순서) 딥다이브 중 코드를 읽다 발견했다.
> **동작을 바꾸지 않는 정리다.** 성능 개선도 버그 수정도 아니다.

---

## 발생 위치

`backend/main/java/com/linkup/Petory/domain/payment/service/PetCoinEscrowService.java`

| # | 메서드 | 문제 |
|---|---|---|
| A | `releaseToProvider` · `refundToRequester` | **파라미터 재할당** |
| B | `assignProvider` · `adjustHeldAmount` · `releaseToProvider` · `refundToRequester` | **불필요한 `save()`** |

---

## 문제 A — 파라미터를 덮어쓴다

```java
public PetCoinEscrow releaseToProvider(PetCoinEscrow escrow) {
    escrow = escrowRepository.findByIdForUpdate(escrow.getIdx())   // ← 받은 걸 버리고 재할당
            .orElseThrow(...);
    escrow.release();
```

같은 이름이 **두 가지 다른 것**을 가리킨다 — 첫 줄까지는 호출자가 락 없이 읽은 엔티티, 그 뒤로는 락을 잡고 다시 읽은 엔티티. 읽는 사람이 지금 어느 쪽이 스코프에 있는지 매번 되짚어야 한다.

그리고 **왜 다시 읽는지가 코드에 안 적혀 있었다.** 주석은 "비관적 락으로 에스크로 조회 (Race Condition 방지)"뿐이라, 재조회가 필수인 이유(락 없이 검사하면 두 트랜잭션이 동시에 `HOLD`를 읽고 둘 다 통과하는 check-then-act)가 드러나지 않는다.

### 처음 계획을 바꾼 이유

애초 계획은 **시그니처를 `Long escrowIdx`로 바꾸는 것**이었다(파라미터가 사실상 ID 운반용이므로). 호출부를 확인하고 접었다.

- 호출자들이 **호출 이후에도 엔티티를 쓴다** — `CareRequestService`의 로그가 `escrow.getIdx()`·`getAmount()`를 찍는다
- 시그니처를 바꾸면 **다른 도메인(care) 호출부 4곳**을 건드려야 하는데, 얻는 건 "시그니처가 정직해진다"뿐이다
- 진짜 냄새는 시그니처가 아니라 **재할당**이었고, 그건 호출부를 안 건드리고 고칠 수 있다

CLAUDE.md의 **"외과적 변경 — 손대야 할 줄만 수정할 것"**에 맞춘 판단이다.

---

## 문제 B — `save()` 가 사실상 no-op

`findByIdForUpdate` / `findByCareRequestForUpdate` 로 가져온 엔티티는 **영속 상태**다. `@Transactional` 안에서 상태를 바꾸면 **더티 체킹으로 반영**되므로 `escrowRepository.save(escrow)` 는 하지 않아도 된다.

⚠️ **새 엔티티를 만드는 두 곳은 `save()` 가 필수라 손대지 않았다** — `holdForRequest`(`:69`), `createEscrow`(`:171`). 둘 다 `PetCoinEscrow.builder()` 로 만든 비영속 인스턴스다.

---

## 개선 코드

```diff
  public PetCoinEscrow releaseToProvider(PetCoinEscrow escrow) {
-     // 비관적 락으로 에스크로 조회 (Race Condition 방지)
-     escrow = escrowRepository.findByIdForUpdate(escrow.getIdx())
+     // 파라미터는 호출자가 락 없이 읽은 엔티티라 상태가 이미 낡았을 수 있다. 여기서 잠그고 다시
+     // 읽어야 아래 release() 의 상태 가드가 의미를 갖는다 — 락 없이 검사하면 두 트랜잭션이 동시에
+     // HOLD 를 읽고 둘 다 통과한다(check-then-act). 파라미터는 식별자를 넘겨받는 용도로만 쓴다.
+     PetCoinEscrow locked = escrowRepository.findByIdForUpdate(escrow.getIdx())
              .orElseThrow(() -> new PetCoinEscrowNotFoundException());
 
-     escrow.release();
+     locked.release();
      ...
-     PetCoinEscrow saved = escrowRepository.save(escrow);
-     log.info("...", saved.getIdx(), ...);
-     return saved;
+     // save() 를 부르지 않는다: locked 는 영속 상태라 변경이 더티 체킹으로 반영된다.
+     log.info("...", locked.getIdx(), ...);
+     return locked;
  }
```

`refundToRequester` 도 동일. `assignProvider`·`adjustHeldAmount` 는 `save()` 제거만.

---

## 🚫 안 건드린 것

### 1. `releaseToProvider` 와 `refundToRequester` 를 합치지 않았다

구조가 쌍둥이라 제일 먼저 눈에 들어온다.
```
공통:  findByIdForUpdate → 상태전이 → 코인 이동 → 로그
차이:  release()/refund() · payoutCoins(제공자)/refundCoins(요청자) · 이벤트 발행 유무
```

합치면 `settle(escrow, target, action)` 같은 게 되는데 **돈이 어디로 가는지가 파라미터에 숨는다.** 게다가 `release` 만 `PaymentRecordedEvent` 를 쏜다(매출 집계라 환불엔 없어야 한다). 그 분기까지 안으로 들어간다.

**돈 경로는 중복이 있어도 분기가 겉으로 보이는 게 맞다.** 줄이면 안 되는 중복이다.

### 2. `findByCareRequestForUpdate` (죽은 메서드) — 기록만

`PetCoinEscrowService.findByCareRequestForUpdate` 는 **호출자가 없다**(내부 `assignProvider`·`adjustHeldAmount` 는 리포지토리 것을 직접 쓴다).

게다가 주석이 사실과 다르다:
> *"비관적 락을 사용한 조회 (동시성 제어용) 상태 변경 시 Race Condition 방지를 위해 사용"*

이 메서드는 **락을 잡고 바로 리턴한다.** 호출자가 트랜잭션 밖이면 메서드가 끝나며 트랜잭션과 함께 락도 풀려서, 받아든 시점엔 이미 보호가 없다. 지금은 아무도 안 써서 무해하지만 **이 주석을 믿고 쓰면 락이 걸린 줄 알고 안 걸린다.**

CLAUDE.md 규칙(**"무관해 보이는 죽은 코드는 삭제하지 말고 언급만 할 것"**)에 따라 이번 범위 밖으로 둔다.

---

## 검증 (2026-09-10)

`./gradlew compileJava` 통과.

| 테스트 | 결과 |
|---|---|
| `CareEscrowAtCreationTest` | 8/8 |
| `CareRequestServiceTest` | 10/10 |
| `CareCompletionConfirmationTest` | 6/6 |
| `PetCoinServiceRaceConditionTest` | 4/4 |
| `CareDealApplicationScopeTest` | 3/3 |
| `CareRequestSchedulerSettlementTest` | 2/2 |
| `CareDealConcurrencyTest` | 1/1 |
| `CareDealEscrowFailureTest` | 1/1 |
| **합계** | **35/35 · 실패 0** |

동작을 바꾸지 않는 정리라 **새 테스트는 추가하지 않았다.** 기존 테스트가 그대로 통과하는 것이 이 변경의 검증이다.

---

## 판정 — 이 코드는 생각보다 깨끗하다

어지러워 보이는 건 **도메인이 복잡해서지 코드가 나빠서가 아니다.** 등록 → 확정 → 완료/환불 4단계에 락 + 상태 가드 + 이벤트가 얹혀 있는데, 이건 **본질적 복잡도**라 추상화를 씌우면 오히려 돈의 흐름이 안 보이게 된다.

`holdForRequest` 의 주석처럼 **왜**를 적어둔 자리도 여럿이다:
> *"왜 확정이 아니라 등록에서 잡는가: 확정 시 차감이면 제공자가 신청하고 채팅까지 마친 뒤에야 요청자의 잔액 부족이 드러난다."*

---

## 상태

- **개선 완료** (2026-09-10)
- 브랜치 `refactor/escrow-service-cleanup` → `dev` 머지 → `main` PR
