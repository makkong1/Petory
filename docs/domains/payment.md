# Payment 도메인

> 기준: 현재 코드를 단일 진실로 본다. 이 문서는 펫코인 잔액, 거래 내역, 케어 거래 에스크로, 관리자 지급을 다룬다. 케어 요청/매칭 자체는 Care 도메인에서 다룬다.

## 1. 범위

Payment 도메인은 Petory 내부 결제 단위인 펫코인의 잔액 변경과 거래 기록을 담당한다. 현재 충전은 실제 PG 연동이 아니라 개발/운영 테스트용 시뮬레이션이다.

포함 범위:

- 현재 사용자 펫코인 잔액 조회
- 현재 사용자 거래 내역 페이징 조회
- 거래 상세 조회
- 사용자 테스트 충전
- 관리자 사용자 코인 지급
- 케어 거래 확정 시 요청자 코인 차감
- 케어 거래 완료 시 제공자 지급
- 케어 거래 취소 시 요청자 환불
- 에스크로 상태 관리
- 잔액 변경과 거래 내역 기록
- 비관적 락 기반 동시성 제어

비범위:

- 실제 PG 결제 승인/검증/취소
- 카드, 계좌, 영수증, 정산 대행
- 케어 요청 생성/상태 정책
- 채팅 거래 확정 UI/메시지
- 환불 정책 운영 심사

## 2. 주요 코드

| 구분 | 주요 파일 |
|---|---|
| 사용자 API | `backend/main/java/com/linkup/Petory/domain/payment/controller/PetCoinController.java` |
| 관리자 API | `backend/main/java/com/linkup/Petory/domain/payment/controller/AdminPaymentController.java` |
| 코인 서비스 | `backend/main/java/com/linkup/Petory/domain/payment/service/PetCoinService.java` |
| 에스크로 서비스 | `backend/main/java/com/linkup/Petory/domain/payment/service/PetCoinEscrowService.java` |
| 거래 엔티티 | `backend/main/java/com/linkup/Petory/domain/payment/entity/PetCoinTransaction.java` |
| 에스크로 엔티티 | `backend/main/java/com/linkup/Petory/domain/payment/entity/PetCoinEscrow.java` |
| 거래 repository | `backend/main/java/com/linkup/Petory/domain/payment/repository/SpringDataJpaPetCoinTransactionRepository.java` |
| 에스크로 repository | `backend/main/java/com/linkup/Petory/domain/payment/repository/SpringDataJpaPetCoinEscrowRepository.java` |
| 프론트 API | `frontend/src/api/paymentApi.js` |
| 충전 화면 | `frontend/src/components/Payment/PetCoinChargePage.js` |
| 거래 목록 modal | `frontend/src/components/Payment/PetCoinTransactionListModal.js` |
| 거래 상세 modal | `frontend/src/components/Payment/PetCoinTransactionDetailModal.js` |

## 3. 핵심 엔티티

### Users.petCoinBalance

사용자의 현재 펫코인 잔액은 `Users.petCoinBalance`에 저장된다.

잔액 변경은 `Users.creditCoins(amount)` 또는 `Users.debitCoins(amount)`를 통해 수행한다. 서비스 레이어에서는 잔액 변경 전에 `UsersRepository.findByIdForUpdate()`로 사용자를 비관적 락 조회한다.

### PetCoinTransaction

펫코인 거래 내역이다. 모든 잔액 변경은 거래 내역을 남긴다.

| 필드 | 의미 |
|---|---|
| `user` | 거래 대상 사용자 |
| `transactionType` | `CHARGE`, `DEDUCT`, `PAYOUT`, `REFUND` |
| `amount` | 거래 코인 수 |
| `balanceBefore` | 거래 전 잔액 |
| `balanceAfter` | 거래 후 잔액 |
| `relatedType` | 관련 도메인 타입, 예: `CARE_REQUEST` |
| `relatedIdx` | 관련 엔티티 ID |
| `description` | 거래 설명 |
| `status` | `PENDING`, `COMPLETED`, `FAILED`, `CANCELLED` |

