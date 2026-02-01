# Report 도메인 DTO → record 리팩토링

## 개요

Report 도메인 DTO 중 record 적용에 적합한 항목을 선별하여 리팩토링함.  
판단 기준: `docs/refactoring/dto-to-record.md` 적용 방침 참고.

---

## record로 전환한 DTO (1개)

### 1. ReportRequestDTO

| 항목 | 내용 |
|------|------|
| **용도** | 신고 요청 (`@RequestBody`) |
| **필드 수** | 4 (targetType, targetIdx, reporterId, reason) |
| **전환 이유** | Request 전용, 필드 4개로 단순, setter 미사용, Jackson 역직렬화 정상 동작 |
| **사용처** | ReportController (createReport), ReportService (createReport) |

---

## record로 전환하지 않은 DTO (3개)

### ReportDTO ❌

| 항목 | 내용 |
|------|------|
| **판단** | 이미 불변 |
| **이유** | **`@Value`** 사용 → 이미 Lombok 불변 DTO. 필드 14개로 record 전환 시 생성자 길어짐 |

### ReportDetailDTO ❌

| 항목 | 내용 |
|------|------|
| **판단** | 이미 불변 |
| **이유** | **`@Value`** 사용 → 이미 Lombok 불변 DTO. 내부 static class `TargetPreview`도 `@Value` |

### ReportHandleRequest ❌

| 항목 | 내용 |
|------|------|
| **판단** | 부적합 |
| **이유** | `@Getter @Setter` 사용 → 가변 DTO. 전환 시 사용처 확인 필요 |

---

## 변경 사항 요약

| 변경 유형 | 내용 |
|----------|------|
| **DTO 정의** | Lombok `@Data` 제거 → `public record XxxDTO(...)` |
| **생성** | 직접 생성 또는 역직렬화 |
| **접근** | `dto.getXxx()` → `dto.xxx()` (record accessor) |

---

## 수정된 파일

| 파일 | 변경 내용 |
|-----|---------|
| `ReportRequestDTO.java` | class → record |
| `ReportService.java` | getter → accessor (11곳) |

---

## 참고

- `docs/refactoring/dto-to-record.md` : record DTO 적용 방침, 장단점, 직렬화 흐름
- `@Value`는 Lombok의 불변 클래스 생성 어노테이션으로, record와 유사한 역할
