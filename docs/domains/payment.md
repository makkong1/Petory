# Payment 도메인 - 펫코인 결제 시스템

## 1. 도메인 개요

### 1.1 역할
펫코인(PetCoin)은 펫케어 거래를 위한 내부 결제 단위입니다. 실제 결제 시스템(PG) 연동 없이 시뮬레이션으로 구현되어 있으며, 실제 운영 시에는 충전 단계만 PG로 대체하면 되도록 설계되었습니다.

### 1.2 핵심 원칙
- **외부 PG 직접 연동 없음**: 개발 단계에서는 실제 결제 없이 시뮬레이션
- **에스크로 시스템**: 거래 확정 시 코인을 임시 보관하여 거래 안전성 확보
- **완전한 거래 내역 기록**: 모든 코인 거래가 `pet_coin_transaction` 테이블에 기록됨
- **트랜잭션 일관성**: 모든 상태 변경과 결제 처리가 원자적으로 처리됨
- **동시성 제어**: 비관적 락을 사용하여 Race Condition 방지
- **확장 가능한 구조**: 실제 결제 연동 시 충전 단계만 교체하면 됨

## 2. 주요 기능

### 2.1 코인 충전
- **엔드포인트**: `POST /api/payment/charge`
- **설명**: 사용자가 펫코인을 충전합니다. 현재는 실제 결제 없이 시뮬레이션으로 처리됩니다.
- **처리 흐름**:
  1. 사용자 잔액 조회
  2. 잔액 증가
  3. 거래 내역 기록 (`CHARGE` 타입)
- **트랜잭션**: `@Transactional`로 보호됨

### 2.2 코인 차감 (에스크로)
- **설명**: 거래 확정 시 요청자의 코인을 차감하여 에스크로에 보관합니다.
- **처리 흐름**:
  1. **비관적 락으로 사용자 조회** (`findByIdForUpdate`) - Race Condition 방지
  2. 잔액 확인 및 검증
  3. 잔액 차감
  4. 거래 내역 기록 (`DEDUCT` 타입)
  5. 에스크로 생성 (`HOLD` 상태)
- **동시성 제어**: `findByIdForUpdate()`로 잔액 확인과 차감이 원자적으로 처리됨
- **트랜잭션**: `@Transactional`로 보호됨

### 2.3 코인 지급
- **설명**: 거래 완료 시 에스크로에서 제공자에게 코인을 지급합니다.
- **처리 흐름**:
  1. **비관적 락으로 에스크로 조회** (`findByIdForUpdate`) - 중복 지급 방지
  2. 에스크로 상태 확인 (`HOLD` 상태만 가능)
  3. 제공자 잔액 증가
  4. 거래 내역 기록 (`PAYOUT` 타입)
  5. 에스크로 상태 변경 (`RELEASED`)
- **동시성 제어**: 락을 사용하여 동시 요청 시 중복 지급 방지
- **트랜잭션**: `@Transactional`로 보호됨, 지급 실패 시 상태 변경 롤백

### 2.4 코인 환불
- **설명**: 거래 취소 시 에스크로에서 요청자에게 코인을 환불합니다.
- **처리 흐름**:
  1. **비관적 락으로 에스크로 조회** (`findByIdForUpdate`) - 중복 환불 방지
  2. 에스크로 상태 확인 (`HOLD` 상태만 가능)
  3. 요청자 잔액 증가
  4. 거래 내역 기록 (`REFUND` 타입)
  5. 에스크로 상태 변경 (`REFUNDED`)
- **동시성 제어**: 락을 사용하여 동시 요청 시 중복 환불 방지
- **트랜잭션**: `@Transactional`로 보호됨, 환불 실패 시 상태 변경 롤백

### 2.5 잔액 조회
- **엔드포인트**: `GET /api/payment/balance`
- **설명**: 현재 사용자의 코인 잔액을 조회합니다.
- **트랜잭션**: `@Transactional(readOnly = true)`로 읽기 전용 처리

