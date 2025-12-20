# 채팅 메시지 읽음 처리 성능 문제

## 📋 요약

**문제**: 메시지 읽음 처리 시 전체 메시지 조회 및 프론트엔드 빈번한 호출로 인한 성능 저하

**해결 방안**:
- 불필요한 로직 제거 (MessageReadStatus 기록 로직)
- 읽음 처리 단순화
- 프론트엔드 최적화 (디바운싱)
- 인덱스 추가

---

## 1. 문제 상황

### 1.1 비효율적인 전체 메시지 조회

**위치**: `ChatMessageService.markAsRead()` (line 171-189)

**문제 코드**:
```java
// ⚠️ 문제: 채팅방의 모든 메시지를 조회하고 Java에서 필터링
List<ChatMessage> unreadMessages = chatMessageRepository
    .findByConversationIdxOrderByCreatedAtDesc(conversationIdx)  // 전체 조회!
    .stream()
    .filter(m -> m.getCreatedAt().isBefore(
            chatMessageRepository.findById(lastMessageIdx)
                .map(ChatMessage::getCreatedAt)
                .orElse(LocalDateTime.now()))
        && !m.getSender().getIdx().equals(userId))
    .collect(Collectors.toList());

// MessageReadStatus 기록 (실제로는 사용 안 함 - 주석 처리됨)
for (ChatMessage message : unreadMessages) {
    if (!readStatusRepository.existsByMessageAndUser(message, user)) {
        // readStatusRepository.save(...); // 주석 처리됨
    }
}
```

**문제점**:
- 채팅방의 모든 메시지를 조회 (수천~수만 건 가능)
- Java에서 스트림으로 필터링 (DB 부하 + 메모리 사용)
- MessageReadStatus 기록 로직이 있지만 실제로 사용 안 함
- `findById`를 반복 호출하여 추가 쿼리 발생

**영향**:
- 메시지가 많을수록 성능 급격히 저하
- DB 부하 증가
- 메모리 사용량 증가

### 1.2 프론트엔드에서 빈번한 호출

**위치**: `ChatRoom.js`

**호출 시점**:
1. 메시지 조회 후 (line 56)
2. WebSocket으로 새 메시지 수신 시 (line 170)
3. 이미지 전송 후 (line 243, 248)
4. 텍스트 메시지 전송 후 (line 287, 292)

**문제**:
- 읽음 처리가 매우 빈번하게 발생
- 사용자가 채팅방에 있을 때마다 계속 호출
- DB 부하 증가 및 응답 속도 저하

### 1.3 트랜잭션 범위가 넓음

**현재 구조**:
```
참여자 조회 → 메시지 전체 조회 → 필터링 → 반복 처리 → 저장
```

**문제**:
- 불필요한 로직 포함 (MessageReadStatus는 사용 안 함)
- 트랜잭션 범위가 넓어 Lock 유지 시간 증가

---

## 2. 해결 방안

### 2.1 불필요한 로직 제거

**현재**: MessageReadStatus 기록 로직이 있지만 주석 처리되어 사용 안 함

**개선**: 해당 로직 완전 제거

```java
@Transactional
public void markAsRead(Long conversationIdx, Long userId, Long lastMessageIdx) {
    // 참여자 확인
    ConversationParticipant participant = participantRepository
            .findByConversationIdxAndUserIdx(conversationIdx, userId)
            .orElseThrow(() -> new IllegalArgumentException("채팅방 참여자가 아닙니다."));

    // 읽지 않은 메시지 수 초기화
    participant.setUnreadCount(0);
    
    if (lastMessageIdx != null) {
        ChatMessage lastMessage = chatMessageRepository.findById(lastMessageIdx)
                .orElse(null);
        if (lastMessage != null) {
            participant.setLastReadMessage(lastMessage);
            participant.setLastReadAt(LocalDateTime.now());
        }
    }
    
    participantRepository.save(participant);
    
    // ⚠️ 제거: MessageReadStatus 기록 로직 (사용 안 함)
}
```

**효과**: 불필요한 전체 메시지 조회 제거, 트랜잭션 범위 축소

### 2.2 프론트엔드 최적화: 디바운싱 적용

**현재**: 읽음 처리가 즉시 호출됨

**개선**: 디바운싱으로 호출 빈도 감소

```javascript
// ChatRoom.js
const markAsReadDebounced = useMemo(
    () => debounce((conversationIdx, userId, lastMessageIdx) => {
        markAsRead(conversationIdx, userId, lastMessageIdx).catch(err => {
            console.error('읽음 처리 실패:', err);
        });
    }, 1000), // 1초 디바운싱
    []
);

// 사용 예시
useEffect(() => {
    if (messages.length > 0) {
        const lastMessage = messages[messages.length - 1];
        markAsReadDebounced(conversationIdx, user.idx, lastMessage.idx);
    }
}, [messages, conversationIdx, user.idx]);
```

**효과**: 읽음 처리 호출 빈도 감소 (초당 최대 1회)

### 2.3 인덱스 추가

**필수 인덱스**:
```sql
-- 메시지 조회 성능 향상 (가장 중요)
CREATE INDEX idx_chat_message_conversation_created 
ON chat_message(conversation_idx, created_at DESC);

-- 참가자 조회 성능 향상
CREATE UNIQUE INDEX idx_conversation_participant_unique 
ON conversation_participant(conversation_idx, user_idx);
```

**추가 인덱스**:
```sql
CREATE INDEX idx_chat_message_sender ON chat_message(sender_idx);
CREATE INDEX idx_chat_message_deleted ON chat_message(is_deleted);
CREATE INDEX idx_conversation_participant_conversation 
ON conversation_participant(conversation_idx);
CREATE INDEX idx_conversation_participant_user 
ON conversation_participant(user_idx);
```

