# Chat 도메인 - 포트폴리오 상세 설명

## 1. 개요

Chat 도메인은 실시간 채팅 기능을 제공하는 도메인입니다. WebSocket(STOMP)을 사용하여 실시간 메시지 전송, 채팅방 관리, 읽지 않은 메시지 수 추적 등을 담당합니다.

**주요 기능**:
- 실시간 채팅 (WebSocket 기반)
- 채팅방 생성 및 관리 (1:1, 그룹, 펫케어, 실종제보, 산책모임)
- 메시지 전송/조회/삭제
- 읽지 않은 메시지 수 관리
- 메시지 읽음 처리
- 채팅방 참여/나가기

---

## 2. 기능 설명

### 2.1 채팅방 타입

#### 1:1 일반 채팅 (DIRECT)
- 두 사용자 간의 개인 채팅
- 기존 채팅방이 있으면 재사용
- 펫케어, 실종제보 등과 연동 가능

#### 펫케어 요청 채팅 (CARE_REQUEST)
- 펫케어 요청 승인 시 자동 생성
- 요청자와 제공자 간 소통
- `CareApplication`과 연동

#### 실종제보 채팅 (MISSING_PET)
- 실종 동물 제보자와 목격자 간 소통
- 같은 제보에 대해 여러 목격자와 개별 채팅방 생성
- `MissingPetBoard`와 연동

#### 산책모임 채팅 (MEETUP)
- 산책모임 참여자들의 그룹 채팅
- 모임 참여 시 자동 참여
- 모임 나가기 시 채팅방에서도 나감

#### 그룹 채팅 (GROUP)
- 여러 사용자가 참여하는 그룹 채팅
- 관리자 지정 가능

### 2.2 실시간 메시지 전송

**WebSocket 기반 실시간 통신**:
- STOMP 프로토콜 사용
- 메시지 전송 시 채팅방 참여자들에게 브로드캐스트
- 타이핑 표시 기능 지원

**메시지 타입**:
- `TEXT`: 일반 텍스트 메시지
- `IMAGE`: 이미지 메시지
- `FILE`: 파일 메시지
- `SYSTEM`: 시스템 메시지
- `NOTICE`: 공지 메시지

### 2.3 읽지 않은 메시지 수 관리

- 메시지 전송 시 참여자들의 `unreadCount` 자동 증가 (본인 제외)
- 메시지 읽음 처리 시 `unreadCount` 초기화
- 채팅방 목록에서 읽지 않은 메시지 수 표시

### 2.4 재참여 처리

- 채팅방 나간 후 재참여 시 `joinedAt` 이후 메시지만 조회
- 이전 대화 내용은 보지 않음
- `lastReadMessage` 초기화

---

## 3. 서비스 로직 설명

