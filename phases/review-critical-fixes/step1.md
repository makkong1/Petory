# Step 1 — WebSocket SUBSCRIBE 목적지 인가 (채팅 도청 차단)

## 배경

`WebSocketAuthChannelInterceptor`(`global/websocket/security/`)는 CONNECT/SUBSCRIBE/SEND에서 JWT **인증**만 수행하고, SUBSCRIBE의 **destination 검증이 없다**. simple broker(`enableSimpleBroker("/topic", "/queue", "/user")`)는 구독자 전원에게 브로드캐스트하므로, 인증된 사용자라면 누구나:

1. 참여하지 않은 대화방 `/topic/conversation/{다른방idx}` 를 구독해 **타인의 1:1/그룹 채팅을 실시간 수신** 가능
2. `/user/{타인loginId}/queue/errors` 를 구독해 타인의 에러 메시지 수신 가능
   (`ChatWebSocketController`가 `convertAndSendToUser`가 아닌 raw `convertAndSend("/user/" + principal.getName() + "/queue/errors", ...)`로 보내기 때문에 이 destination은 세션 스코프가 아닌 일반 broker destination임)

**대비**: SEND 경로는 `ChatMessageService.sendMessage`(line 45~52)에서 참여자 + ACTIVE 상태를 검증한다. 쓰기는 막혀있고 읽기(구독)만 뚫려있는 비대칭 상태.

## 현재 브로드캐스트 destination 목록 (`ChatWebSocketController`)

| destination | 내용 |
|---|---|
| `/topic/conversation/{idx}` | 채팅 메시지 (line 75) |
| `/topic/conversation/{idx}/typing` | 타이핑 상태 (line 144) |
| `/user/{loginId}/queue/errors` | 개인 에러 메시지 (line 86, raw convertAndSend) |

## 수정 대상 파일

- `backend/main/java/com/linkup/Petory/global/websocket/security/WebSocketAuthChannelInterceptor.java`
- (신규) `backend/test/java/com/linkup/Petory/global/websocket/security/WebSocketAuthChannelInterceptorTest.java`

## 수정 내용

### 1. WebSocketAuthChannelInterceptor — SUBSCRIBE destination 인가 추가

기존 인증 로직이 끝난 뒤(auth가 확보된 상태), `command == StompCommand.SUBSCRIBE`일 때 destination을 검증한다:

```java
// 인증 확보 후, SUBSCRIBE에 한해 destination 인가 검증
if (command == StompCommand.SUBSCRIBE && !isSubscriptionAuthorized(accessor)) {
    log.warn("WebSocket 구독 인가 실패: userId={}, destination={}",
            sessionUserId(accessor), accessor.getDestination());
    return null; // 구독 차단
}
```

`isSubscriptionAuthorized` 로직:

```java
private boolean isSubscriptionAuthorized(StompHeaderAccessor accessor) {
    String destination = accessor.getDestination();
    String loginId = sessionUserId(accessor); // 세션 attrs의 "userId" (loginId 문자열)
    if (destination == null || loginId == null) {
        return false;
    }

    // 1) /topic/conversation/{idx}[/suffix] → ACTIVE 참여자만 구독 허용
    Long conversationIdx = parseConversationIdx(destination);
    if (conversationIdx != null) {
        Long userIdx = usersRepository.findByIdString(loginId)
                .map(Users::getIdx).orElse(null);
        if (userIdx == null) {
            return false;
        }
        return participantRepository.findByConversationIdxAndUserIdx(conversationIdx, userIdx)
                .filter(p -> p.getStatus() == ParticipantStatus.ACTIVE)
                .filter(p -> !Boolean.TRUE.equals(p.getIsDeleted()))
                .isPresent();
    }

    // 2) /user/{loginId}/... → 본인 것만 구독 허용
    if (destination.startsWith("/user/")) {
        return destination.startsWith("/user/" + loginId + "/");
    }

    // 3) 그 외 destination은 기존 동작 유지 (허용)
    return true;
}

private Long parseConversationIdx(String destination) {
    String prefix = "/topic/conversation/";
    if (!destination.startsWith(prefix)) {
        return null;
    }
    String rest = destination.substring(prefix.length());
    int slash = rest.indexOf('/');
    String idxPart = slash >= 0 ? rest.substring(0, slash) : rest;
    try {
        return Long.parseLong(idxPart);
    } catch (NumberFormatException e) {
        return null; // 파싱 불가 → null 반환 → 위에서 /topic/conversation 아닌 것으로 처리되지 않도록 아래 참고
    }
}
```

> **주의**: `parseConversationIdx`가 `/topic/conversation/abc` 같은 비정상 destination에서 null을 반환하면 "그 외 허용" 분기로 빠진다. `/topic/conversation/` prefix인데 idx 파싱이 실패하면 **차단**해야 한다 — 구현 시 `startsWith(prefix) && idx == null → return false` 분기를 넣을 것.

### 2. 의존성 주입

인터셉터에 추가 주입:
- `ConversationParticipantRepository` (`domain/chat/repository/` — `findByConversationIdxAndUserIdx(Long, Long)` 이미 존재, line 54)
- `UsersRepository` (`findByIdString(String)` 이미 존재)

`@RequiredArgsConstructor` 필드 추가로 처리. **순환 의존 주의**: 인터셉터가 WebSocket config에서 등록되므로, repository 주입이 순환을 만들면 `ObjectProvider<T>`로 지연 주입한다 (기존 프로젝트에서 순환 문제가 없으면 그대로 필드 주입).

### 3. 단위 테스트 (신규)

`WebSocketAuthChannelInterceptorTest` — Mockito 기반, 최소 4케이스:

1. **참여자(ACTIVE)의 conversation 구독** → 메시지 통과
2. **비참여자의 conversation 구독** → `null` 반환 (차단)
3. **타인의 `/user/{other}/queue/errors` 구독** → `null` 반환 (차단)
4. **본인의 `/user/{me}/queue/errors` 구독** → 통과

테스트 작성 패턴: `StompHeaderAccessor.create(StompCommand.SUBSCRIBE)` → `setDestination(...)`, `setSessionAttributes(Map.of("userId", "...", "authentication", mockAuth))` → `MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders())` → `interceptor.preSend(message, channel)` 결과 검증.

## 가드레일

- 기존 인증 로직(CONNECT/SEND 처리, 세션 attrs 저장)은 **변경하지 않는다**. SUBSCRIBE 인가 검증만 추가.
- `ChatWebSocketController`의 에러 발송 방식(raw convertAndSend)은 이 step에서 바꾸지 않는다 — 구독 차단으로 충분하며, 발송 방식 변경은 프론트 구독 경로 변경을 수반하므로 scope 밖.
- CLAUDE.md 외과적 변경 원칙: 무관한 리팩터 금지.

## AC (Acceptance Criteria)

```bash
./gradlew compileJava
./gradlew test --tests '*WebSocketAuthChannelInterceptor*'
./gradlew test --tests '*Chat*'   # 기존 채팅 테스트 회귀 확인 (MySQL+Redis 필요)
```
