# 펫코인 결제 시스템 트러블슈팅 분석

## 📋 개요

이 문서는 펫코인 결제 시스템의 잠재적 문제점과 트러블슈팅 포인트를 분석한 문서입니다.
다음 문서들을 종합 분석하여 작성되었습니다:
- `docs/domains/payment.md` - Payment 도메인 설계 문서
- `docs/architecture/펫케어 코인 관련 흐름.md` - 펫코인 거래 흐름 문서
- 백엔드 Payment 관련 서비스 코드

## 🔍 분석 기준 및 현재 구현 상태

### 분석 기준
이 문서는 `.cursorrules`에 명시된 개발 역량 규칙을 기준으로 분석되었습니다:
- **트랜잭션**: 트랜잭션 관리 및 동시성 제어 능력 필수
- **락**: 비관적 락, 낙관적 락 등 동시성 제어 기법 이해 및 적용
- **인덱스**: 인덱스 설계 및 최적화 능력 필수
- **실운영 고려**: 실제 운영 환경을 고려한 에러 처리, 로깅, 모니터링

### 📅 업데이트 이력
- 2026-01-28: Critical 우선순위 문제 6개 해결 완료
  - 트랜잭션 일관성 문제 (1.1, 1.2)
  - 동시성 문제 (2.2, 2.3)
  - 거래 취소 시 환불 처리 (6.1)
  - 자동 완료 처리 문제 (7.1)
  - 데이터베이스 제약조건 (10.1, 10.2)

### 현재 구현 상태 요약

#### ✅ 잘 구현된 부분
- `ConversationService.confirmCareDeal()`: 비관적 락 사용 (`findByIdWithLock`)
- `PetCoinEscrow` 엔티티: `@JoinColumn(unique = true)` 제약조건으로 중복 방지
- 모든 서비스 메서드에 `@Transactional` 적용
- 에스크로 생성/지급/환불 로직 구현 완료

#### ✅ 해결 완료된 부분
- **트랜잭션 일관성**: 에스크로 생성/지급 실패 시 롤백 처리 완료 ✅
- **동시성 제어**: 
  - `PetCoinService.deductCoins()`: 비관적 락 추가 완료 ✅
  - `PetCoinEscrowService`: 비관적 락 추가 완료 ✅
- **거래 취소 시 환불**: `CANCELLED` 상태 변경 시 환불 로직 추가 완료 ✅
- **자동 완료 처리**: 스케줄러에서 서비스 메서드 호출로 변경 완료 ✅
- **데이터베이스 제약조건**: 
  - 잔액 음수 방지 제약조건 추가 완료 ✅
  - 에스크로 중복 방지 제약조건 이미 존재 (unique = true) ✅

#### ⚠️ 개선이 필요한 부분
- **데이터 불일치 문제**: offeredCoins 검증 강화 필요
- **잔액 부족 문제**: UX 개선 필요 (명확한 에러 메시지)
- **에스크로 상태 관리**: Idempotency 키, 처리 이력 기록 필요
- **로깅 및 모니터링**: 알림 시스템 연동, 모니터링 대시보드 필요

---

## 🔴 1. 트랜잭션 일관성 문제

### 1.1 거래 확정 시 에스크로 생성 실패 처리 ✅ 해결 완료

**현재 구현 상태:**
- ✅ 예외를 다시 던져 트랜잭션 롤백 처리 완료
- ✅ `offeredCoins`가 null이거나 0인 경우 거래 확정 불가하도록 예외 발생

**문제 상황 (해결 전):**
```java
// ConversationService.java (line 655-672)
if (offeredCoins != null && offeredCoins > 0) {
    try {
        petCoinEscrowService.createEscrow(...);
    } catch (Exception e) {
        log.error("펫코인 차감 및 에스크로 생성 실패: ...");
        // 코인 차감 실패 시 거래 확정은 진행하되, 로그만 남김
        // 실제 운영 환경에서는 예외를 다시 던져서 거래 확정을 롤백할 수도 있음
    }
}
```