### 2.6 거래 내역 조회
- **엔드포인트**: `GET /api/payment/transactions`
- **설명**: 현재 사용자의 코인 거래 내역을 조회합니다 (페이징 지원).
- **트랜잭션**: `@Transactional(readOnly = true)`로 읽기 전용 처리

## 3. 데이터베이스 구조

### 3.1 Users 테이블
```sql
pet_coin_balance INT DEFAULT 0 NOT NULL COMMENT '펫코인 잔액'
```

### 3.2 pet_coin_transaction 테이블
모든 코인 거래 내역을 기록합니다.

**주요 컬럼**:
- `transaction_type`: CHARGE, DEDUCT, PAYOUT, REFUND
- `amount`: 거래 금액
- `balance_before`: 거래 전 잔액
- `balance_after`: 거래 후 잔액
- `related_type`: 관련 엔티티 타입 (예: CARE_REQUEST)
- `related_idx`: 관련 엔티티 ID
- `status`: COMPLETED, PENDING, FAILED, CANCELLED
- `description`: 거래 설명

### 3.3 pet_coin_escrow 테이블
거래 확정 시 코인을 임시 보관합니다.

**주요 컬럼**:
- `care_request_idx`: 펫케어 요청 ID (UNIQUE 제약조건)
- `care_application_idx`: 펫케어 지원 ID
- `requester_idx`: 요청자 ID
- `provider_idx`: 제공자 ID
- `amount`: 에스크로 금액
- `status`: HOLD, RELEASED, REFUNDED
- `released_at`: 지급 시각 (RELEASED 상태일 때)
- `refunded_at`: 환불 시각 (REFUNDED 상태일 때)

## 4. 펫케어 거래 흐름과의 연동

### 4.1 거래 확정 시 (`ConversationService.confirmCareDeal()`)

**호출 위치**: 채팅방에서 양쪽 모두 거래 확정 시

**처리 내용**:
```java
// 거래 확정 시 코인 차감 및 에스크로 생성
if (offeredCoins != null && offeredCoins > 0) {
    petCoinEscrowService.createEscrow(
        careRequest,
        careApplication,
        requester,
        provider,
        offeredCoins
    );
}
```

**처리 흐름**:
1. `CareRequest` 상태를 `IN_PROGRESS`로 변경
2. `CareApplication` 상태를 `ACCEPTED`로 변경
3. 요청자 코인 차감 (`PetCoinService.deductCoins`)
   - 비관적 락으로 잔액 확인 및 차감
4. 에스크로 생성 (`PetCoinEscrow` - `HOLD` 상태)
5. 거래 내역 기록 (`DEDUCT` 타입)

**트랜잭션 관리**:
- `ConversationService.confirmCareDeal()`에 `@Transactional` 적용
- 에스크로 생성 실패 시 전체 트랜잭션 롤백 (상태 변경도 함께 롤백)

**주의사항**:
- `offeredCoins`가 null이거나 0이면 거래 확정 불가
- 이미 에스크로가 있으면 `IllegalStateException` 발생

### 4.2 거래 완료 시 (`CareRequestService.updateStatus()`)

**호출 위치**: `CareRequest` 상태를 `COMPLETED`로 변경 시

**처리 내용**:
```java
// 상태가 COMPLETED로 변경될 때 에스크로에서 제공자에게 코인 지급
if (oldStatus != CareRequestStatus.COMPLETED && newStatus == CareRequestStatus.COMPLETED) {
    // 비관적 락으로 에스크로 조회 (동시 요청 시 중복 지급 방지)
    PetCoinEscrow escrow = petCoinEscrowService.findByCareRequestForUpdate(request);
    if (escrow != null && escrow.getStatus() == EscrowStatus.HOLD) {
        petCoinEscrowService.releaseToProvider(escrow);
    }
}
```

**처리 흐름**:
1. **비관적 락으로 에스크로 조회** (`findByCareRequestForUpdate`) - 동시성 제어
2. 에스크로 상태 확인 (`HOLD` 상태만 가능)
3. 제공자 코인 지급 (`PetCoinService.payoutCoins`)
4. 에스크로 상태 변경 (`RELEASED`)
5. 거래 내역 기록 (`PAYOUT` 타입)

