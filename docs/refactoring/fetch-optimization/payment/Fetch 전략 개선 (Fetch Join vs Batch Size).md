# Payment 도메인 Fetch 전략 개선

> **규칙**: 단건 상세 → Fetch Join / 페이징 목록 → Batch Size (또는 EntityGraph)

---

## 요약

| 구분 | 대상 | 전략 | 상태 |
|------|------|------|------|
| PetCoinTransaction 페이징 | `getMyTransactions`, `getUserTransactions` | EntityGraph | ✅ 이미 적용됨 |
| PetCoinTransaction 단건 | `getTransactionDetail` | Fetch Join | ✅ 적용됨 |
| PetCoinEscrow 단건 | `getTransactionDetail` 내 escrow 조회 | Fetch Join | ✅ 적용됨 |
| PetCoinEscrow (release/refund) | `releaseToProvider`, `refundToRequester` | findByIdForUpdate | 단건 lazy 수준, 우선순위 낮음 |

---

## 1. PetCoinTransaction (펫코인 거래)

### 1.1 페이징 목록 — ✅ 이미 적용됨

**대상**
- `getMyTransactions` — `GET /api/payment/transactions`
- `getUserTransactions` — `GET /api/admin/payment/transactions/{userId}`

**현재 상태**
- `findByUserOrderByCreatedAtDesc(user, pageable)` — `@EntityGraph(attributePaths = "user")` 적용 ✓
- `PetCoinTransactionConverter.toDTO()`에서 `transaction.getUser().getIdx()` 접근 시 추가 쿼리 없음

### 1.2 단건 상세 — ✅ 적용됨

**적용 내용**
- `findByIdWithUser(Long idx)` 추가 및 `getTransactionDetail`에서 사용
- transaction + user 2회 → 1회 쿼리로 수렴

### 1.3 비페이징 목록 (테스트용)

- `findByUserOrderByCreatedAtDesc(Users user)` — 테스트에서만 사용, user fetch 없음
- 우선순위 낮음 (테스트 전용)

---

## 2. PetCoinEscrow (펫코인 에스크로)

### 2.1 getTransactionDetail 내 escrow 조회 — ✅ 적용됨

**적용 내용**
- `findByCareRequestIdxWithDetails(Long careRequestIdx)` 추가 (requester, provider, careRequest Fetch Join)
- careRequestRepository.findById 제거 → escrow.getCareRequest().getTitle() 사용
- CareRequestRepository 의존성 제거

### 2.2 releaseToProvider, refundToRequester

- `findByIdForUpdate` 사용 — 비관적 락
- `escrow.getProvider()`, `escrow.getRequester()`, `escrow.getCareRequest()` 접근
- **findByIdForUpdate에 Fetch Join 추가 시 락과 충돌 가능** → 별도 `findByIdForUpdateWithDetails` 검토
- 현재는 단건 조회라 lazy 2~3회 수준 → 우선순위 낮음

---

## 3. 적용 완료 내역

| 파일 | 적용 내용 |
|------|----------|
| `SpringDataJpaPetCoinTransactionRepository` | `findByIdWithUser(Long idx)` 추가 |
| `PetCoinTransactionRepository`, `JpaPetCoinTransactionAdapter` | `findByIdWithUser` 시그니처/구현 |
| `PetCoinService.getTransactionDetail` | `findByIdWithUser`, `findByCareRequestIdxWithDetails` 사용 |
| `SpringDataJpaPetCoinEscrowRepository` | `findByCareRequestIdxWithDetails(Long careRequestIdx)` 추가 |
| `PetCoinEscrowRepository`, `JpaPetCoinEscrowAdapter` | `findByCareRequestIdxWithDetails` 시그니처/구현 |
| `PetCoinService` | CareRequestRepository 의존성 제거 |

---

## 4. 적용 불필요 구간

| 구간 | 이유 |
|------|------|
| chargeCoins, deductCoins 등 | save 후 반환, user 이미 로드됨 |
| findByRequesterOrProvider | 현재 미사용 (정의만 존재) |
| findByIdForUpdate | 락 쿼리, Fetch Join 혼합 시 주의 |
