# Step 6 — Chat DTO 안내 플래그 + 도메인 액션 차단

## 목적

채팅 관련 조회 응답에 "제재된 참여자 존재 여부" 플래그를 추가하고, 제재 사용자가 케어 거래 확정(`confirmCareDeal`)을 시도하는 것을 차단한다.

**전제**: Step 3(Chat 전송 차단)이 완료되어 `ChatForbiddenException.sanctionedUserCannotSend()`가 있어야 한다.

## 배경 정책

- "제재된 사용자" 안내는 실시간 push 아닌 조회 응답에 플래그로 포함한다.
- `hasSanctionedParticipant: true` 플래그를 프론트엔드가 받아 UI에서 안내 표시 결정.
- 기존 채팅 메시지는 삭제·마스킹 없이 유지. 채팅방은 닫지 않는다.
- `confirmCareDeal`: 제재 사용자(요청자 또는 제공자)가 시도하면 403.

## 변경 파일

### 1. `ConversationDTO.java` — 안내 플래그 추가
경로: `backend/main/java/com/linkup/Petory/domain/chat/dto/ConversationDTO.java`

기존 DTO 필드 아래에 추가:
```java
/** 채팅방 참여자 중 제재 중인 사용자가 있을 때 true. 프론트엔드 안내 표시용. */
private boolean hasSanctionedParticipant;
```

Lombok `@Builder` 또는 `@Setter`가 있는 구조에 맞게 필드를 추가한다.

### 2. `ConversationConverter.java` 또는 `ConversationService.java` — 플래그 산출
경로: `backend/main/java/com/linkup/Petory/domain/chat/converter/ConversationConverter.java` (또는 해당 DTO 생성 위치)

`ConversationDTO`를 생성하는 시점에 참여자 제재 여부를 확인한다:

```java
// Conversation → ConversationDTO 변환 시 추가
boolean hasSanctionedParticipant = conversation.getParticipants().stream()
        .anyMatch(p -> p.getStatus() == ParticipantStatus.ACTIVE
                && p.getUser().isSanctioned());
dto.setHasSanctionedParticipant(hasSanctionedParticipant);
```

`Conversation.getParticipants()`가 지연 로딩이라면, 해당 메서드가 트랜잭션 안에서 호출되는지 확인. 지연 로딩 문제가 있으면 `ConversationService`에서 명시적으로 참여자를 조회한 후 플래그를 설정한다.

실제 컨버터/서비스 구조를 확인 후 DTO 생성 위치에 정확히 삽입한다.

### 3. `ChatForbiddenException.java` — 거래 확정 차단용 팩토리 추가
경로: `backend/main/java/com/linkup/Petory/domain/chat/exception/ChatForbiddenException.java`

```java
public static ChatForbiddenException sanctionedPartyCannotConfirmDeal() {
    return new ChatForbiddenException("제재된 사용자가 포함된 케어는 거래 확정을 할 수 없습니다.");
}
```

### 4. `ConversationService.java` — confirmCareDeal 제재 차단
경로: `backend/main/java/com/linkup/Petory/domain/chat/service/ConversationService.java`

`confirmCareDeal()` 메서드(또는 거래 확정 처리 메서드) 내부에서, 요청자/제공자 로드 후 제재 검사 추가:

```java
// 거래 확정 관련 사용자(요청자, 제공자) 로드 후
if (requester.isSanctioned() || provider.isSanctioned()) {
    throw ChatForbiddenException.sanctionedPartyCannotConfirmDeal();
}
```

`confirmCareDeal()` 내부 코드 흐름을 읽고 requester/provider 변수를 정확히 확인 후 삽입 위치를 결정한다.

## 주의사항

- `hasSanctionedParticipant` 산출 시 `ParticipantStatus.ACTIVE` 필터를 붙여야 한다. 이미 LEFT/INACTIVE인 제재 사용자의 참여 이력까지 포함하면 오탐이 발생한다.
- `user.isSanctioned()` 호출이 N+1 문제를 일으키지 않도록 주의. 참여자 목록을 이미 fetch하고 있다면 추가 쿼리가 발생하지 않는다.
- ConversationDTO 빌더 패턴(`@Builder`)을 쓰는 경우 `hasSanctionedParticipant`를 `@Builder.Default`로 false 처리한다.

## AC (Acceptance Criteria)

```bash
# 컴파일 통과
./gradlew compileJava

# 확인 포인트:
# - 채팅방 목록/상세 응답의 JSON에 hasSanctionedParticipant 필드 존재
# - 제재 사용자가 포함된 채팅방: hasSanctionedParticipant = true
# - 정상 사용자끼리만 있는 채팅방: hasSanctionedParticipant = false
# - 제재 사용자(요청자 또는 제공자)가 confirmCareDeal 시도 시 403
```