**문제점:**
- 에스크로 생성 실패 시에도 `CareRequest` 상태는 `IN_PROGRESS`로 변경됨
- `CareApplication` 상태는 `ACCEPTED`로 변경됨
- 하지만 코인은 차감되지 않았고 에스크로도 생성되지 않음
- 결과: 거래는 확정되었지만 결제가 처리되지 않은 상태

**영향:**
- 거래 완료 시 제공자에게 코인 지급 불가능
- 데이터 불일치 발생 (상태는 확정인데 에스크로 없음)
- 사용자 신뢰도 하락

**해결 방안:**
1. **트랜잭션 롤백 (권장)**
   ```java
   @Transactional
   public void confirmDeal(...) {
       // ... 상태 변경 ...
       // 에스크로 생성 실패 시 전체 롤백
       petCoinEscrowService.createEscrow(...); // 예외 발생 시 롤백
   }
   ```

2. **보상 트랜잭션**
   - 에스크로 생성 실패 시 상태 변경도 롤백
   - 사용자에게 명확한 에러 메시지 제공

---

### 1.2 거래 완료 시 코인 지급 실패 처리 ✅ 해결 완료

**현재 구현 상태:**
- ✅ 코인 지급 실패 시 예외를 다시 던져 상태 변경 롤백 처리 완료

**문제 상황 (해결 전):**
```java
// CareRequestService.java (line 232-243)
if (oldStatus != CareRequestStatus.COMPLETED && newStatus == CareRequestStatus.COMPLETED) {
    PetCoinEscrow escrow = petCoinEscrowService.findByCareRequest(request);
    if (escrow != null && escrow.getStatus() == EscrowStatus.HOLD) {
        try {
            petCoinEscrowService.releaseToProvider(escrow);
        } catch (Exception e) {
            log.error("거래 완료 시 제공자에게 코인 지급 실패: ...");
            // 코인 지급 실패 시에도 상태 변경은 유지 (운영 환경에서는 롤백 고려)
        }
    }
}
```

**문제점:**
- 코인 지급 실패 시에도 `CareRequest` 상태는 `COMPLETED`로 유지됨
- 제공자는 서비스를 완료했지만 코인을 받지 못함
- 에스크로는 `HOLD` 상태로 남아있음

**영향:**
- 제공자 손실 (서비스 완료했지만 코인 미수령)
- 수동 개입 필요 (관리자가 직접 처리해야 함)
- 분쟁 발생 가능성

**해결 방안:**
1. **상태 변경 롤백 (권장)**
   ```java
   @Transactional
   public CareRequestDTO updateStatus(...) {
       // 상태 변경
       request.setStatus(newStatus);
       
       // 코인 지급 (실패 시 전체 롤백)
       if (newStatus == CareRequestStatus.COMPLETED) {
           petCoinEscrowService.releaseToProvider(escrow); // 예외 발생 시 롤백
       }
       
       return careRequestRepository.save(request);
   }
   ```

2. **재시도 메커니즘**
   - 코인 지급 실패 시 재시도 로직 추가
   - 최대 재시도 횟수 제한

3. **수동 처리 대기열**
   - 실패한 지급 건을 별도 테이블에 기록
   - 관리자가 수동으로 처리할 수 있는 대시보드 제공

---

## 🔴 2. 동시성 문제 (Race Condition)

### 2.1 거래 확정 시 중복 에스크로 생성

**현재 구현 상태:**
- ✅ `ConversationService.confirmCareDeal()`에서 비관적 락 사용 (`findByIdWithLock`)
- ✅ `PetCoinEscrow` 엔티티에 `@JoinColumn(unique = true)` 제약조건 존재
- ✅ 데이터베이스 레벨에서 UNIQUE 제약조건으로 중복 방지됨
- ⚠️ 애플리케이션 레벨 락은 없지만, DB 제약조건으로 충분히 방지됨

**문제 상황:**
```java
// PetCoinEscrowService.java (line 47-51)
escrowRepository.findByCareRequest(careRequest)
    .ifPresent(existing -> {
        throw new IllegalStateException("이미 에스크로가 생성되어 있습니다.");
    });
// ... 에스크로 생성 ...
```

