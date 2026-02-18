# Payment 백엔드 성능 최적화 리팩토링

## 개요

Payment(PetCoin) 도메인의 백엔드 코드 분석을 통해 발견된 성능 이슈 및 리팩토링 포인트를 정리합니다.

**대상 도메인**:
- **펫코인**: PetCoinTransaction, PetCoinEscrow, PetCoinService, PetCoinEscrowService
- **연동**: CareRequestService, ConversationService (에스크로 생성/지급/환불)

**참고 문서**: [Board 백엔드 성능 최적화](../board/board-backend-performance-optimization.md) 형식 준수

---

## 아키텍처 요약

```
PetCoinController        → PetCoinService, PetCoinTransactionRepository, PetCoinTransactionConverter
AdminPaymentController   → PetCoinService, PetCoinTransactionRepository
PetCoinEscrowService     → PetCoinService, PetCoinEscrowRepository
CareRequestService       → PetCoinEscrowService (에스크로 지급/환불)
ConversationService      → PetCoinEscrowService (에스크로 생성)
```

---

## 🔴 Critical (긴급) - 리팩토링

### 1. PetCoinController - 거래 내역 메모리 페이징 ✅

**파일**: `PetCoinController.java` (Lines 60-82)

**기존 문제**:
- `GET /api/payment/transactions`: `findByUserOrderByCreatedAtDesc(user)` **전체 조회** 후 `subList`로 메모리 페이징
- 거래 내역 1만 건 시 → 1만 건 전부 로드 후 20건만 반환
- AdminPaymentController는 이미 `findByUserOrderByCreatedAtDesc(user, pageable)` DB 페이징 사용

```java
// 기존 코드 - 메모리 페이징
List<PetCoinTransaction> transactions = transactionRepository
        .findByUserOrderByCreatedAtDesc(user);  // 전체 로드!
int start = page * size;
int end = Math.min(start + size, transactions.size());
List<PetCoinTransaction> pagedTransactions = transactions.subList(...);
```

**해결 방안** (적용됨):
1. `PetCoinTransactionRepository`에 `findByUserOrderByCreatedAtDesc(Users user, Pageable pageable)` 추가
2. `JpaPetCoinTransactionAdapter`에서 SpringData JPA 메서드 위임
3. PetCoinController에서 `@PageableDefault(size = 20) Pageable` 사용, DB 페이징으로 변경

```java
// PetCoinTransactionRepository
Page<PetCoinTransaction> findByUserOrderByCreatedAtDesc(Users user, Pageable pageable);

// PetCoinController
@GetMapping("/transactions")
public ResponseEntity<Page<PetCoinTransactionDTO>> getMyTransactions(
        @PageableDefault(size = 20) Pageable pageable) {
    // ...
    Page<PetCoinTransaction> transactions = transactionRepository
            .findByUserOrderByCreatedAtDesc(user, pageable);
    return ResponseEntity.ok(transactions.map(transactionConverter::toDTO));
}
```

**API 응답 형식 변경 (List → Page)**:
- **변경 전**: `[{...}, {...}]` (배열)
- **변경 후**: `{ content: [...], totalElements, totalPages, size, number, ... }` (Spring Page)
- **프론트엔드**: `paymentApi.getTransactions(page, size)` 호출 시 `response.content`로 목록 접근, `response.totalElements`, `response.totalPages`로 페이지네이션 처리. `paymentApi.js`에 Page 응답 형식 주석 추가 완료.

---

### 2. PetCoinService - chargeCoins/payoutCoins/refundCoins Race Condition ✅

**파일**: `PetCoinService.java` (Lines 36-223)

**상세 문서**: [PetCoinService Race Condition 상세 분석](./petcoin-service-race-condition.md)

**기존 문제**: `chargeCoins`, `payoutCoins`, `refundCoins`는 `findById` 사용 → 동시 요청 시 Lost Update + Deadlock, 잔액 불일치.

**해결 완료**: `chargeCoins`, `payoutCoins`, `refundCoins`에서 `findById` → `findByIdForUpdate`로 변경. `SELECT ... FOR UPDATE`로 해당 User 행 락 유지 → 순차 처리. `PetCoinServiceRaceConditionTest` 추가.

---

### 3. PetCoinTransactionConverter - N+1 쿼리 ✅

**파일**: `PetCoinTransactionConverter.java` (Line 25), `PetCoinController.java`, `AdminPaymentController.java`

**기존 문제**: `toDTO()`에서 `transaction.getUser().getIdx()` 접근 시 Lazy Loading → 거래 N건 조회 시 1 + N 쿼리.

**해결 완료**: `SpringDataJpaPetCoinTransactionRepository.findByUserOrderByCreatedAtDesc`에 `@EntityGraph(attributePaths = "user")` 추가. JOIN FETCH로 User를 한 번에 로드 → N+1 제거.

---

## 🟠 High Priority - 리팩토링

### 4. PetCoinController - User 중복 조회 ✅

**파일**: `PetCoinController.java` (Lines 46-127)

**기존 문제**: `getCurrentUserId()` 1회 + 각 메서드에서 `findById` 1회 → 동일 요청 내 User 2회 조회.

**해결 완료**: `getCurrentUserId()` → `getCurrentUser()`로 변경. User 엔티티를 1회만 조회하고 `getMyBalance()`, `getMyTransactions()`, `chargeCoins()`에 전달. 요청당 User 조회 2회 → 1회로 감소.

---

### 5. AdminPaymentController - Repository 패턴 일관성 ✅

**파일**: `AdminPaymentController.java` (Lines 14, 35, 83-85)