현재 정상 거래는 `COMPLETED`로 기록된다.

### PetCoinEscrow

케어 거래 확정 시 요청자 코인을 임시 보관하는 엔티티다.

| 필드 | 의미 |
|---|---|
| `careRequest` | 연결된 케어 요청, unique |
| `careApplication` | 확정된 케어 매칭 |
| `requester` | 코인을 지불한 요청자 |
| `provider` | 지급받을 제공자 |
| `amount` | 에스크로 금액 |
| `status` | `HOLD`, `RELEASED`, `REFUNDED` |
| `releasedAt` | 지급 시각 |
| `refundedAt` | 환불 시각 |

에스크로 상태 변경:

- `release()`: `HOLD -> RELEASED`
- `refund()`: `HOLD -> REFUNDED`

`care_request_idx`는 unique라 하나의 케어 요청에는 하나의 에스크로만 연결된다.

## 4. 사용자 Payment API

### `/api/payment`

| API | 인증 | 설명 |
|---|---|---|
| `GET /api/payment/balance` | 인증 필요 | 현재 사용자 잔액 조회 |
| `GET /api/payment/transactions?page&size` | 인증 필요 | 현재 사용자 거래 내역 DB 페이징 조회 |
| `GET /api/payment/transactions/{id}` | 인증 필요 | 거래 상세 조회 |
| `POST /api/payment/charge` | 인증 필요 | 현재 사용자 코인 충전 |

컨트롤러는 `SecurityContextHolder`의 authentication name으로 현재 사용자를 조회한다.

주의:

- `POST /api/payment/charge`는 실제 PG 결제가 아니라 시뮬레이션 충전이다.
- 실제 운영 결제에서는 이 endpoint를 PG 승인 검증 경로로 대체해야 한다.

## 5. 관리자 Payment API

### `/api/admin/payment`

`ADMIN`, `MASTER` 접근 가능.

| API | 설명 |
|---|---|
| `POST /api/admin/payment/charge` | 특정 사용자에게 코인 지급 |
| `GET /api/admin/payment/balance/{userId}` | 특정 사용자 잔액 조회 |
| `GET /api/admin/payment/transactions/{userId}?page&size` | 특정 사용자 거래 내역 페이징 조회 |

관리자 지급은 `PetCoinChargeRequest.userId`가 필수다.

## 6. 코인 거래 서비스

### chargeCoins

사용자 충전 또는 관리자 지급에 사용된다.

흐름:

1. amount가 0 이하이면 거절
2. 사용자를 `findByIdForUpdate()`로 비관적 락 조회
3. `creditCoins(amount)`로 잔액 증가
4. 변경된 사용자를 저장
5. `TransactionType.CHARGE` 거래 내역 저장

### deductCoins

케어 거래 확정 시 요청자 코인을 에스크로로 이동하기 위해 차감한다.

흐름:

1. amount가 0 이하이면 거절
2. 사용자를 `findByIdForUpdate()`로 비관적 락 조회
3. 잔액 부족이면 `InsufficientBalanceException`
4. `debitCoins(amount)`로 잔액 차감
5. 변경된 사용자를 저장
6. `TransactionType.DEDUCT` 거래 내역 저장

### payoutCoins

케어 완료 시 제공자에게 지급한다.

흐름:

1. amount가 0 이하이면 거절
2. 제공자를 `findByIdForUpdate()`로 비관적 락 조회
3. 잔액 증가
4. `TransactionType.PAYOUT` 거래 내역 저장

### refundCoins

케어 취소 시 요청자에게 환불한다.

흐름:

1. amount가 0 이하이면 거절
2. 요청자를 `findByIdForUpdate()`로 비관적 락 조회
3. 잔액 증가
4. `TransactionType.REFUND` 거래 내역 저장