**문제점:**
- `ConversationService`에서는 비관적 락을 사용하지만, `PetCoinEscrowService.createEscrow()` 내부의 `findByCareRequest`는 락 없이 조회
- `findByCareRequest`와 `save` 사이에 다른 트랜잭션이 끼어들 수 있음
- 데이터베이스 제약조건(`unique = true`)이 있지만, 애플리케이션 레벨에서도 추가 보호 필요

**시나리오:**
1. 사용자 A: `findByCareRequest` → 없음
2. 사용자 B: `findByCareRequest` → 없음
3. 사용자 A: `save(escrow)` → 성공
4. 사용자 B: `save(escrow)` → `DataIntegrityViolationException` 발생 (DB 제약조건으로 방지됨)

**영향:**
- DB 제약조건으로 중복 생성은 방지되지만, 예외 처리 필요
- 예외 발생 시 트랜잭션 롤백으로 인한 사용자 경험 저하 가능

**해결 방안:**
1. **비관적 락 추가 (권장)**
   ```java
   // SpringDataJpaPetCoinEscrowRepository.java
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   Optional<PetCoinEscrow> findByCareRequest(CareRequest careRequest);
   ```
   - 애플리케이션 레벨에서 동시성 제어 강화

2. **예외 처리 개선**
   ```java
   try {
       escrowRepository.save(escrow);
   } catch (DataIntegrityViolationException e) {
       // 이미 생성된 경우 조회하여 반환
       return escrowRepository.findByCareRequest(careRequest)
           .orElseThrow(() -> new IllegalStateException("에스크로 생성 실패"));
   }
   ```

3. **낙관적 락 (Optimistic Lock)**
   - `@Version` 필드 추가하여 동시 수정 감지

---

### 2.2 잔액 확인과 차감 사이의 Race Condition

**현재 구현 상태:**
- ✅ `PetCoinService.deductCoins()`에서 비관적 락 사용 (`findByIdForUpdate`)
- ✅ 데이터베이스 제약조건 추가 완료 (`chk_pet_coin_balance`)

**문제 상황:**
```java
// PetCoinService.java (line 90-105)
Users currentUser = usersRepository.findById(user.getIdx())
    .orElseThrow(() -> new RuntimeException("User not found"));

Integer balanceBefore = currentUser.getPetCoinBalance();
if (balanceBefore < amount) {
    throw new IllegalStateException("잔액이 부족합니다.");
}
// ... 잔액 차감 ...
```

**문제점:**
- 잔액 확인과 차감 사이에 다른 트랜잭션이 끼어들 수 있음
- 두 거래가 동시에 잔액을 확인하고 둘 다 통과할 수 있음
- `@Transactional`이 있지만, 같은 트랜잭션 내에서도 다른 트랜잭션이 끼어들 수 있음

**시나리오:**
- 사용자 잔액: 100 코인
- 거래 A: 80 코인 차감 시도
- 거래 B: 50 코인 차감 시도
1. 거래 A: 잔액 확인 → 100 (통과)
2. 거래 B: 잔액 확인 → 100 (통과)
3. 거래 A: 차감 → 잔액 20
4. 거래 B: 차감 → 잔액 -30 (음수!)

**영향:**
- 음수 잔액 발생 가능
- 데이터 무결성 위반

**해결 방안 (구현 완료):**
1. **데이터베이스 제약조건 추가** ✅
   ```sql
   ALTER TABLE users 
   ADD CONSTRAINT chk_pet_coin_balance CHECK (pet_coin_balance >= 0);
   ```
   - 최종 방어선으로 음수 잔액 방지

2. **비관적 락 추가** ✅
   ```java
   // UsersRepository에 추가 완료
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   Optional<Users> findByIdForUpdate(Long idx);
   
   // PetCoinService.deductCoins()에서 사용 완료
   Users currentUser = usersRepository.findByIdForUpdate(user.getIdx())
       .orElseThrow(() -> new RuntimeException("User not found"));
   ```

