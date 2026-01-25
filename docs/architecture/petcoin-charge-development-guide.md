# 펫코인 충전 - 개발 단계 가이드

## 📋 개발 단계에서 코인 충전 방법

실제 결제 시스템 연동 전까지 개발/테스트를 위한 여러 방법을 제공합니다.

## 🎯 방법 1: 관리자 API 사용 (권장)

**가장 간단하고 안전한 방법**

### 사용 방법
```bash
# 관리자 계정으로 로그인 후
POST /api/admin/payment/charge
Content-Type: application/json
Authorization: Bearer {admin_token}

{
  "userId": 1,
  "amount": 10000,
  "description": "개발용 코인 지급"
}
```

### 장점
- ✅ 실제 운영과 유사한 방식
- ✅ 권한 체크 (ADMIN/MASTER만 가능)
- ✅ 거래 내역 자동 기록
- ✅ 프로덕션 환경에서도 사용 가능

### 단점
- 관리자 계정 필요

---

## 🎯 방법 2: 테스트 충전 API (개발 환경)

**개발/테스트 환경에서만 사용 가능**

### 설정
`application-dev.yml` 또는 `application-test.yml`에서:
```yaml
spring:
  profiles:
    active: dev  # 또는 test
```

### 사용 방법
```bash
# 일반 사용자로 로그인 후
POST /api/payment/charge
Content-Type: application/json
Authorization: Bearer {user_token}

{
  "amount": 5000,
  "description": "테스트 충전"
}
```

### 장점
- ✅ 빠른 테스트 가능
- ✅ 관리자 계정 불필요
- ✅ 본인 계정에 직접 충전

### 단점
- 프로덕션 환경에서는 자동 비활성화
- 보안상 개발 환경에서만 사용 권장

---

## 🎯 방법 3: 초기 데이터 (시드 데이터)

**애플리케이션 시작 시 자동으로 코인 지급**

### 구현 방법

#### Option A: CommandLineRunner 사용
```java
@Component
@RequiredArgsConstructor
public class PetCoinSeeder implements CommandLineRunner {
    
    private final PetCoinService petCoinService;
    private final UsersRepository usersRepository;
    
    @Value("${spring.profiles.active:prod}")
    private String activeProfile;
    
    @Override
    public void run(String... args) {
        // 개발 환경에서만 실행
        if (!"dev".equals(activeProfile) && !"test".equals(activeProfile)) {
            return;
        }
        
        // 테스트 사용자들에게 초기 코인 지급
        List<Users> testUsers = usersRepository.findAll();
        for (Users user : testUsers) {
            if (user.getPetCoinBalance() == 0) {
                petCoinService.chargeCoins(user, 10000, "초기 개발용 코인 지급");
            }
        }
    }
}
```

#### Option B: SQL 스크립트
```sql
-- 개발용 초기 코인 지급
UPDATE users 
SET pet_coin_balance = 10000 
WHERE pet_coin_balance = 0 
AND idx IN (1, 2, 3); -- 테스트 사용자 ID

-- 거래 내역도 기록 (선택사항)
INSERT INTO pet_coin_transaction 
(user_idx, transaction_type, amount, balance_before, balance_after, description, status, created_at)
SELECT 
    idx, 
    'CHARGE', 
    10000, 
    0, 
    10000, 
    '초기 개발용 코인 지급', 
    'COMPLETED', 
    NOW()
FROM users 
WHERE pet_coin_balance = 10000;
```

### 장점
- ✅ 자동화 가능
- ✅ 반복 작업 불필요
- ✅ 일관된 초기 상태

### 단점
- 프로덕션 환경에서 실행되면 안 됨
- 프로파일 체크 필수

---

## 🎯 방법 4: 더미 결제 API (모의 결제)

**실제 결제 플로우를 시뮬레이션**

### 구현 예시
```java
@RestController
@RequestMapping("/api/payment/dummy")
@RequiredArgsConstructor
@Profile({"dev", "test"}) // 개발/테스트 환경에서만 활성화
public class DummyPaymentController {
    
    private final PetCoinService petCoinService;
    private final UsersRepository usersRepository;
    
    /**
     * 더미 결제 (개발용)
     * 실제 결제 없이 코인 충전
     */
    @PostMapping("/charge")
    public ResponseEntity<PetCoinTransactionDTO> dummyCharge(
            @RequestBody PetCoinChargeRequest request) {
        
        Long userId = getCurrentUserId();
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // 실제 결제 검증 로직 대신 바로 충전
        PetCoinTransaction transaction = petCoinService.chargeCoins(
                user,
                request.getAmount(),
                "더미 결제 - " + (request.getDescription() != null ? request.getDescription() : ""));
        
        return ResponseEntity.ok(transactionConverter.toDTO(transaction));
    }
}
```

### 장점
- ✅ 실제 결제 플로우와 유사
- ✅ 나중에 실제 결제 API로 교체 용이
- ✅ 프론트엔드 개발 시 유용

### 단점
- 추가 구현 필요
- 프로덕션 환경에서 제거 필요

---

## 📊 추천 방법 조합

### 개발 초기 단계
1. **관리자 API** 사용 (가장 빠름)
2. 필요시 **테스트 충전 API** 사용

### 개발 중반 단계
1. **초기 데이터 (시드)** 추가
2. **더미 결제 API** 구현 (프론트엔드 연동용)

### 개발 후반 단계
1. 실제 결제 시스템 연동 준비
2. 더미 API를 실제 API로 교체

---

## ⚠️ 주의사항

1. **프로덕션 환경 보호**
   - 테스트 충전 API는 반드시 프로파일 체크
   - 더미 결제 API는 `@Profile` 어노테이션 사용

2. **거래 내역 기록**
   - 모든 충전은 거래 내역에 기록되어야 함
   - 감사(audit) 목적으로 중요

3. **잔액 검증**
   - 음수 방지
   - 오버플로우 방지
   - 트랜잭션 일관성 유지

---

## 🚀 빠른 시작 가이드

### 1단계: 관리자 계정 생성 (없는 경우)
```sql
-- 관리자 계정 생성 (예시)
INSERT INTO users (id, username, email, password, role, pet_coin_balance)
VALUES ('admin', 'admin', 'admin@petory.com', '{암호화된 비밀번호}', 'ADMIN', 0);
```

### 2단계: 관리자로 로그인
```bash
POST /api/auth/login
{
  "username": "admin",
  "password": "admin123"
}
```

### 3단계: 코인 지급
```bash
POST /api/admin/payment/charge
Authorization: Bearer {admin_token}
{
  "userId": 1,
  "amount": 10000,
  "description": "개발용 코인"
}
```

### 4단계: 잔액 확인
```bash
GET /api/payment/balance
Authorization: Bearer {user_token}
```

---

## 📝 향후 실제 결제 연동 시

1. `PetCoinService.chargeCoins()` 메서드 수정
2. 결제 검증 로직 추가
3. 결제 실패 시 롤백 처리
4. 웹훅 처리 (결제 완료 알림)

**현재 구조는 실제 결제 연동 시 최소한의 변경으로 확장 가능하도록 설계되었습니다.**