**기존 문제**: `SpringDataJpaPetCoinTransactionRepository` 직접 주입 → PetCoinController와 Repository 패턴 불일치.

**해결 완료**: AdminPaymentController에서 `PetCoinTransactionRepository` (도메인 인터페이스) 사용으로 변경. JPA 인터페이스는 Adapter 내부에서만 사용.

**N+1과의 차이**: 이 항목은 **쿼리 성능(N+1)** 이 아니라 **아키텍처 일관성** 이슈. N+1은 Lazy Loading으로 인한 1+N 쿼리 발생 → `@EntityGraph`, JOIN FETCH로 해결. 본 항목은 Controller가 JPA 구현체에 직접 의존하는 구조 문제 → 도메인 인터페이스로 추상화.

---

## 🟡 Medium Priority

### 6. PetCoinService.getBalance - User 재조회 ✅

**파일**: `PetCoinService.java` (Lines 228-233)

**기존 문제**: `getBalance(Users user)`에서 `findById` 재조회 → Controller에서 user 전달해도 2회 쿼리.

**해결 완료**: `user.getPetCoinBalance()` 직접 반환. Controller의 `getCurrentUser()`로 조회한 user 전달 시 추가 쿼리 없음.

---

### 7. CareRequestService - createCareRequest 잔액 검증 TOCTOU

**파일**: `CareRequestService.java` (Lines 110-112)

**현재 문제**:
- `user.getPetCoinBalance() < dto.getOfferedCoins()` 검증
- createCareRequest 시점과 실제 에스크로 생성(createEscrow) 시점 사이에 **시간차**
- 그 사이 다른 거래로 잔액 차감 가능 → createEscrow 시 deductCoins에서 잔액 부족 예외
- createEscrow의 deductCoins는 `findByIdForUpdate` 사용 → Race Condition 방지됨
- createCareRequest의 검증은 "사전 체크"일 뿐, 실제 차감은 createEscrow에서 처리

**개선 포인트**:
- 현재 구조 유지해도 무방 (createEscrow에서 최종 검증)
- createCareRequest 시점 검증은 UX 목적 (사전 안내)
- **선택**: createCareRequest에서 `findByIdForUpdate`로 잔액 조회 후 검증 → 더 정확하나, 에스크로 생성 전까지 락 유지 불가. 실질적 개선 어려움.

---

### 8. Exception 처리 - RuntimeException 일원화

**파일**: `PetCoinController`, `AdminPaymentController`, `PetCoinService`

**현재 문제**:
- `RuntimeException("User not found")`, `IllegalArgumentException`, `IllegalStateException` 혼용
- 도메인별 예외 (UserNotFoundException 등) 없음

**개선 포인트**:
- `UserNotFoundException`, `InsufficientBalanceException` 등 도메인 예외 도입

---

## 🟢 Low Priority

### 9. PetCoinEscrow - Lazy Loading 위험

**파일**: `PetCoinEscrow` 엔티티, `PetCoinEscrowService`, `CareRequestService`

**현재 문제**:
- `escrow.getCareRequest().getIdx()`, `escrow.getProvider()`, `escrow.getRequester()` 등 Lazy 필드 접근
- `releaseToProvider`, `refundToRequester` 호출 전 escrow 조회 시 fetch 전략 확인 필요
- `findByIdForUpdate`는 escrow만 조회, careRequest/provider/requester는 Lazy → 접근 시 추가 쿼리

**개선 포인트**:
- `findByIdForUpdate`에 `@EntityGraph(attributePaths = {"careRequest", "provider", "requester"})` 추가 시 JOIN FETCH로 1회 조회

---

### 10. 데이터베이스 인덱스

**Entity 인덱스 검토**:
- `pet_coin_transaction`: `user_idx`, `created_at`, `related_type`, `related_idx`
- `pet_coin_escrow`: `care_request_idx` (unique), `requester_idx`, `provider_idx`, `status`

---

## 체크리스트

- [x] PetCoinController getMyTransactions → DB 페이징 (PetCoinTransactionRepository에 Page 메서드 추가)
- [ ] PetCoinService chargeCoins/payoutCoins/refundCoins → findByIdForUpdate 적용
- [x] PetCoinTransactionConverter N+1 → @EntityGraph 또는 JOIN FETCH
- [x] PetCoinController getCurrentUser 통합, User 중복 조회 제거
- [x] AdminPaymentController → PetCoinTransactionRepository 사용 (SpringData JPA 직접 제거)
- [x] PetCoinService getBalance - user 전달 시 재조회 생략 검토
- [ ] 도메인 예외 클래스 도입 (선택)

---

## 예상 효과

| 항목 | Before | After |
|------|--------|-------|
| getMyTransactions (1000건) | 1000건 전체 로드 + N+1 | DB 페이징 20건 + 1 쿼리 (EntityGraph) |
| 동시 충전/지급/환불 | 잔액 불일치 위험 | 비관적 락으로 일관성 보장 |
| 거래 내역 N건 변환 | N+1 쿼리 | 1 쿼리 (JOIN FETCH) |
| PetCoinController User 조회 | 요청당 2~3회 | 1회 |

---

## 관련 문서

- [Board 백엔드 성능 최적화](../board/board-backend-performance-optimization.md)
- [User 백엔드 성능 최적화](../user/user-backend-performance-optimization.md)
- [Payment DTO Record 리팩토링](../recordType/payment/dto-record-refactoring.md)
- [PetCoinService Race Condition 상세 분석](./petcoin-service-race-condition.md)