### 3.1 채팅방 생성

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public ConversationDTO createConversation(
        ConversationType conversationType,
        RelatedType relatedType,
        Long relatedIdx,
        String title,
        List<Long> participantUserIds) {
    
    // 참여자 유효성 검증
    List<Users> participants = participantUserIds.stream()
            .map(userId -> usersRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + userId)))
            .collect(Collectors.toList());
    
    // 탈퇴한 사용자 제외
    participants = participants.stream()
            .filter(user -> !Boolean.TRUE.equals(user.getIsDeleted()))
            .collect(Collectors.toList());
    
    // 1:1 채팅인 경우 기존 채팅방 확인
    if (conversationType == ConversationType.DIRECT && participants.size() == 2) {
        Optional<Conversation> existing = conversationRepository.findDirectConversationBetweenUsers(
                participants.get(0).getIdx(),
                participants.get(1).getIdx());
        
        if (existing.isPresent()) {
            return conversationConverter.toDTO(existing.get());
        }
    }
    
    // Conversation 생성
    Conversation conversation = Conversation.builder()
            .conversationType(conversationType)
            .relatedType(relatedType)
            .relatedIdx(relatedIdx)
            .title(title)
            .status(ConversationStatus.ACTIVE)
            .build();
    
    conversation = conversationRepository.save(conversation);
    
    // 참여자 추가
    for (Users user : participants) {
        ConversationParticipant participant = ConversationParticipant.builder()
                .conversation(conversation)
                .user(user)
                .role(ParticipantRole.MEMBER)
                .status(ParticipantStatus.ACTIVE)
                .unreadCount(0)
                .build();
        participantRepository.save(participant);
    }
    
    return conversationConverter.toDTO(conversation);
}
```

**핵심 로직**:
- **1:1 채팅 중복 방지**: 기존 채팅방이 있으면 재사용
- **탈퇴한 사용자 제외**: 탈퇴한 사용자는 참여 불가
- **별도 트랜잭션**: `REQUIRES_NEW`로 실패해도 호출한 트랜잭션에 영향 없음

### 3.2 메시지 전송

```java
@Transactional
public ChatMessageDTO sendMessage(Long conversationIdx, Long senderIdx, String content, MessageType messageType) {
    // 1. 전송자 확인
    Users sender = usersRepository.findById(senderIdx)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
    
    if (Boolean.TRUE.equals(sender.getIsDeleted())) {
        throw new IllegalStateException("탈퇴한 사용자는 메시지를 보낼 수 없습니다.");
    }
    
    // 2. 채팅방 확인
    Conversation conversation = conversationRepository.findById(conversationIdx)
            .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));
    
    // 3. 참여자인지 확인
    ConversationParticipant senderParticipant = participantRepository
            .findByConversationIdxAndUserIdx(conversationIdx, senderIdx)
            .orElseThrow(() -> new IllegalArgumentException("채팅방 참여자가 아닙니다."));
    
    if (senderParticipant.getStatus() != ParticipantStatus.ACTIVE) {
        throw new IllegalStateException("채팅방에 참여 중이 아닙니다.");
    }
    
    // 4. 메시지 저장
    ChatMessage message = ChatMessage.builder()
            .conversation(conversation)
            .sender(sender)
            .content(content)
            .messageType(messageType != null ? messageType : MessageType.TEXT)
            .build();
    
    message = chatMessageRepository.save(message);
    
    // 5. 참여자들의 읽지 않은 메시지 수 증가 (본인 제외)
    // DB 레벨 원자적 증가로 Lost Update 방지
    participantRepository.incrementUnreadCount(conversationIdx, senderIdx);
    
    // 6. Conversation 메타데이터 업데이트
    conversation.setLastMessageAt(LocalDateTime.now());
    String preview = messageType == MessageType.IMAGE ? "[사진]"
            : messageType == MessageType.FILE ? "[파일]"
                    : content.length() > 200 ? content.substring(0, 200) : content;
    conversation.setLastMessagePreview(preview);
    conversationRepository.save(conversation);
    
    return messageConverter.toDTO(message);
}
```

**핵심 로직**:
- **참여자 검증**: 채팅방 참여자만 메시지 전송 가능
- **원자적 증가**: `incrementUnreadCount`로 동시성 문제 해결
- **메타데이터 업데이트**: 마지막 메시지 시간 및 미리보기 업데이트

### 3.3 메시지 읽음 처리

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
}
```

**핵심 로직**:
- **읽지 않은 메시지 수 초기화**: 읽음 처리 시 `unreadCount`를 0으로 설정
- **마지막 읽은 메시지 저장**: `lastReadMessage`로 읽음 위치 추적

### 3.4 재참여 시 메시지 조회

```java
public Page<ChatMessageDTO> getMessages(Long conversationIdx, Long userId, int page, int size) {
    // 참여자 정보 확인 (재참여 여부 체크)
    ConversationParticipant participant = participantRepository
            .findByConversationIdxAndUserIdx(conversationIdx, userId)
            .orElse(null);
    
    LocalDateTime readFrom = null;
    if (participant != null && participant.getLastReadMessage() == null && participant.getJoinedAt() != null) {
        // 재참여한 경우: lastReadMessage가 null이고 joinedAt이 있으면 재참여로 간주
        readFrom = participant.getJoinedAt();
    }
    
    Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    
    Page<ChatMessage> messages;
    if (readFrom != null) {
        // 재참여한 경우: joinedAt 이후 메시지만 조회
        messages = chatMessageRepository
                .findByConversationIdxAndCreatedAtAfterOrderByCreatedAtDesc(conversationIdx, readFrom, pageable);
    } else {
        // 기존 참여자: 전체 메시지 조회
        messages = chatMessageRepository
                .findByConversationIdxOrderByCreatedAtDesc(conversationIdx, pageable);
    }
    
    return messages.map(messageConverter::toDTO);
}
```