**구현 완료:**
- ✅ `UsersRepository`에 `findByIdForUpdate()` 메서드 추가
- ✅ `SpringDataJpaUsersRepository`에 비관적 락 쿼리 추가
- ✅ `PetCoinService.deductCoins()`에서 락 사용
- ✅ 데이터베이스 제약조건 추가 완료

---

### 2.3 에스크로 상태 변경 시 Race Condition

**현재 구현 상태:**
- ✅ `PetCoinEscrowService.releaseToProvider()`와 `refundToRequester()`에서 비관적 락 사용 (`findByIdForUpdate`)
- ✅ 동시성 제어 완료

**문제 상황:**
```java
// PetCoinEscrowService.java (line 87-89, 120-122)
if (escrow.getStatus() != EscrowStatus.HOLD) {
    throw new IllegalStateException("HOLD 상태의 에스크로만 지급할 수 있습니다.");
}
// ... 코인 지급 및 상태 변경 ...
```

**문제점:**
- 상태 확인과 변경 사이에 다른 트랜잭션이 끼어들 수 있음
- 동일한 에스크로에 대해 중복 지급/환불 가능
- `@Transactional`이 있지만, 같은 트랜잭션 내에서도 다른 트랜잭션이 끼어들 수 있음

**시나리오:**
- 에스크로 상태: `HOLD`
- 관리자 A: 지급 시도
- 관리자 B: 환불 시도
1. 관리자 A: 상태 확인 → `HOLD` (통과)
2. 관리자 B: 상태 확인 → `HOLD` (통과)
3. 관리자 A: 지급 완료 → 상태 `RELEASED`
4. 관리자 B: 환불 완료 → 상태 `REFUNDED` (중복 처리!)

**영향:**
- 코인 이중 지급 또는 이중 환불
- 데이터 불일치

**해결 방안 (구현 완료):**
1. **비관적 락 추가** ✅
   ```java
   // SpringDataJpaPetCoinEscrowRepository.java에 추가 완료
   @Lock(LockModeType.PESSIMISTIC_WRITE)
   Optional<PetCoinEscrow> findByIdForUpdate(Long idx);
   
   // PetCoinEscrowService에서 사용 완료
   PetCoinEscrow escrow = escrowRepository.findByIdForUpdate(escrow.getIdx())
       .orElseThrow(() -> new RuntimeException("Escrow not found"));
   ```

**구현 완료:**
- ✅ `PetCoinEscrowRepository`에 `findByIdForUpdate()` 메서드 추가
- ✅ `SpringDataJpaPetCoinEscrowRepository`에 비관적 락 쿼리 추가
- ✅ `PetCoinEscrowService.releaseToProvider()`와 `refundToRequester()`에서 락 사용

---

## 🔴 3. 데이터 불일치 문제

### 3.1 offeredCoins가 null이거나 0인 경우

**문제 상황:**
```java
// ConversationService.java (line 655-676)
if (offeredCoins != null && offeredCoins > 0) {
    // 에스크로 생성
} else {
    log.warn("펫코인 가격이 설정되지 않음: ...");
}
```

**문제점:**
- `offeredCoins`가 null이거나 0이면 에스크로가 생성되지 않음
- 하지만 거래는 확정됨 (`IN_PROGRESS` 상태)
- 거래 완료 시 코인 지급 불가능

**영향:**
- 무료 거래로 처리됨
- 제공자가 서비스를 제공했지만 코인을 받지 못함

**해결 방안:**
1. **사전 검증 강화**
   ```java
   // CareRequest 생성/수정 시 검증
   if (offeredCoins == null || offeredCoins <= 0) {
       throw new IllegalArgumentException("코인 가격은 필수이며 0보다 커야 합니다.");
   }
   ```

2. **거래 확정 시 재검증**
   ```java
   if (offeredCoins == null || offeredCoins <= 0) {
       throw new IllegalStateException("거래 확정을 위해서는 코인 가격이 설정되어야 합니다.");
   }
   ```

