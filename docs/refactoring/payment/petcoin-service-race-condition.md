# PetCoinService Race Condition 상세 분석

## 개요

`PetCoinService`의 `chargeCoins`, `payoutCoins`, `refundCoins` 메서드에서 **비관적 락(Pessimistic Lock)** 미적용으로 인한 Race Condition 문제를 분석합니다.

**관련 문서**: [Payment 백엔드 성능 최적화](./payment-backend-performance-optimization.md)

---

## 현재 코드 분석

### 1. deductCoins (락 적용됨 ✅)

```java
// PetCoinService.java Lines 82-125
@Transactional
public PetCoinTransaction deductCoins(Users user, Integer amount, String relatedType,
                Long relatedIdx, String description) {
    // ...

    // 사용자 최신 정보 조회 (비관적 락으로 Race Condition 방지)
    Users currentUser = usersRepository.findByIdForUpdate(user.getIdx())
                    .orElseThrow(() -> new RuntimeException("User not found"));

    Integer balanceBefore = currentUser.getPetCoinBalance();
    if (balanceBefore < amount) {
        throw new IllegalStateException(...);
    }
    Integer balanceAfter = balanceBefore - amount;

    currentUser.setPetCoinBalance(balanceAfter);
    usersRepository.save(currentUser);
    // ...
}
```

- `findByIdForUpdate` 사용 → `SELECT ... FOR UPDATE` (비관적 락)
- 동시 요청 시 한 트랜잭션이 락을 잡으면 다른 트랜잭션은 대기 → 순차 처리 보장

---

### 2. chargeCoins (락 미적용 ❌)

```java
// PetCoinService.java Lines 36-69
@Transactional
public PetCoinTransaction chargeCoins(Users user, Integer amount, String description) {
    // ...

    // 사용자 최신 정보 조회  ← findById 사용 (락 없음)
    Users currentUser = usersRepository.findById(user.getIdx())
                    .orElseThrow(() -> new RuntimeException("User not found"));

    Integer balanceBefore = currentUser.getPetCoinBalance();  // 예: 100
    Integer balanceAfter = balanceBefore + amount;            // 예: 100 + 50 = 150

    currentUser.setPetCoinBalance(balanceAfter);
    usersRepository.save(currentUser);
    // ...
}
```

---

### 3. payoutCoins (락 미적용 ❌)

```java
// PetCoinService.java Lines 136-174
@Transactional
public PetCoinTransaction payoutCoins(Users user, Integer amount, String relatedType,
                Long relatedIdx, String description) {
    // ...

    // 사용자 최신 정보 조회  ← findById 사용 (락 없음)
    Users currentUser = usersRepository.findById(user.getIdx())
                    .orElseThrow(() -> new RuntimeException("User not found"));

    Integer balanceBefore = currentUser.getPetCoinBalance();
    Integer balanceAfter = balanceBefore + amount;

    currentUser.setPetCoinBalance(balanceAfter);
    usersRepository.save(currentUser);
    // ...
}
```

---

### 4. refundCoins (락 미적용 ❌)

```java
// PetCoinService.java Lines 185-223
@Transactional
public PetCoinTransaction refundCoins(Users user, Integer amount, String relatedType,
                Long relatedIdx, String description) {
    // ...

    // 사용자 최신 정보 조회  ← findById 사용 (락 없음)
    Users currentUser = usersRepository.findById(user.getIdx())
                    .orElseThrow(() -> new RuntimeException("User not found"));

    Integer balanceBefore = currentUser.getPetCoinBalance();
    Integer balanceAfter = balanceBefore + amount;

    currentUser.setPetCoinBalance(balanceAfter);
    usersRepository.save(currentUser);
    // ...
}
```

---

## UsersRepository 락 메서드

```java
// SpringDataJpaUsersRepository.java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT u FROM Users u WHERE u.idx = :idx")
Optional<Users> findByIdForUpdate(@Param("idx") Long idx);
```

- `PESSIMISTIC_WRITE` → `SELECT ... FOR UPDATE` (행 락)
- 트랜잭션 커밋/롤백 시까지 해당 행에 대한 쓰기 락 유지

---

## 문제점: Race Condition 시나리오

테스트 조건: 초기 잔액 100, 동시 충전 5건 × 10 = 50 (예상 최종 150)

### 시나리오 1: 동시 충전 5건 (chargeCoins) — 테스트 로그 기반

| 시간 | 충전-0~4 (각 10 충전) | DB 잔액 |
|------|------------------------|---------|
| T1 | 5개 스레드 모두 findById → balanceBefore=100 | 100 |
| T2 | 5개 모두 balanceAfter=110 (100+10) 계산, transaction INSERT | 100 |
| T3 | users UPDATE 시도 → Lost Update + **Deadlock** 발생 | - |
| T4 | 1~2건만 커밋 성공, 나머지 3~4건 Deadlock 롤백 | **110** ❌ |

**실제 테스트 결과**:
- 성공: 1~2건, 실패: 3~4건 (Deadlock)
- 예상 최종 잔액: 150
- **실제 최종 잔액: 110**
- 거래 내역: 1~2건만 저장 (나머지는 롤백)

**결과**: 5건 중 1~2건만 반영됨. 50이 늘어나야 하는데 10만 늘어남. Lost Update(덮어쓰기) + Deadlock으로 40 분실.

---

### 시나리오 2: 동시 지급 5건 (payoutCoins)