---

## 3. 해결 완료

### 3.1 실제 구현 완료

**수정 파일**: `backend/main/java/com/linkup/Petory/domain/chat/service/ChatMessageService.java`

**수정 내용**:
1. ✅ **불필요한 전체 메시지 조회 로직 제거** (171-189줄)
   - `findByConversationIdxOrderByCreatedAtDesc()` 전체 조회 제거
   - Java 스트림 필터링 로직 제거
   - MessageReadStatus 기록 로직 제거 (사용하지 않음)

2. ✅ **불필요한 의존성 제거**
   - `MessageReadStatusRepository` 필드 및 import 제거
   - 사용하지 않는 `Collectors` import는 다른 메서드에서 사용하므로 유지

**최종 코드**:
```java
@Transactional
public void markAsRead(Long conversationIdx, Long userId, Long lastMessageIdx) {
    // 참여자 확인
    ConversationParticipant participant = participantRepository
            .findByConversationIdxAndUserIdx(conversationIdx, userId)
            .orElseThrow(() -> new IllegalArgumentException("채팅방 참여자가 아닙니다."));

    // 읽지 않은 메시지 수 초기화
    participant.setUnreadCount(0);
    if (lastMessageIdx != null) {
        ChatMessage lastMessage = chatMessageRepository.findById(lastMessageIdx)
                .orElse(null);
        if (lastMessage != null) {
            participant.setLastReadMessage(lastMessage);
            participant.setLastReadAt(LocalDateTime.now());
        }
    }
    participantRepository.save(participant);
    
    // ⚠️ 제거됨: 불필요한 전체 메시지 조회 및 MessageReadStatus 기록 로직
    // - 전체 메시지 조회는 성능 문제를 일으킴 (수천~수만 건 조회)
    // - MessageReadStatus 기록 로직은 실제로 사용되지 않음
    // - 참여자의 unreadCount와 lastReadMessage 업데이트만으로 충분함
}
```

**해결한 문제**:
- ✅ 채팅방의 모든 메시지를 조회하는 비효율적인 쿼리 제거
- ✅ Java에서 대량 데이터를 필터링하는 로직 제거
- ✅ 사용하지 않는 MessageReadStatus 기록 로직 제거
- ✅ 불필요한 DB 부하 제거
- ✅ 메모리 사용량 감소
- ✅ 트랜잭션 범위 축소로 Lock 유지 시간 단축

**예상 효과**:
- 메시지 7,000건 기준: 전체 조회 쿼리 1개 제거 (약 수백 ms ~ 수 초 절약)
- 메모리 사용량: 전체 메시지 로드 제거로 대폭 감소
- DB 부하: 대량 조회 쿼리 제거로 부하 감소
- 응답 시간: 읽음 처리 시간 단축 (필수 로직만 실행)

---

## 4. 결과 (예상)

### 4.1 Before/After 비교

| 항목 | Before | After |
|------|--------|-------|
| **메시지 조회** | 전체 메시지 조회 (수천~수만 건) | ✅ 불필요한 조회 제거 |
| **Java 필터링** | 스트림으로 대량 데이터 필터링 | ✅ 제거 |
| **MessageReadStatus** | 사용 안 하는 로직 포함 | ✅ 제거 |
| **트랜잭션 범위** | 넓음 (불필요한 로직 포함) | ✅ 좁음 (필수 로직만) |
| **DB 부하** | 높음 (대량 조회) | ✅ 낮음 (필수 쿼리만) |
| **메모리 사용** | 전체 메시지 로드 | ✅ 최소화 |

### 4.2 성능 개선 효과

**구현 완료**:
- ✅ **메시지 조회 제거**: 메시지 7,000건 기준 → 쿼리 1개 제거, 메모리 사용량 대폭 감소
- ✅ **코드 단순화**: 불필요한 로직 제거로 유지보수성 향상

**추가 개선 가능 사항** (필요 시):
- 프론트엔드 디바운싱 적용: 초당 5회 → 초당 1회 (80% 감소)
- 인덱스 추가: 메시지 조회 성능 향상 (10배 이상 개선 예상)

---

## 5. 핵심 포인트

### 구현 완료 항목
1. ✅ **높음**: 불필요한 MessageReadStatus 로직 제거 (완료)
   - 전체 메시지 조회 제거
   - Java 필터링 로직 제거
   - 사용하지 않는 의존성 제거

### 추가 개선 가능 항목 (필요 시)
2. **중간**: 프론트엔드 디바운싱 적용 (2.2 참고)
3. **중간**: 필수 인덱스 추가 (`idx_chat_message_conversation_created`) (2.3 참고)
4. **낮음**: 추가 인덱스 (성능 모니터링 후 결정)

### 해결 방법 요약

**문제**: `markAsRead()` 메서드에서 채팅방의 모든 메시지를 조회하고 Java에서 필터링하여 성능 저하 발생

**해결**: 
1. 불필요한 전체 메시지 조회 로직 완전 제거
2. MessageReadStatus 기록 로직 제거 (사용하지 않음)
3. 필수 로직만 유지 (참여자의 unreadCount, lastReadMessage 업데이트)

**효과**:
- 실행 시간 단축 (전체 메시지 조회 제거)
- 메모리 사용량 감소
- DB 부하 감소
- 코드 단순화 및 유지보수성 향상

### 주의사항
- MessageReadStatus는 현재 사용하지 않지만, 향후 기능 확장 시 필요할 수 있음 (필요 시 별도 구현)
- 인덱스 추가 전 현재 인덱스 상태 확인 필요 (추가 최적화 시)
- 프론트엔드 디바운싱은 사용자 경험에 영향 없도록 적절한 시간 설정 (필요 시)
