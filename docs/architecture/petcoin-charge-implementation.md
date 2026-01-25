# 펫코인 충전 구현 방안 (개발 단계)

## 📋 목표

실제 결제 시스템 연동 전까지 개발/테스트를 위한 대체 충전 방식 구현

## 🎯 구현 전략

### 1. 관리자용 코인 지급 API (우선 구현)
- **목적**: 관리자가 사용자에게 코인을 직접 지급
- **사용 시나리오**: 
  - 개발/테스트 시 관리자가 테스트 계정에 코인 지급
  - 운영 초기 단계에서 수동 코인 지급
- **권한**: ADMIN, MASTER만 접근 가능

### 2. 일반 사용자용 테스트 충전 API (선택적)
- **목적**: 개발/테스트 환경에서 일반 사용자도 코인 충전 가능
- **조건**: 
  - `spring.profiles.active=dev` 또는 `test` 환경에서만 활성화
  - 프로덕션 환경에서는 비활성화
- **구현**: 프로파일별 조건부 활성화

### 3. 향후 확장: 실제 결제 시스템 연동
- PG사 연동 (토스페이먼츠, 이니시스 등)
- 결제 검증 로직 추가
- 결제 내역 저장

## 📁 구현 구조

```
backend/main/java/com/linkup/Petory/domain/payment/
├── entity/
│   ├── PetCoinTransaction.java ✅
│   ├── PetCoinEscrow.java ✅
│   └── ...
├── repository/
│   ├── PetCoinTransactionRepository.java (생성 필요)
│   ├── PetCoinEscrowRepository.java (생성 필요)
│   └── ...
├── service/
│   ├── PetCoinService.java (생성 필요)
│   └── ...
├── dto/
│   ├── PetCoinChargeRequest.java (생성 필요)
│   ├── PetCoinTransactionDTO.java (생성 필요)
│   └── ...
└── controller/
    ├── PetCoinController.java (생성 필요 - 일반 사용자용)
    └── ...
```

## 🔧 구현 단계

### Step 1: Repository 생성
- `PetCoinTransactionRepository`
- `PetCoinEscrowRepository`

### Step 2: Service 생성
- `PetCoinService`: 코인 충전/차감/지급/환불 로직
- 트랜잭션 관리 필수
- 거래 내역 자동 기록

### Step 3: Controller 생성
- `AdminPaymentController`: 관리자용 코인 지급 API
- `PetCoinController`: 일반 사용자용 충전 API (개발 환경)

### Step 4: DTO 생성
- 요청/응답 DTO

## 💡 핵심 로직

### 코인 충전 프로세스
1. 사용자 잔액 확인
2. 코인 추가 (Users.petCoinBalance 증가)
3. 거래 내역 기록 (PetCoinTransaction 생성)
   - 타입: CHARGE
   - 상태: COMPLETED
   - 거래 전/후 잔액 기록

### 트랜잭션 관리
- `@Transactional` 필수
- 잔액 업데이트와 거래 내역 기록은 원자적으로 처리
- 실패 시 롤백

## 🚀 API 엔드포인트 설계

### 관리자용 API
```
POST /api/admin/payment/charge
- 관리자가 사용자에게 코인 지급
- Body: { userId, amount, description }
```

### 일반 사용자용 API (개발 환경)
```
POST /api/payment/charge
- 본인 계정에 코인 충전 (테스트용)
- Body: { amount }
- 프로파일별 조건부 활성화
```

### 조회 API
```
GET /api/payment/balance
- 본인 코인 잔액 조회

GET /api/payment/transactions
- 본인 거래 내역 조회 (페이징)
```

## ⚠️ 주의사항

1. **잔액 검증**: 음수 방지, 오버플로우 방지
2. **트랜잭션 일관성**: 잔액과 거래 내역 항상 동기화
3. **권한 검증**: 관리자 API는 반드시 권한 체크
4. **프로파일 분리**: 테스트 충전 API는 프로덕션에서 비활성화

## 📝 향후 확장

실제 결제 시스템 연동 시:
- `PaymentGatewayService` 인터페이스 생성
- 토스페이먼츠, 이니시스 등 구현체 생성
- 결제 검증 로직 추가
- 웹훅 처리 (결제 완료 알림)