**트랜잭션 관리**:
- `CareRequestService.updateStatus()`에 `@Transactional` 적용
- 코인 지급 실패 시 상태 변경 롤백 (상태는 `COMPLETED`로 변경되지 않음)

**동시성 제어**:
- 조회 시점부터 락을 획득하여 중복 지급 완전 방지
- 첫 번째 요청만 처리되고, 두 번째 요청은 락 대기 후 이미 `RELEASED` 상태 확인하여 차단

### 4.3 거래 취소 시 (`CareRequestService.updateStatus()`)

**호출 위치**: `CareRequest` 상태를 `CANCELLED`로 변경 시

**처리 내용**:
```java
// 상태가 CANCELLED로 변경될 때 에스크로에서 요청자에게 코인 환불
if (newStatus == CareRequestStatus.CANCELLED) {
    // 비관적 락으로 에스크로 조회 (동시 요청 시 중복 환불 방지)
    PetCoinEscrow escrow = petCoinEscrowService.findByCareRequestForUpdate(request);
    if (escrow != null && escrow.getStatus() == EscrowStatus.HOLD) {
        petCoinEscrowService.refundToRequester(escrow);
    }
}
```

**처리 흐름**:
1. **비관적 락으로 에스크로 조회** (`findByCareRequestForUpdate`) - 동시성 제어
2. 에스크로 상태 확인 (`HOLD` 상태만 가능)
3. 요청자 코인 환불 (`PetCoinService.refundCoins`)
4. 에스크로 상태 변경 (`REFUNDED`)
5. 거래 내역 기록 (`REFUND` 타입)

**트랜잭션 관리**:
- `CareRequestService.updateStatus()`에 `@Transactional` 적용
- 환불 실패 시 상태 변경 롤백 (상태는 `CANCELLED`로 변경되지 않음)

**동시성 제어**:
- 조회 시점부터 락을 획득하여 중복 환불 완전 방지
- 첫 번째 요청만 처리되고, 두 번째 요청은 락 대기 후 이미 `REFUNDED` 상태 확인하여 차단

### 4.4 자동 완료 처리 (`CareRequestScheduler`)

**호출 위치**: 만료된 `CareRequest`를 자동으로 `COMPLETED`로 변경 시

**처리 내용**:
```java
// 스케줄러에서 서비스 메서드를 통해 상태 변경 (에스크로 처리 포함)
careRequestService.updateStatus(
    request.getIdx(), 
    "COMPLETED", 
    null // 스케줄러는 시스템 작업이므로 currentUserId 없음
);
```

**처리 흐름**:
1. 만료된 `CareRequest` 조회 (날짜가 지났고 `OPEN` 또는 `IN_PROGRESS` 상태)
2. `CareRequestService.updateStatus()` 호출
3. 상태를 `COMPLETED`로 변경
4. 에스크로 처리 (4.2와 동일)

**트랜잭션 관리**:
- 각 요청을 개별 트랜잭션으로 처리하여 실패 시 다른 요청에 영향 없음
- 개별 요청별 예외 처리 추가

## 5. 서비스 로직

### 5.1 PetCoinService
**역할**: 코인 충전, 차감, 지급, 환불 등 모든 코인 거래 처리

**주요 메서드**:
- `chargeCoins()`: 코인 충전
- `deductCoins()`: 코인 차감 (에스크로로 이동)
  - **비관적 락 사용**: `findByIdForUpdate()`로 Race Condition 방지
  - 잔액 부족 시 `IllegalStateException` 발생
- `payoutCoins()`: 코인 지급 (에스크로에서 제공자에게)
- `refundCoins()`: 코인 환불 (에스크로에서 요청자에게)
- `getBalance()`: 잔액 조회

**트랜잭션 관리**:
- 모든 메서드에 `@Transactional` 적용
- 잔액 업데이트와 거래 내역 기록이 원자적으로 처리됨

**동시성 제어**:
- `deductCoins()`에서 `findByIdForUpdate()` 사용
- 잔액 확인과 차감이 원자적으로 처리되어 음수 잔액 방지