---

### 3.2 에스크로 없이 COMPLETED 상태로 변경

**문제 상황:**
```java
// CareRequestService.java (line 233-246)
PetCoinEscrow escrow = petCoinEscrowService.findByCareRequest(request);
if (escrow != null && escrow.getStatus() == EscrowStatus.HOLD) {
    // 코인 지급
} else {
    log.warn("에스크로를 찾을 수 없거나 이미 처리됨: ...");
}
```

**문제점:**
- 에스크로가 없는데 `COMPLETED` 상태로 변경 가능
- 제공자가 서비스를 완료했지만 코인을 받지 못함
- 원인:
  - 거래 확정 시 에스크로 생성 실패
  - `offeredCoins`가 null/0이었음
  - 데이터 마이그레이션 이슈

**영향:**
- 제공자 손실
- 데이터 불일치

**해결 방안:**
1. **상태 변경 전 검증**
   ```java
   if (newStatus == CareRequestStatus.COMPLETED) {
       PetCoinEscrow escrow = petCoinEscrowService.findByCareRequest(request);
       if (escrow == null) {
           throw new IllegalStateException("거래 완료를 위해서는 에스크로가 필요합니다.");
       }
       if (escrow.getStatus() != EscrowStatus.HOLD) {
           throw new IllegalStateException("에스크로가 이미 처리되었습니다.");
       }
   }
   ```

2. **경고 로그 및 알림**
   - 에스크로 없이 완료 시도 시 관리자에게 알림
   - 수동 처리 대기열에 추가

---

### 3.3 에스크로 금액과 CareRequest의 offeredCoins 불일치

**문제 상황:**
- 거래 확정 시 `careRequest.getOfferedCoins()`로 에스크로 생성
- 이후 `CareRequest`의 `offeredCoins`가 변경될 수 있음 (현재는 불가능하지만 향후 확장 가능)

**문제점:**
- 에스크로 금액과 실제 거래 금액이 다를 수 있음
- 가격 변경 정책이 추가되면 문제 발생 가능

**해결 방안:**
1. **가격 변경 제한**
   - 거래 확정 후 가격 변경 불가 (현재 정책과 일치)

2. **에스크로 금액 고정**
   - 에스크로 생성 시점의 금액을 고정
   - `CareRequest`의 `offeredCoins` 변경이 에스크로에 영향 없음

---

## 🔴 4. 잔액 부족 문제

### 4.1 거래 확정 시 잔액 부족

**문제 상황:**
```java
// PetCoinService.java (line 96-99)
if (balanceBefore < amount) {
    throw new IllegalStateException("잔액이 부족합니다.");
}
```

**문제점:**
- 잔액 부족 시 `IllegalStateException` 발생
- `ConversationService`에서 catch하여 로그만 남김
- 거래 확정은 실패하지만 사용자에게 명확한 피드백 없음

**영향:**
- 사용자 혼란 (왜 거래 확정이 안 되는지 모름)
- UX 저하

**해결 방안:**
1. **사전 잔액 확인**
   ```java
   // 거래 확정 전 잔액 확인
   Integer balance = petCoinService.getBalance(requester);
   if (balance < offeredCoins) {
       throw new InsufficientBalanceException("잔액이 부족합니다.");
   }
   ```

2. **명확한 에러 메시지**
   - 프론트엔드에서 잔액 부족 시 충전 안내

3. **잔액 부족 알림**
   - 거래 확정 시도 시 잔액 부족 알림 발송

---

## 🔴 5. 에스크로 상태 관리 문제

### 5.1 HOLD 상태가 아닌 에스크로에 대한 지급/환불 시도

**문제 상황:**
```java
// PetCoinEscrowService.java
if (escrow.getStatus() != EscrowStatus.HOLD) {
    throw new IllegalStateException("HOLD 상태의 에스크로만 지급할 수 있습니다.");
}
```

**문제점:**
- 이미 `RELEASED` 또는 `REFUNDED` 상태인 에스크로에 대해 재처리 시도 가능
- 관리자 실수 또는 버그로 인한 중복 처리