## 7. 에스크로 서비스

### createEscrow

`ConversationService.confirmCareDeal()`에서 양쪽 거래 확정 후 호출된다.

흐름:

1. amount가 0 이하이면 거절
2. 같은 CareRequest에 이미 에스크로가 있으면 `PaymentConflictException`
3. `deductCoins(requester, amount, "CARE_REQUEST", careRequest.idx, ...)` 호출
4. `PetCoinEscrow(status=HOLD)` 생성

중요:

- 이 메서드 자체는 `@Transactional`이다.
- 하지만 호출자인 `ConversationService.confirmCareDeal()`은 이 호출을 try/catch로 감싸고 예외를 다시 던지지 않는다.
- 그래서 에스크로 생성 실패 시에도 Care 매칭 상태 전이가 커밋될 수 있다.

### releaseToProvider

`CareRequestService.updateStatus(..., COMPLETED, ...)`에서 호출된다.

흐름:

1. 전달받은 escrow id로 `findByIdForUpdate()` 비관적 락 조회
2. `escrow.release()`로 `HOLD -> RELEASED`
3. `payoutCoins(provider, amount, "CARE_REQUEST", careRequest.idx, ...)` 호출
4. `StatisticsService.recordPayment(amount)` 호출
5. 에스크로 저장

`escrow.release()`는 `HOLD`가 아니면 `IllegalStateException`을 던진다.

### refundToRequester

`CareRequestService.updateStatus(..., CANCELLED, ...)`에서 호출된다.

흐름:

1. 전달받은 escrow id로 `findByIdForUpdate()` 비관적 락 조회
2. `escrow.refund()`로 `HOLD -> REFUNDED`
3. `refundCoins(requester, amount, "CARE_REQUEST", careRequest.idx, ...)` 호출
4. 에스크로 저장

`escrow.refund()`는 `HOLD`가 아니면 `PaymentConflictException.holdStatusRequiredForRefund()`를 던진다.

## 8. Care 연동 흐름

### 거래 확정

진입점:

- `ConversationService.confirmCareDeal()`

흐름:

1. Chat 도메인이 케어 채팅방을 비관적 락으로 조회
2. 양쪽 참여자의 거래 확정 여부 확인
3. `CareApplication` 생성 또는 `ACCEPTED` 처리
4. `CareRequest`를 `IN_PROGRESS`로 변경
5. Payment의 `createEscrow()` 호출
6. 요청자 코인 `DEDUCT` 거래 내역과 `HOLD` 에스크로 생성

현재 리스크:

- `createEscrow()` 실패가 거래 확정 롤백으로 이어지지 않는다.
- 코인 없이 `IN_PROGRESS`가 될 수 있어 운영 정책에 따라 롤백 전파가 필요할 수 있다.

### 거래 완료

진입점:

- `CareRequestService.updateStatus(..., "COMPLETED", ...)`
- `CareRequestScheduler` 자동 완료도 같은 서비스 메서드를 호출한다.

흐름:

1. Care가 상태를 `COMPLETED`로 변경
2. Care가 `findByCareRequest()`로 에스크로 조회
3. 에스크로가 있고 `HOLD`이면 `releaseToProvider()` 호출
4. Payment가 escrow id 비관적 락을 잡고 제공자에게 지급

### 거래 취소

진입점:

- `CareRequestService.updateStatus(..., "CANCELLED", ...)`

흐름:

1. Care가 상태를 `CANCELLED`로 변경
2. Care가 `findByCareRequest()`로 에스크로 조회
3. 에스크로가 있고 `HOLD`이면 `refundToRequester()` 호출
4. Payment가 escrow id 비관적 락을 잡고 요청자에게 환불

## 9. 거래 상세 조회

`PetCoinService.getTransactionDetail()`은 본인 거래만 조회할 수 있다.

흐름:

