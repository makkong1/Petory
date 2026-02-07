# Payment 도메인 DTO → record 리팩토링

## 개요

Payment 도메인 DTO 중 record 적용에 적합한 항목을 선별하여 리팩토링함.  
판단 기준: `docs/refactoring/dto-to-record.md` 적용 방침 참고.

---

## record로 전환한 DTO (2개)

### 1. PetCoinBalanceResponse

| 항목 | 내용 |
|------|------|
| **용도** | 코인 잔액 응답 |
| **필드 수** | 2 (userId, balance) |
| **전환 이유** | Response 전용, 필드 2개로 매우 단순, setter 미사용 |
| **사용처** | PetCoinController (getMyBalance), AdminPaymentController (getUserBalance) |

### 2. PetCoinChargeRequest

| 항목 | 내용 |
|------|------|
| **용도** | 코인 충전 요청 (`@RequestBody`) |
| **필드 수** | 3 (userId, amount, description) |
| **전환 이유** | Request 전용, 필드 3개로 단순, Jackson 역직렬화 정상 동작 |
| **사용처** | PetCoinController (chargeCoins), AdminPaymentController (chargeCoins) |

---

## record로 전환하지 않은 DTO (1개)

### PetCoinTransactionDTO ❌

| 항목 | 내용 |
|------|------|
| **판단** | 부적합 |
| **이유** | 필드 **12개** → 생성자 길어짐. Response 전용이지만 필드 수가 많아 builder 유지가 가독성에 유리 |

---

## 변경 사항 요약

| 변경 유형 | 내용 |
|----------|------|
| **DTO 정의** | Lombok `@Data` `@Builder` 제거 → `public record XxxDTO(...)` |
| **생성** | `.builder().field(x).build()` → `new XxxDTO(...)` |
| **접근** | `dto.getXxx()` → `dto.xxx()` (record accessor) |

---

## 수정된 파일

| 파일 | 변경 내용 |
|-----|---------|
| `PetCoinBalanceResponse.java` | class → record |
| `PetCoinChargeRequest.java` | class → record |
| `PetCoinController.java` | builder → 생성자, getter → accessor |
| `AdminPaymentController.java` | builder → 생성자, getter → accessor |

---

## 참고

- `docs/refactoring/dto-to-record.md` : record DTO 적용 방침, 장단점, 직렬화 흐름