**영향:**
- 코인 이중 지급/환불
- 데이터 불일치

**해결 방안:**
1. **상태 검증 강화 (현재 구현됨)**
   - 상태 확인 후 처리

2. **Idempotency 키**
   - 각 지급/환불 요청에 고유 키 부여
   - 동일 키로 재요청 시 무시

3. **처리 이력 기록**
   - 지급/환불 시도 이력을 별도 테이블에 기록
   - 중복 처리 방지

---

## 🔴 6. 거래 취소 시 환불 처리 미구현

### 6.1 환불 로직 미구현 ✅ 해결 완료

**현재 구현 상태:**
- ✅ `PetCoinEscrowService.refundToRequester()` 메서드는 구현되어 있음
- ✅ `CareRequestService.updateStatus()`에서 `CANCELLED` 상태 변경 시 환불 처리 추가 완료
- ✅ 환불 실패 시 상태 변경 롤백 처리 완료

**문제 상황:**
```java
// CareRequestService.updateStatus() (line 206-250)
// CANCELLED 상태로 변경 시 환불 처리 로직 없음
if (oldStatus != CareRequestStatus.COMPLETED && newStatus == CareRequestStatus.COMPLETED) {
    // COMPLETED일 때만 처리
    // CANCELLED일 때는 처리 안 됨!
}
```

**문제점:**
- 거래 취소 시 코인이 환불되지 않음
- 요청자가 코인을 잃음
- 에스크로는 `HOLD` 상태로 남아있음

**영향:**
- 사용자 손실
- 서비스 신뢰도 하락
- 데이터 불일치 (에스크로는 있지만 환불 안 됨)

**해결 방안:**
1. **거래 취소 시 환불 처리 추가 (필수)**
   ```java
   // CareRequestService.updateStatus()에 추가
   if (newStatus == CareRequestStatus.CANCELLED) {
       PetCoinEscrow escrow = petCoinEscrowService.findByCareRequest(request);
       if (escrow != null && escrow.getStatus() == EscrowStatus.HOLD) {
           try {
               petCoinEscrowService.refundToRequester(escrow);
               log.info("거래 취소 시 요청자에게 코인 환불 완료: careRequestIdx={}, escrowIdx={}, amount={}",
                       request.getIdx(), escrow.getIdx(), escrow.getAmount());
           } catch (Exception e) {
               log.error("거래 취소 시 요청자에게 코인 환불 실패: careRequestIdx={}, error={}",
                       request.getIdx(), e.getMessage(), e);
               // 환불 실패 시 상태 변경 롤백 고려
               throw new RuntimeException("환불 처리 중 오류가 발생했습니다.", e);
           }
       } else {
           log.warn("에스크로를 찾을 수 없거나 이미 처리됨: careRequestIdx={}", request.getIdx());
       }
   }
   ```

2. **취소 정책 정의**
   - 언제 환불 가능한지 정책 수립
   - 부분 환불 정책 (서비스 시작 전/후)
   - 취소 수수료 정책 (필요 시)

---

## 🔴 7. 자동 완료 처리 문제

### 7.1 스케줄러를 통한 자동 완료 시 에스크로 처리 ✅ 해결 완료

**현재 구현 상태:**
- ✅ `CareRequestScheduler.updateExpiredCareRequests()`에서 `CareRequestService.updateStatus()` 호출로 변경 완료
- ✅ 스케줄러 실행 시에도 에스크로 처리 정상 작동
- ✅ 수동 완료와 자동 완료의 처리 방식 일치

**문제 상황:**
```java
// CareRequestScheduler.java (line 51-56)
for (CareRequest request : expiredRequests) {
    request.setStatus(CareRequestStatus.COMPLETED);
    updatedCount++;
    // 에스크로 처리 없음!
}
careRequestRepository.saveAll(expiredRequests);
```