### 5.2 PetCoinEscrowService
**역할**: 에스크로 생성, 지급, 환불 관리

**주요 메서드**:
- `createEscrow()`: 에스크로 생성 (거래 확정 시)
  - 중복 에스크로 생성 방지 (`findByCareRequest()`로 확인)
  - 요청자 코인 차감 후 에스크로 생성
- `releaseToProvider()`: 제공자에게 지급 (거래 완료 시)
  - **비관적 락 사용**: `findByIdForUpdate()`로 중복 지급 방지
  - `HOLD` 상태만 지급 가능
- `refundToRequester()`: 요청자에게 환불 (거래 취소 시)
  - **비관적 락 사용**: `findByIdForUpdate()`로 중복 환불 방지
  - `HOLD` 상태만 환불 가능
- `findByCareRequest()`: CareRequest로 에스크로 조회 (읽기 전용)
- `findByCareRequestForUpdate()`: **비관적 락을 사용한 조회** (동시성 제어용)

**트랜잭션 관리**:
- 모든 메서드에 `@Transactional` 적용
- 에스크로 상태 변경과 코인 지급/환불이 원자적으로 처리됨

**동시성 제어**:
- `releaseToProvider()`와 `refundToRequester()`에서 `findByIdForUpdate()` 사용
- `CareRequestService`에서 `findByCareRequestForUpdate()` 사용하여 조회 시점부터 락 획득

## 6. 동시성 제어 (Race Condition 방지)

### 6.1 잔액 확인과 차감 사이의 Race Condition 방지

**문제**: 두 거래가 동시에 잔액을 확인하고 둘 다 통과할 수 있음

**해결**:
- `PetCoinService.deductCoins()`에서 `findByIdForUpdate()` 사용
- 비관적 락으로 잔액 확인과 차감이 원자적으로 처리됨

**시나리오**:
```
사용자 잔액: 100 코인
거래 A: 80 코인 차감 시도
거래 B: 50 코인 차감 시도

✅ 개선 후:
- 거래 A: 락 획득 → 잔액 확인 (100) → 차감 → 잔액 20
- 거래 B: 락 대기 → 락 획득 → 잔액 확인 (20) → 잔액 부족 예외 발생
```

### 6.2 에스크로 상태 변경 시 Race Condition 방지

**문제**: 동일한 에스크로에 대해 중복 지급/환불 가능

**해결**:
- `PetCoinEscrowService.releaseToProvider()`와 `refundToRequester()`에서 `findByIdForUpdate()` 사용
- 상태 확인과 변경이 원자적으로 처리됨

### 6.3 상태 변경 시 에스크로 조회 Race Condition 방지

**문제**: 조회 시점과 `releaseToProvider()` 호출 사이에 다른 트랜잭션이 끼어들 수 있음

**해결**:
- `CareRequestService.updateStatus()`에서 `findByCareRequestForUpdate()` 사용
- 조회 시점부터 락을 획득하여 중복 처리 완전 방지

**시나리오**:
```
에스크로 상태: HOLD
요청 A: COMPLETED 상태 변경 시도
요청 B: COMPLETED 상태 변경 시도 (동시에)

✅ 개선 후:
- 요청 A: findByCareRequestForUpdate() → 락 획득 → HOLD 확인 → releaseToProvider() → RELEASED
- 요청 B: findByCareRequestForUpdate() → 락 대기 → 락 획득 → RELEASED 확인 → 처리 안 함
```

## 7. 트랜잭션 관리

### 7.1 트랜잭션 전파

**현재 구조**:
- `CareRequestService.updateStatus()`: `@Transactional` (기본값 `REQUIRED`)
- `PetCoinEscrowService.releaseToProvider()`: `@Transactional` (기본값 `REQUIRED`)
- `PetCoinEscrowService.findByCareRequestForUpdate()`: `@Transactional` (기본값 `REQUIRED`)

**결과**: 모두 같은 트랜잭션에 참여합니다. `releaseToProvider()` 내부에서 예외가 발생하면 전체가 롤백되어 `CareRequest` 상태 변경도 함께 롤백됩니다.

