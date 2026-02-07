# User 도메인 DTO → record 리팩토링

## 개요

User 도메인 DTO 중 record 적용에 적합한 항목을 선별하여 리팩토링함.  
판단 기준: `docs/refactoring/dto-to-record.md` 적용 방침 참고.

---

## record로 전환한 DTO (3개)

### 1. LoginRequest

| 항목 | 내용 |
|------|------|
| **용도** | 로그인 요청 (`@RequestBody`) |
| **필드 수** | 2 (id, password) |
| **전환 이유** | Request 전용, 필드 2개로 매우 단순, Jackson 역직렬화 정상 동작 |
| **사용처** | AuthController (login) |

### 2. TokenResponse

| 항목 | 내용 |
|------|------|
| **용도** | 토큰 발급 응답 |
| **필드 수** | 3 (accessToken, refreshToken, user) |
| **전환 이유** | Response 전용, 필드 3개로 단순, setter 미사용 |
| **사용처** | AuthService (login, refreshAccessToken), OAuth2Service, AuthController |

### 3. SocialUserDTO

| 항목 | 내용 |
|------|------|
| **용도** | 소셜 로그인 사용자 정보 응답 |
| **필드 수** | 3 (idx, provider, providerId) |
| **전환 이유** | Response 전용, 필드 3개로 단순, setter 미사용 |
| **사용처** | SocialUserConverter (toDTO, toEntity), UsersConverter |

---

## record로 전환하지 않은 DTO (5개)

### UsersDTO ❌

| 항목 | 내용 |
|------|------|
| **판단** | 부적합 |
| **이유** | 필드 **17개** → 생성자 과도하게 김. Request/Response 겸용 (`@RequestBody UsersDTO`). 중첩 구조 포함 |

### PetDTO ❌

| 항목 | 내용 |
|------|------|
| **판단** | 부적합 |
| **이유** | 필드 **18개** → 생성자 과도하게 김. Request/Response 겸용. 중첩 구조(`List<PetVaccinationDTO>`) 포함 |

### PetVaccinationDTO ❌

| 항목 | 내용 |
|------|------|
| **판단** | 보류 |
| **이유** | 필드 8개로 적당하지만, 사용 빈도 낮고 Request/Response 겸용 가능성 있음 |

### UserProfileWithReviewsDTO ❌

| 항목 | 내용 |
|------|------|
| **판단** | 확인 필요 |
| **이유** | 사용처 및 setter 사용 여부 확인 필요 |

### UserPageResponseDTO ❌

| 항목 | 내용 |
|------|------|
| **판단** | 확인 필요 |
| **이유** | 페이징 응답으로 record 적합할 수 있으나 별도 확인 필요 |

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
| `LoginRequest.java` | class → record |
| `TokenResponse.java` | class → record |
| `SocialUserDTO.java` | class → record |
| `AuthController.java` | getter → accessor |
| `AuthService.java` | builder → 생성자 |
| `OAuth2Service.java` | builder → 생성자 |
| `SocialUserConverter.java` | builder → 생성자, getter → accessor |

---

## 참고

- `docs/refactoring/dto-to-record.md` : record DTO 적용 방침, 장단점, 직렬화 흐름