**문제점:**
- 자동 완료 시 코인 지급이 안 됨
- 스케줄러와 수동 완료의 처리 방식 불일치
- 제공자가 서비스를 완료했지만 코인을 받지 못할 수 있음

**영향:**
- 제공자 손실
- 데이터 불일치
- 서비스 신뢰도 하락

**해결 방안:**
1. **스케줄러에서 서비스 메서드 호출 (권장)**
   ```java
   // CareRequestScheduler.java
   private final CareRequestService careRequestService;
   
   @Scheduled(cron = "0 0 * * * ?")
   @Transactional
   public void updateExpiredCareRequests() {
       // ...
       for (CareRequest request : expiredRequests) {
           // 서비스 메서드를 통해 상태 변경 (에스크로 처리 포함)
           careRequestService.updateStatus(request.getIdx(), "COMPLETED", null);
       }
   }
   ```
---

## 🔴 8. 트랜잭션 범위 문제

### 8.1 분산 트랜잭션 부재

**문제 상황:**
- `ConversationService`에서 거래 확정 처리
- `PetCoinEscrowService`에서 에스크로 생성
- 각각 별도의 트랜잭션으로 처리됨

**문제점:**
- 중간에 실패 시 부분 커밋 가능
- 데이터 불일치 발생 가능

**해결 방안:**
1. **상위 트랜잭션으로 통합**
   ```java
   @Transactional
   public void confirmDeal(...) {
       // 모든 작업을 하나의 트랜잭션으로 처리
   }
   ```

2. **트랜잭션 전파 설정**
   ```java
   @Transactional(propagation = Propagation.REQUIRED)
   ```

---

## 🔴 9. 로깅 및 모니터링 부족

### 9.1 중요한 거래 로그 부족

**문제점:**
- 코인 거래 실패 시 로그만 남기고 알림 없음
- 관리자가 문제를 즉시 인지하기 어려움

**해결 방안:**
1. **알림 시스템 연동**
   - 코인 지급 실패 시 관리자에게 알림

2. **모니터링 대시보드**
   - 실패한 거래 건수 모니터링
   - 에스크로 상태별 통계

3. **상세 로깅**
   - 모든 코인 거래에 상세 로그 기록
   - 감사(Audit) 목적

---

## 🔴 10. 데이터베이스 제약조건 부족

### 10.1 잔액 음수 방지 제약조건 ✅ 해결 완료

**현재 구현 상태:**
- ✅ 데이터베이스 제약조건 추가 완료
- ✅ 애플리케이션 레벨 검증 + DB 제약조건으로 이중 보호

**구현 완료:**
```sql
ALTER TABLE users 
ADD CONSTRAINT chk_pet_coin_balance CHECK (pet_coin_balance >= 0);
```

---

### 10.2 에스크로 중복 방지 제약조건 ✅ 이미 존재

**현재 구현 상태:**
- ✅ `PetCoinEscrow` 엔티티에 `@JoinColumn(unique = true)` 제약조건 존재
- ✅ 마이그레이션 파일에 `UNIQUE KEY uk_care_request (care_request_idx)` 존재
- ✅ 데이터베이스 레벨에서 중복 방지됨

**참고:**
- JPA의 `unique = true`와 마이그레이션의 `UNIQUE KEY`가 동일한 제약조건을 생성
- 추가 SQL 실행 시 중복 인덱스 경고 발생하지만 기능상 문제 없음
- 별도 추가 작업 불필요

---

## 📊 우선순위별 정리

### ✅ Critical 우선순위 - 해결 완료
1. **트랜잭션 일관성 문제** (1.1, 1.2) ✅
   - ✅ 에스크로 생성 실패 시 롤백 처리 완료
   - ✅ 코인 지급 실패 시 롤백 처리 완료
2. **동시성 문제** (2.2, 2.3) ✅
   - ✅ 잔액 확인과 차감 사이의 Race Condition 해결 (비관적 락 추가)
   - ✅ 에스크로 상태 변경 시 Race Condition 해결 (비관적 락 추가)
3. **거래 취소 시 환불 처리** (6.1) ✅
   - ✅ CANCELLED 상태 변경 시 환불 로직 추가 완료