동일 패턴. 5건 × 10 지급 시 예상 150 → 실제 110 (1건만 반영).

---

### 시나리오 3: 동시 환불 5건 (refundCoins)

동일 패턴. 5건 × 10 환불 시 예상 150 → 실제 110 (1건만 반영).

---

## 왜 deductCoins만 락을 쓰는가?

- `deductCoins`는 **잔액 부족 검증**이 필수 → 동시 차감 시 부족 검증이 무의미해질 수 있어 락 적용
- `chargeCoins`, `payoutCoins`, `refundCoins`는 **증가만** 하므로 검증 실패 위험이 낮다고 판단했을 가능성
- 하지만 **증가 연산도 read-modify-write**이므로 동시에 같은 balanceBefore를 읽으면 덮어쓰기 발생

---

## 해결 방안

### 적용할 변경

`chargeCoins`, `payoutCoins`, `refundCoins`에서 `findById` → `findByIdForUpdate`로 변경.

```java
// 변경 전
Users currentUser = usersRepository.findById(user.getIdx())
                .orElseThrow(() -> new RuntimeException("User not found"));

// 변경 후
Users currentUser = usersRepository.findByIdForUpdate(user.getIdx())
                .orElseThrow(() -> new RuntimeException("User not found"));
```

### 수정 대상 라인

| 메서드 | 파일 라인 |
|--------|-----------|
| chargeCoins | 43 |
| payoutCoins | 146 |
| refundCoins | 204 |

---

## 해결 적용 내용 ✅

### 적용한 변경

`chargeCoins`, `payoutCoins`, `refundCoins` 세 메서드에서 사용자 조회 시 `findById`를 `findByIdForUpdate`로 변경했다.

### 동작 원리

1. **findByIdForUpdate**: `SELECT ... FOR UPDATE` (비관적 락) 실행
2. **락 획득**: 해당 User 행에 쓰기 락을 걸어 다른 트랜잭션이 같은 행을 수정하지 못하게 함
3. **순차 처리**: 동시 요청 시 한 트랜잭션이 락을 잡으면 나머지는 대기 → read-modify-write가 순차적으로 실행됨
4. **락 해제**: 트랜잭션 커밋/롤백 시 락 해제

### 적용 후 시나리오 (동시 충전 2건)

| 시간 | 트랜잭션 A (50 충전) | 트랜잭션 B (30 충전) | DB 잔액 |
|------|----------------------|----------------------|---------|
| T1 | findByIdForUpdate → 락 획득, balanceBefore=100 | | 100 |
| T2 | | findByIdForUpdate → **대기** (A가 락 보유) | 100 |
| T3 | balanceAfter=150, save, commit → **락 해제** | | 150 |
| T4 | | 락 획득, balanceBefore=**150** (최신값) | 150 |
| T5 | | balanceAfter=180, save, commit | **180** ✅ |

**결과**: A 완료 후 B가 최신 잔액(150)을 읽어 180으로 정확히 반영됨.

### 수정한 파일·라인

| 메서드 | 파일 | 변경 내용 |
|--------|------|-----------|
| chargeCoins | PetCoinService.java | L43: `findById` → `findByIdForUpdate` |
| payoutCoins | PetCoinService.java | L146: `findById` → `findByIdForUpdate` |
| refundCoins | PetCoinService.java | L204: `findById` → `findByIdForUpdate` |

---

## 주의사항

- **락 범위**: `findByIdForUpdate` 호출 시점 ~ 트랜잭션 커밋까지 해당 User 행 락 유지
- **데드락**: 동일 사용자에 대한 순차 처리만 발생하므로 데드락 위험 낮음 (다만 User A → User B, User B → User A 같은 교차 락은 없음)
- **성능**: 동일 사용자 동시 요청 시 직렬화됨. 일반적으로 충전/지급/환불 빈도가 높지 않아 수용 가능

---

## 테스트

**파일**: `PetCoinServiceRaceConditionTest.java`

### 테스트 구성

| 테스트 메서드 | 목적 | DB 영향 |
|---------------|------|---------|
| `testChargeCoins_RaceCondition_ProblemOccurs` | chargeCoins 동시 충전 시 Lost Update 재현 | @AfterEach에서 거래내역·사용자 삭제 |
| `testChargeCoins_RaceCondition_Fixed` | findByIdForUpdate 적용 후 잔액 일관성 검증 | 동일 |
| `testPayoutCoins_RaceCondition_ProblemOccurs` | payoutCoins 동시 지급 시 Lost Update 재현 | 동일 |
| `testRefundCoins_RaceCondition_ProblemOccurs` | refundCoins 동시 환불 시 Lost Update 재현 | 동일 |

### 실행 방법

```bash
./gradlew test --tests PetCoinServiceRaceConditionTest
```

### 로그

- `setUp`/`tearDown`: 테스트 사용자 생성·삭제, 거래 내역 삭제
- 각 충전/지급/환불: 스레드명, amount, balanceBefore/After, 소요시간
- 결과: 성공 건수, 예상/실제 최종 잔액, Race Condition 발생 여부

### 해결 후

`chargeCoins`, `payoutCoins`, `refundCoins`에 `findByIdForUpdate` 적용 시 `testChargeCoins_RaceCondition_Fixed`가 항상 통과해야 함.

---

## 체크리스트

- [x] chargeCoins: findById → findByIdForUpdate
- [x] payoutCoins: findById → findByIdForUpdate
- [x] refundCoins: findById → findByIdForUpdate
