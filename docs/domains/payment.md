# Payment 도메인 - 펫코인 결제 시스템

## 1. 도메인 개요

### 1.1 역할
펫코인(PetCoin)은 펫케어 거래를 위한 내부 결제 단위입니다. 실제 결제 시스템(PG) 연동 없이 시뮬레이션으로 구현되어 있으며, 실제 운영 시에는 충전 단계만 PG로 대체하면 되도록 설계되었습니다.

### 1.2 핵심 원칙
- **외부 PG 직접 연동 없음**: 개발 단계에서는 실제 결제 없이 시뮬레이션
- **에스크로 시스템**: 거래 확정 시 코인을 임시 보관하여 거래 안전성 확보
- **완전한 거래 내역 기록**: 모든 코인 거래가 `pet_coin_transaction` 테이블에 기록됨
- **확장 가능한 구조**: 실제 결제 연동 시 충전 단계만 교체하면 됨

## 2. 주요 기능

### 2.1 코인 충전
- **엔드포인트**: `POST /api/payment/charge`
- **설명**: 사용자가 펫코인을 충전합니다. 현재는 실제 결제 없이 시뮬레이션으로 처리됩니다.
- **처리 흐름**:
  1. 사용자 잔액 조회
  2. 잔액 증가
  3. 거래 내역 기록 (`CHARGE` 타입)

### 2.2 코인 차감 (에스크로)
- **설명**: 거래 확정 시 요청자의 코인을 차감하여 에스크로에 보관합니다.
- **처리 흐름**:
  1. 잔액 확인
  2. 잔액 차감
  3. 거래 내역 기록 (`DEDUCT` 타입)
  4. 에스크로 생성 (`HOLD` 상태)

### 2.3 코인 지급
- **설명**: 거래 완료 시 에스크로에서 제공자에게 코인을 지급합니다.
- **처리 흐름**:
  1. 에스크로 조회 및 상태 확인 (`HOLD` 상태)
  2. 제공자 잔액 증가
  3. 거래 내역 기록 (`PAYOUT` 타입)
  4. 에스크로 상태 변경 (`RELEASED`)

### 2.4 코인 환불
- **설명**: 거래 취소 시 에스크로에서 요청자에게 코인을 환불합니다.
- **처리 흐름**:
  1. 에스크로 조회 및 상태 확인 (`HOLD` 상태)
  2. 요청자 잔액 증가
  3. 거래 내역 기록 (`REFUND` 타입)
  4. 에스크로 상태 변경 (`REFUNDED`)

### 2.5 잔액 조회
- **엔드포인트**: `GET /api/payment/balance`
- **설명**: 현재 사용자의 코인 잔액을 조회합니다.

### 2.6 거래 내역 조회
- **엔드포인트**: `GET /api/payment/transactions`
- **설명**: 현재 사용자의 코인 거래 내역을 조회합니다 (페이징 지원).

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

### 3.3 pet_coin_escrow 테이블
거래 확정 시 코인을 임시 보관합니다.

**주요 컬럼**:
- `care_request_idx`: 펫케어 요청 ID (UNIQUE)
- `care_application_idx`: 펫케어 지원 ID
- `requester_idx`: 요청자 ID
- `provider_idx`: 제공자 ID
- `amount`: 에스크로 금액
- `status`: HOLD, RELEASED, REFUNDED

## 4. 펫케어 거래 흐름과의 연동

### 4.1 거래 확정 시 (`ConversationService`)
```java
// 거래 확정 시 코인 차감 및 에스크로 생성
petCoinEscrowService.createEscrow(
    careRequest,
    careApplication,
    requester,
    provider,
    offeredCoins
);
```

**처리 내용**:
1. 요청자 코인 차감 (`PetCoinService.deductCoins`)
2. 에스크로 생성 (`PetCoinEscrow` - `HOLD` 상태)
3. 거래 내역 기록 (`DEDUCT` 타입)

### 4.2 거래 완료 시 (`CareRequestService`)
```java
// 상태가 COMPLETED로 변경될 때 제공자에게 코인 지급
petCoinEscrowService.releaseToProvider(escrow);
```