4. **자동 완료 처리 문제** (7.1) ✅
   - ✅ 스케줄러에서 서비스 메서드 호출로 변경 완료
5. **데이터베이스 제약조건** (10.1, 10.2) ✅
   - ✅ 잔액 음수 방지 제약조건 추가 완료
   - ✅ 에스크로 중복 방지 제약조건 이미 존재

### 🟡 High (단기간 내 해결 필요)
6. **동시성 문제** (2.1)
   - 에스크로 중복 생성 방지 (DB 제약조건으로 방지되지만, 애플리케이션 레벨 락 추가 고려)
7. **데이터 불일치 문제** (3.1, 3.2)
   - offeredCoins 검증 강화
   - 에스크로 없이 COMPLETED 상태 변경 방지
8. **잔액 부족 문제** (4.1)
   - 사용자 경험 개선 (명확한 에러 메시지)

### 🟢 Medium (중장기 개선)
9. **에스크로 상태 관리 문제** (5.1)
   - Idempotency 키 추가
   - 처리 이력 기록
10. **로깅 및 모니터링 부족** (9.1)
    - 알림 시스템 연동
    - 모니터링 대시보드

---

## 📝 체크리스트

### 개발 단계
- [x] **트랜잭션 롤백 로직 구현** ✅
  - [x] ConversationService: 에스크로 생성 실패 시 예외 재던지기
  - [x] CareRequestService: 코인 지급 실패 시 롤백 처리
- [x] **동시성 제어 (락) 구현** ✅
  - [x] PetCoinService: Users 조회 시 비관적 락 추가 (`findByIdForUpdate`)
  - [x] PetCoinEscrowService: 에스크로 조회 시 비관적 락 추가 (`findByIdForUpdate`)
  - [x] PetCoinEscrowService: 상태 변경 시 비관적 락 추가 (`releaseToProvider`, `refundToRequester`)
- [x] **거래 취소 시 환불 처리 구현** ✅
  - [x] CareRequestService.updateStatus()에 CANCELLED 처리 로직 추가
- [x] **자동 완료 처리 수정** ✅
  - [x] CareRequestScheduler에서 CareRequestService.updateStatus() 호출로 변경
- [x] **데이터베이스 제약조건 추가** ✅
  - [x] users 테이블에 잔액 음수 방지 제약조건 추가 (`chk_pet_coin_balance`)
  - [x] pet_coin_escrow 테이블에 에스크로 중복 방지 제약조건 확인 (이미 존재)
- [ ] **사전 검증 강화**
  - [ ] CareRequest 생성 시 offeredCoins 검증
  - [x] 거래 확정 시 offeredCoins 재검증 (예외 발생)

### 테스트 단계
- [ ] **동시성 테스트**
  - [ ] 여러 사용자가 동시에 거래 확정 시도
  - [ ] 동일 에스크로에 동시 지급/환불 시도
  - [ ] 동일 사용자에 동시 코인 차감 시도
- [ ] **잔액 부족 시나리오 테스트**
  - [ ] 잔액 부족 시 거래 확정 실패 확인
  - [ ] 명확한 에러 메시지 확인
- [ ] **에스크로 생성 실패 시나리오 테스트**
  - [ ] 에스크로 생성 실패 시 거래 확정 롤백 확인
- [ ] **코인 지급 실패 시나리오 테스트**
  - [ ] 코인 지급 실패 시 상태 변경 롤백 확인
- [ ] **거래 취소 시 환불 테스트**
  - [ ] CANCELLED 상태 변경 시 환불 확인
  - [ ] 에스크로 상태가 REFUNDED로 변경 확인
- [ ] **자동 완료 처리 테스트**
  - [ ] 스케줄러 실행 시 에스크로 처리 확인

### 모니터링
- [ ] 코인 거래 실패 알림 설정
- [ ] 에스크로 상태 모니터링 대시보드
- [ ] 거래 실패율 추적
- [ ] 환불 처리 실패 알림

---
