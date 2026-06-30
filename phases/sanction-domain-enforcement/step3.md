# Step 3 — Chat 서비스 레벨 제재 가드 (REST + WebSocket 통합 차단)

## 목적

제재 전에 열린 WebSocket 세션은 JWT 필터를 이미 통과한 상태라서 `JwtAuthenticationFilter`의 SUSPENDED 차단이 적용되지 않는다. REST와 STOMP 메시지 전송 모두 `ChatMessageService.sendMessage()`를 거치므로 이 서비스 레이어에서 최신 사용자 상태를 확인해 차단한다.

## 배경 정책

- SUSPENDED 또는 BANNED 사용자의 메시지 전송 차단
- 기존 채팅 메시지는 삭제·마스킹 없이 유지
- Redis/TTL 세션 강제 종료 전략은 2차 과제. 1차는 메시지 전송 시점 체크로 충분

## 변경 파일

### 1. `ChatForbiddenException.java` — 팩토리 메서드 추가
경로: `backend/main/java/com/linkup/Petory/domain/chat/exception/ChatForbiddenException.java`

기존 `deletedUserCannotSend()` 아래에 추가:
```java
public static ChatForbiddenException sanctionedUserCannotSend() {
    return new ChatForbiddenException("제재된 사용자는 메시지를 보낼 수 없습니다.");
}
```

### 2. `ChatMessageService.java` — sendMessage()에 제재 가드 추가
경로: `backend/main/java/com/linkup/Petory/domain/chat/service/ChatMessageService.java`

`sendMessage()` 내부, 기존 `isDeleted` 체크 직후에 추가:

```java
// 기존 코드 (수정 없음):
if (Boolean.TRUE.equals(sender.getIsDeleted())) {
    throw ChatForbiddenException.deletedUserCannotSend();
}

// 추가할 코드 — 항상 최신 상태를 DB에서 가져오므로 별도 재조회 불필요
if (sender.isSanctioned()) {
    throw ChatForbiddenException.sanctionedUserCannotSend();
}
```

`sender`는 `usersRepository.findById(senderIdx)`로 이미 DB에서 최신 상태를 로드한다. `isSanctioned()`는 `Users` 엔티티의 기존 메서드다:
```java
// Users.isSanctioned() 참고 (수정 불필요)
public boolean isSanctioned() {
    if (status == UserStatus.BANNED) return true;
    if (status == UserStatus.SUSPENDED && suspendedUntil != null) {
        return LocalDateTime.now().isBefore(suspendedUntil);
    }
    return false;
}
```

## 커버 범위

- `POST /api/chat/conversations/{id}/messages` (REST) → `ChatMessageController` → `ChatMessageService.sendMessage()` → 차단
- STOMP `/app/chat.send` → `ChatWebSocketController` → `ChatMessageService.sendMessage()` → 차단
- 기존 WebSocket 세션에서 전송 시 → 서비스 레이어에서 최신 DB 상태 확인 후 차단

## AC (Acceptance Criteria)

```bash
# 컴파일 통과
./gradlew compileJava

# 동작 확인 포인트:
# - SUSPENDED 사용자가 sendMessage() 호출 시 ChatForbiddenException (HTTP 403) 발생
# - BANNED 사용자가 sendMessage() 호출 시 ChatForbiddenException (HTTP 403) 발생
# - ACTIVE 사용자는 기존과 동일하게 메시지 전송 가능
```