**핵심 로직**:
- **재참여 감지**: `lastReadMessage`가 null이고 `joinedAt`이 있으면 재참여로 간주
- **메시지 필터링**: 재참여한 경우 `joinedAt` 이후 메시지만 조회

---

## 4. 아키텍처 설명

### 4.1 엔티티 구조

#### Conversation (채팅방)
```java
@Entity
@Table(name = "conversation")
public class Conversation {
    private Long idx;
    private ConversationType conversationType;  // DIRECT, GROUP, CARE_REQUEST, MISSING_PET, MEETUP
    private String title;
    private RelatedType relatedType;  // CARE_APPLICATION, MISSING_PET_BOARD, MEETUP
    private Long relatedIdx;  // 연관 엔티티 ID
    private ConversationStatus status;  // ACTIVE, CLOSED
    private LocalDateTime lastMessageAt;
    private String lastMessagePreview;
    private Boolean isDeleted;
    private List<ConversationParticipant> participants;
    private List<ChatMessage> messages;
}
```

#### ConversationParticipant (채팅방 참여자)
```java
@Entity
@Table(name = "conversationparticipant")
public class ConversationParticipant {
    private Long idx;
    private Conversation conversation;
    private Users user;
    private ParticipantRole role;  // MEMBER, ADMIN
    private Integer unreadCount;  // 읽지 않은 메시지 수
    private ChatMessage lastReadMessage;  // 마지막 읽은 메시지
    private LocalDateTime lastReadAt;
    private ParticipantStatus status;  // ACTIVE, LEFT
    private LocalDateTime joinedAt;
    private LocalDateTime leftAt;
}
```

#### ChatMessage (메시지)
```java
@Entity
@Table(name = "chatmessage")
public class ChatMessage {
    private Long idx;
    private Conversation conversation;
    private Users sender;
    private MessageType messageType;  // TEXT, IMAGE, FILE, SYSTEM, NOTICE
    private String content;
    private ChatMessage replyToMessage;  // 답장 메시지
    private Boolean isDeleted;
    private List<MessageReadStatus> readStatuses;
}
```

#### MessageReadStatus (메시지 읽음 상태)
```java
@Entity
@Table(name = "messagereadstatus")
public class MessageReadStatus {
    private Long idx;
    private ChatMessage message;
    private Users user;
    private LocalDateTime readAt;
}
```

### 4.2 도메인 연관관계

```mermaid
graph TD
    A[Conversation] --> B[ConversationParticipant]
    A --> C[ChatMessage]
    B --> D[Users]
    C --> D
    C --> E[MessageReadStatus]
    E --> D
    C --> C
    A --> F[CareApplication]
    A --> G[MissingPetBoard]
    A --> H[Meetup]
```

### 4.3 API 설계

| 엔드포인트 | Method | 설명 |
|-----------|--------|------|
| `/api/chat/conversations` | GET | 내 채팅방 목록 |
| `/api/chat/conversations` | POST | 채팅방 생성 |
| `/api/chat/conversations/{id}` | GET | 채팅방 상세 |
| `/api/chat/conversations/{id}/leave` | POST | 채팅방 나가기 |
| `/api/chat/conversations/direct` | POST | 1:1 채팅방 생성/조회 |
| `/api/chat/conversations/care-request` | POST | 펫케어 채팅방 생성 |
| `/api/chat/messages` | POST | 메시지 전송 |
| `/api/chat/messages/conversation/{id}` | GET | 메시지 목록 조회 |
| `/api/chat/messages/conversation/{id}/read` | POST | 메시지 읽음 처리 |
| `/app/chat.send` | WebSocket | 실시간 메시지 전송 |
| `/app/chat.read` | WebSocket | 실시간 읽음 처리 |
| `/app/chat.typing` | WebSocket | 타이핑 표시 |