1. 거래를 `findByIdWithUser()`로 조회
2. 거래 user와 현재 사용자가 다르면 거절
3. 기본 거래 상세 DTO 생성
4. `relatedType == "CARE_REQUEST"`이면 에스크로를 `findByCareRequestIdxWithDetails()`로 조회
5. 거래 타입에 따라 상대방을 설정
   - `DEDUCT`, `REFUND`: 상대방은 provider
   - `PAYOUT`: 상대방은 requester
6. 케어 요청 제목을 `relatedTitle`로 설정

거래 목록은 `findByUserOrderByCreatedAtDesc(user, pageable)`로 DB 페이징한다.

## 10. 동시성 제어

사용자 잔액 변경:

- `chargeCoins`
- `deductCoins`
- `payoutCoins`
- `refundCoins`

위 메서드 모두 `UsersRepository.findByIdForUpdate()`로 사용자를 비관적 락 조회한 뒤 잔액을 변경한다.

에스크로 상태 변경:

- `releaseToProvider`
- `refundToRequester`

두 메서드는 `PetCoinEscrowRepository.findByIdForUpdate()`로 에스크로 row를 비관적 락 조회한다.

주의:

- `findByCareRequestForUpdate()` repository 메서드는 존재하지만 현재 `CareRequestService.updateStatus()` 경로에서는 사용하지 않는다.
- 현재 완료/취소 경로는 먼저 `findByCareRequest()`로 조회하고, 실제 상태 변경 시 `releaseToProvider()` 또는 `refundToRequester()` 내부에서 escrow id 락을 잡는다.

## 11. 도메인 간 연결

Care:

- 거래 확정 시 Payment 에스크로 생성 호출.
- 완료/취소 상태 전이 시 지급/환불 호출.

Chat:

- 양쪽 거래 확정 후 `createEscrow()`를 호출하는 주체.

User:

- 현재 잔액은 `Users.petCoinBalance`.
- 모든 잔액 변경은 사용자 row 비관적 락 후 처리.

Statistics:

- 에스크로 지급 완료 시 `recordPayment(amount)` 호출.

Admin:

- 관리자 지급, 사용자 잔액/거래 내역 조회.

## 12. 한계와 개선

- 실제 PG 연동이 없다. 현재 충전은 시뮬레이션이다.
- 거래 확정 중 에스크로 생성 실패가 Chat/Care 매칭 롤백으로 이어지지 않는다.
- `createEscrow()`의 중복 에스크로 검사는 일반 조회 후 저장이다. DB unique 제약이 최종 방어선이다.
- `release()`는 `IllegalStateException`, `refund()`는 `PaymentConflictException`을 사용해 예외 타입이 일관되지 않다.
- `TransactionStatus`에는 `PENDING`, `FAILED`, `CANCELLED`가 있지만 현재 정상 서비스 흐름은 대부분 `COMPLETED`만 기록한다.
- 사용자 충전 endpoint가 열려 있으므로 운영 환경에서는 PG 검증 또는 관리자 전용 정책으로 바꿔야 한다.
- 금액 타입은 정수 코인이다. 실제 원화/PG 금액과 연결하려면 별도 금액 단위 정책이 필요하다.

## 13. 관련 문서

- [펫코인 결제 아키텍처](../architecture/payment/펫코인 결제 아키텍처.md)
- [Care 도메인](care.md)
- [펫케어 코인 관련 흐름](../architecture/care/펫케어 코인 관련 흐름.md)
- [PetCoinService Race Condition 리팩토링](../refactoring/payment/petcoin-service-race-condition.md)
- [Payment 백엔드 성능 최적화](../refactoring/payment/payment-backend-performance-optimization.md)
- [Payment 트러블슈팅 분석](../troubleshooting/payment/payment-troubleshooting-analysis.md)
- [Payment 상세 트러블슈팅](../troubleshooting/payment/payment-troubleshooting-detailed.md)
