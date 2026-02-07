# Meetup 도메인 DTO → record 리팩토링

## 개요

Meetup 도메인 DTO 중 record 적용에 적합한 항목을 선별하여 리팩토링함.  
판단 기준: `docs/refactoring/dto-to-record.md` 적용 방침 참고.

---

## record로 전환한 DTO (1개)

### 1. MeetupParticipantsDTO

| 항목 | 내용 |
|------|------|
| **용도** | 모임 참여자 정보 응답 |
| **필드 수** | 4 (meetupIdx, userIdx, username, joinedAt) |
| **전환 이유** | Response 전용, 필드 4개로 단순, setter 미사용, Converter에서 builder로만 생성 |
| **사용처** | MeetupParticipantsConverter (toDTO), MeetupConverter (toDTO), MeetupController (getMeetupParticipants, joinMeetup) |

---

## record로 전환하지 않은 DTO (1개)

### MeetupDTO ❌

| 항목 | 내용 |
|------|------|
| **판단** | 부적합 |
| **이유** | 필드 **17개** → 생성자 과도하게 김. Request/Response 겸용 (`@RequestBody MeetupDTO`). `toEntity()` 존재하여 역방향 변환에서 getter 다수 사용 |

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
| `MeetupParticipantsDTO.java` | class → record |
| `MeetupParticipantsConverter.java` | builder → 생성자, getter → accessor |

---

## 참고

- `docs/refactoring/dto-to-record.md` : record DTO 적용 방침, 장단점, 직렬화 흐름