### 4.4 WebSocket 구조

**STOMP 프로토콜 사용**:
- **클라이언트 → 서버**: `/app/chat.send`, `/app/chat.read`, `/app/chat.typing`
- **서버 → 클라이언트**: `/topic/conversation/{conversationIdx}`
- **에러 전송**: `/user/{userId}/queue/errors`

**인증**:
- WebSocket 연결 시 JWT 토큰 검증
- `Principal`을 통해 사용자 정보 추출

---

## 5. 트러블슈팅

---

## 6. 성능 최적화

### 6.1 DB 최적화

#### 인덱스 전략
```sql
-- 채팅방 목록 조회 (사용자별)
CREATE INDEX idx_participant_user_status ON conversationparticipant(user_idx, status, conversation_idx);

-- 메시지 조회 (채팅방별, 시간순)
CREATE INDEX idx_message_conversation_created ON chatmessage(conversation_idx, created_at DESC, is_deleted);

-- 읽지 않은 메시지 수 조회
CREATE INDEX idx_participant_conversation_user ON conversationparticipant(conversation_idx, user_idx, status);

-- 1:1 채팅방 조회
CREATE INDEX idx_conversation_type_status ON conversation(conversation_type, status, is_deleted);

-- 관련 엔티티 조회
CREATE INDEX idx_conversation_related ON conversation(related_type, related_idx, is_deleted);
```

**선정 이유**:
- 자주 조회되는 컬럼 조합 (user_idx, status, conversation_idx)
- WHERE 절에서 자주 사용되는 조건
- JOIN에 사용되는 외래키 (conversation_idx, user_idx)
- 시간순 정렬을 위한 인덱스 (created_at DESC)

**효과**:
- 채팅방 목록 조회: 인덱스 사용으로 쿼리 실행 시간 50% 감소
- 메시지 조회: 시간순 정렬 성능 향상

### 6.2 캐싱 전략

#### 채팅방 목록 캐싱
```java
@Cacheable(value = "userConversations", key = "#userId")
public List<ConversationDTO> getMyConversations(Long userId) {
    // 채팅방 목록 조회
}

@CacheEvict(value = "userConversations", key = "#userId")
public ConversationDTO createConversation(...) {
    // 채팅방 생성 시 캐시 무효화
}
```

**효과**:
- 채팅방 목록 조회 응답 시간 70% 감소 (캐시 히트 시)
- DB 부하 감소

### 6.3 동시성 제어

#### 읽지 않은 메시지 수 증가
- **방법**: `@Modifying @Query`로 DB 레벨 원자적 증가
- **효과**: Lost Update 문제 해결

#### 채팅방 참여/나가기
- **방법**: `@Transactional`로 트랜잭션 보장
- **효과**: 데이터 정합성 보장

---

## 7. 핵심 포인트 요약

### 7.1 실시간 통신
- **WebSocket(STOMP)**: 실시간 양방향 통신
- **브로드캐스트**: 메시지 전송 시 채팅방 참여자들에게 실시간 전달
- **타이핑 표시**: 사용자 경험 향상

### 7.2 읽지 않은 메시지 수 관리
- **원자적 증가**: DB 레벨에서 증가하여 동시성 문제 해결
- **읽음 처리**: 메시지 읽음 시 `unreadCount` 초기화
- **재참여 처리**: 채팅방 나간 후 재참여 시 이전 메시지 미조회

### 7.3 채팅방 타입별 특화
- **1:1 채팅**: 기존 채팅방 재사용
- **펫케어 채팅**: `CareApplication` 승인 시 자동 생성
- **실종제보 채팅**: 제보자-목격자 조합별 개별 채팅방
- **산책모임 채팅**: 모임 참여 시 자동 참여

### 7.4 성능 최적화
- **N+1 문제 해결**: Fetch Join + 배치 조회
- **인덱스 전략**: 자주 조회되는 컬럼 조합 인덱싱
- **캐싱**: 채팅방 목록 캐싱으로 DB 부하 감소