**처리 내용**:
1. 에스크로 조회 및 상태 확인
2. 제공자 코인 지급 (`PetCoinService.payoutCoins`)
3. 에스크로 상태 변경 (`RELEASED`)
4. 거래 내역 기록 (`PAYOUT` 타입)

### 4.3 거래 취소 시 (향후 구현)
```java
// 거래 취소 시 요청자에게 코인 환불
petCoinEscrowService.refundToRequester(escrow);
```

**처리 내용**:
1. 에스크로 조회 및 상태 확인
2. 요청자 코인 환불 (`PetCoinService.refundCoins`)
3. 에스크로 상태 변경 (`REFUNDED`)
4. 거래 내역 기록 (`REFUND` 타입)

## 5. 서비스 로직

### 5.1 PetCoinService
**역할**: 코인 충전, 차감, 지급, 환불 등 모든 코인 거래 처리

**주요 메서드**:
- `chargeCoins()`: 코인 충전
- `deductCoins()`: 코인 차감 (에스크로로 이동)
- `payoutCoins()`: 코인 지급 (에스크로에서 제공자에게)
- `refundCoins()`: 코인 환불 (에스크로에서 요청자에게)
- `getBalance()`: 잔액 조회

**트랜잭션 관리**:
- 모든 메서드에 `@Transactional` 적용
- 잔액 업데이트와 거래 내역 기록이 원자적으로 처리됨

### 5.2 PetCoinEscrowService
**역할**: 에스크로 생성, 지급, 환불 관리

**주요 메서드**:
- `createEscrow()`: 에스크로 생성 (거래 확정 시)
- `releaseToProvider()`: 제공자에게 지급 (거래 완료 시)
- `refundToRequester()`: 요청자에게 환불 (거래 취소 시)
- `findByCareRequest()`: CareRequest로 에스크로 조회

## 6. API 엔드포인트

### 6.1 사용자용 API (`PetCoinController`)
- `GET /api/payment/balance`: 코인 잔액 조회
- `GET /api/payment/transactions`: 거래 내역 조회 (페이징)
- `POST /api/payment/charge`: 코인 충전 (시뮬레이션)

### 6.2 관리자용 API (`AdminPaymentController`)
- `POST /api/admin/payment/charge`: 관리자 코인 지급
- `GET /api/admin/payment/balance/{userId}`: 특정 사용자 잔액 조회
- `GET /api/admin/payment/transactions/{userId}`: 특정 사용자 거래 내역 조회

## 7. 실제 결제 연동 시 확장 방안

### 7.1 현재 구조
```
사용자 → 코인 충전 API → PetCoinService.chargeCoins()
```

### 7.2 실제 결제 연동 시
```
사용자 → PG 결제 페이지 → PG 콜백 → PetCoinService.chargeCoins()
```

**변경 사항**:
- `POST /api/payment/charge` 엔드포인트를 PG 연동으로 교체
- PG 결제 성공 후 `PetCoinService.chargeCoins()` 호출
- 나머지 로직(차감, 지급, 환불)은 그대로 사용 가능

## 8. 보안 고려사항

### 8.1 트랜잭션 안전성
- 모든 코인 거래는 `@Transactional`로 보호됨
- 잔액 업데이트와 거래 내역 기록이 원자적으로 처리됨

### 8.2 잔액 검증
- 차감 시 잔액 부족 체크
- 거래 전/후 잔액을 모두 기록하여 추적 가능

### 8.3 에스크로 상태 관리
- `HOLD` 상태의 에스크로만 지급/환불 가능
- 중복 지급/환불 방지

## 9. 포트폴리오 관점

### 9.1 설계 특징
- **확장 가능한 구조**: 실제 결제 연동 시 최소한의 변경으로 대응 가능
- **완전한 거래 추적**: 모든 거래 내역이 기록되어 감사 가능
- **에스크로 시스템**: 거래 안전성 확보

### 9.2 기술적 고려사항
- 트랜잭션 관리로 데이터 일관성 보장
- 에스크로를 통한 거래 안전성 확보
- 실제 결제 연동 시 확장 가능한 구조 설계

---

## ✨ 한 줄 요약

**"실제 결제 시스템 연동 없이 시뮬레이션으로 구현했으며, 실제 운영 시 충전 단계만 PG로 대체하면 되도록 설계한 펫코인 결제 시스템"**