**`REQUIRES_NEW` 사용 시 문제점**:
- 만약 `releaseToProvider()`에 `REQUIRES_NEW`를 적용하면:
  - `releaseToProvider()`가 독립 트랜잭션으로 실행되어 먼저 커밋됨
  - 이후 `updateStatus()`에서 예외가 발생하면:
    - 코인 지급은 이미 커밋되어 롤백 불가능
    - `CareRequest` 상태 변경만 롤백
    - **데이터 불일치 발생**: 코인은 지급되었지만 상태는 `COMPLETED`가 아님

**결론**: 현재 구조(같은 트랜잭션)가 더 안전합니다.

### 7.2 롤백 처리

**에스크로 생성 실패 시**:
- `ConversationService.confirmCareDeal()`에서 예외 발생
- 전체 트랜잭션 롤백 (`CareRequest` 상태 변경, `CareApplication` 상태 변경 모두 롤백)

**코인 지급 실패 시**:
- `CareRequestService.updateStatus()`에서 예외 발생
- 전체 트랜잭션 롤백 (`CareRequest` 상태 변경 롤백)

**코인 환불 실패 시**:
- `CareRequestService.updateStatus()`에서 예외 발생
- 전체 트랜잭션 롤백 (`CareRequest` 상태 변경 롤백)

## 8. API 엔드포인트

### 8.1 사용자용 API (`PetCoinController`)
- `GET /api/payment/balance`: 코인 잔액 조회
- `GET /api/payment/transactions`: 거래 내역 조회 (페이징)
- `POST /api/payment/charge`: 코인 충전 (시뮬레이션)

### 8.2 관리자용 API (`AdminPaymentController`)
- `POST /api/admin/payment/charge`: 관리자 코인 지급
- `GET /api/admin/payment/balance/{userId}`: 특정 사용자 잔액 조회
- `GET /api/admin/payment/transactions/{userId}`: 특정 사용자 거래 내역 조회 (페이징)

## 9. 실제 결제 연동 시 확장 방안

### 9.1 현재 구조
```
사용자 → 코인 충전 API → PetCoinService.chargeCoins()
```

### 9.2 실제 결제 연동 시
```
사용자 → PG 결제 페이지 → PG 콜백 → PetCoinService.chargeCoins()
```

**변경 사항**:
- `POST /api/payment/charge` 엔드포인트를 PG 연동으로 교체
- PG 결제 성공 후 `PetCoinService.chargeCoins()` 호출
- 나머지 로직(차감, 지급, 환불)은 그대로 사용 가능

## 10. 보안 고려사항

### 10.1 트랜잭션 안전성
- 모든 코인 거래는 `@Transactional`로 보호됨
- 잔액 업데이트와 거래 내역 기록이 원자적으로 처리됨
- 에스크로 생성/지급/환불 실패 시 자동 롤백

### 10.2 잔액 검증
- 차감 시 잔액 부족 체크
- 거래 전/후 잔액을 모두 기록하여 추적 가능
- 비관적 락으로 음수 잔액 방지

### 10.3 에스크로 상태 관리
- `HOLD` 상태의 에스크로만 지급/환불 가능
- 중복 지급/환불 방지 (비관적 락 사용)
- 상태 변경과 코인 지급/환불이 원자적으로 처리됨

### 10.4 동시성 제어
- 비관적 락(`PESSIMISTIC_WRITE`)을 사용하여 Race Condition 방지
- 잔액 확인과 차감이 원자적으로 처리됨
- 에스크로 상태 확인과 변경이 원자적으로 처리됨
- 조회 시점부터 락을 획득하여 중복 처리 완전 방지

---

## ✨ 한 줄 요약

**"실제 결제 시스템 연동 없이 시뮬레이션으로 구현했으며, 비관적 락을 사용한 동시성 제어와 트랜잭션 일관성을 보장하는 펫코인 결제 시스템. 실제 운영 시 충전 단계만 PG로 대체하면 되도록 설계됨"**
