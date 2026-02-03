# Chat 도메인 DTO → record 리팩토링

## 개요

Chat 도메인 DTO 중 record 적용에 적합한 항목을 선별하여 리팩토링함.  
판단 기준: `docs/refactoring/dto-to-record.md` 적용 방침 참고.

---

## record로 전환한 DTO (2개)

### 1. CreateConversationRequest

| 항목 | 내용 |
|------|------|
| **용도** | 채팅방 생성 요청 (`@RequestBody`) |
| **필드 수** | 5 (conversationType, relatedType, relatedIdx, title, participantUserIds) |
| **전환 이유** | Request 전용, 필드 적음, setter 미사용, Jackson 역직렬화 정상 동작 |
| **사용처** | ConversationController (createConversation) |

### 2. SendMessageRequest

| 항목 | 내용 |
|------|------|
| **용도** | 메시지 전송 요청 (`@RequestBody`) |
| **필드 수** | 4 (conversationIdx, content, messageType, replyToMessageIdx) |
| **전환 이유** | Request 전용, 필드 4개로 단순, setter 미사용 |
| **사용처** | ChatMessageController (sendMessage) |

---

## record로 전환하지 않은 DTO (3개)

### ChatMessageDTO ❌

| 항목 | 내용 |
|------|------|
| **판단** | 부적합 |
| **이유** | 필드 15개 → 생성자 과도하게 김. 자기 참조(replyToMessage), 중첩 구조(List<FileDTO>) 포함 |

### ConversationDTO ❌

| 항목 | 내용 |
|------|------|
| **판단** | 부적합 |
| **이유** | Service에서 **setter 3곳 사용** (`setUnreadCount`, `setParticipants`, `setLastMessage`). N+1 최적화를 위한 배치 조회 후 setter로 조립하는 패턴 |

### ConversationParticipantDTO ❌

| 항목 | 내용 |
|------|------|
| **판단** | 부적합 |
| **이유** | 필드 **18개** → 생성자 너무 길어짐 |

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
| `CreateConversationRequest.java` | class → record |
| `SendMessageRequest.java` | class → record |
| `ConversationController.java` | getter 호출 방식 변경 (5곳) |
| `ChatMessageController.java` | getter 호출 방식 변경 (4곳) |

---

## 참고

- `docs/refactoring/dto-to-record.md` : record DTO 적용 방침, 장단점, 직렬화 흐름
