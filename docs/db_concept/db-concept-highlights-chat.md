# DB 개념 어필 포인트 — Chat 도메인

> 코드베이스 실측 데이터 기준 (실제 파일 확인)
>
> 확인 파일: `domain/chat/service/ChatMessageService.java`, `ConversationService.java`, `ConversationCreatorService.java`, `repository/SpringDataJpaChatMessageRepository.java`, `SpringDataJpaConversationParticipantRepository.java`, `entity/ConversationParticipant.java`

---

## 1. DB 레벨 원자적 증가로 Lost Update 방지 (unreadCount)

### 어필 포인트

메시지 전송 시 수신자들의 `unreadCount`를 증가시키는 로직에서 **동시성 문제**가 발생할 수 있다. 여러 사용자가 동시에 메시지를 보내면 Java에서 조회 → 증가 → 저장 흐름은 Lost Update를 일으킨다.

실제 구현 (`SpringDataJpaConversationParticipantRepository`):

```java
@RepositoryMethod("채팅 참여자: 읽지 않은 메시지 수 증가")
@Modifying
@Query("UPDATE ConversationParticipant p SET p.unreadCount = p.unreadCount + 1 " +
       "WHERE p.conversation.idx = :conversationIdx AND p.user.idx != :senderUserId AND p.status = 'ACTIVE'")
void incrementUnreadCount(@Param("conversationIdx") Long conversationIdx,
                          @Param("senderUserId") Long senderUserId);
```

`ChatMessageService.sendMessage()`에서 호출:

```java
// 5. 참여자들의 읽지 않은 메시지 수 증가 (본인 제외)
// DB 레벨 원자적 증가로 Lost Update 방지
participantRepository.incrementUnreadCount(conversationIdx, senderIdx);
```

- 단일 `UPDATE` 쿼리로 채팅방 내 모든 ACTIVE 수신자의 카운트를 동시에 증가
- 발신자(`senderUserId`) 제외 조건이 WHERE 절에 포함
- Java 레벨 증가(조회 → 수정 → 저장)를 쓰지 않아 Lost Update 없음

### 말할 내용

> "메시지 전송 시 수신자들의 읽지 않은 메시지 수를 증가시켜야 하는데, Java에서 엔티티를 조회해 `unreadCount++` 후 저장하면 동시 메시지 전송 시 Lost Update가 발생합니다. 이를 `@Modifying @Query`로 단일 UPDATE 쿼리를 DB에 직접 날려 원자적으로 처리했습니다. WHERE 조건에 발신자 제외와 ACTIVE 상태 필터를 함께 걸어 불필요한 업데이트도 막았습니다."

---

## 2. 읽음 처리 최적화 — 불필요한 전체 메시지 조회 제거

### 어필 포인트

기존 코드는 읽음 처리 시 채팅방의 **전체 메시지를 조회**해서 `MessageReadStatus`를 하나씩 저장하는 로직이 있었다. 실제 서비스에서는 참여자의 `unreadCount`와 `lastReadMessage`만 업데이트하면 충분하다.

실제 구현 (`ChatMessageService.markAsRead()`):

```java
@Transactional
public void markAsRead(Long conversationIdx, Long userId, Long lastMessageIdx) {
    requireActiveParticipant(conversationIdx, userId);

    ConversationParticipant participant = participantRepository
            .findByConversationIdxAndUserIdx(conversationIdx, userId)
            .orElseThrow(ChatForbiddenException::notParticipant);

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

- `MessageReadStatus` 엔티티 제거 (엔티티 목록에 없음 — `ChatMessage`, `Conversation`, `ConversationParticipant` 3개만 존재)
- 읽음 처리 = `unreadCount` 0으로 초기화 + `lastReadMessage` / `lastReadAt` 갱신
- 트랜잭션 범위가 참여자 1건 조회·저장으로 최소화됨

### 말할 내용

> "처음에는 읽음 처리 시 채팅방의 모든 메시지를 조회해서 각 메시지에 MessageReadStatus 레코드를 남기는 구조였습니다. 메시지가 쌓일수록 수천 건을 한 번에 로드하는 성능 문제가 있었습니다. 실제로 필요한 데이터는 '어디까지 읽었는가'뿐이므로, 참여자 테이블의 `unreadCount`를 0으로, `lastReadMessage`를 마지막 메시지로 갱신하는 방식으로 변경했습니다. MessageReadStatus 테이블 자체를 제거해 구조를 단순화했습니다."

---

## 3. 채팅방 목록 조회 N+1 해결 — 배치 조회

### 어필 포인트

채팅방 목록(`getMyConversations()`)은 화면에 각 채팅방별 참여자 정보 + 마지막 메시지를 함께 보여줘야 한다. 채팅방 N개를 루프 돌며 각각 참여자와 메시지를 조회하면 N+1 문제가 발생한다.

실제 구현 (`ConversationService.getMyConversations()`):

```java
// 배치 조회: 현재 사용자의 참여자 정보 (읽지 않은 메시지 수 포함)
List<ConversationParticipant> myParticipants = participantRepository
        .findParticipantsByConversationIdxsAndUserIdx(conversationIdxs, userId);
Map<Long, ConversationParticipant> myParticipantMap = ...;

// 배치 조회: 모든 활성 참여자 정보
List<ConversationParticipant> allParticipants = participantRepository
        .findParticipantsByConversationIdxsAndStatus(conversationIdxs, ParticipantStatus.ACTIVE);
Map<Long, List<ConversationParticipant>> participantsMap = ...;

// 배치 조회: 각 채팅방의 최신 메시지
List<ChatMessage> latestMessages = chatMessageRepository
        .findLatestMessagesByConversationIdxs(conversationIdxs);
Map<Long, ChatMessage> latestMessageMap = ...;
```

쿼리 3개로 전체 채팅방 목록 구성:
1. 사용자 기준 참여자 정보 (IN 조건, JOIN FETCH)
2. 채팅방 기준 활성 참여자 전체 (IN 조건, JOIN FETCH)
3. 채팅방별 최신 메시지 (서브쿼리로 MAX(idx) 사용)

Repository 쿼리 (`findLatestMessagesByConversationIdxs`):

```java
@Query("SELECT m FROM ChatMessage m " +
       "JOIN FETCH m.sender s " +
       "WHERE m.conversation.idx IN :conversationIdxs " +
       "  AND m.isDeleted = false " +
       "  AND s.isDeleted = false " +
       "  AND m.idx IN (" +
       "    SELECT MAX(m2.idx) FROM ChatMessage m2 " +
       "    WHERE m2.conversation.idx = m.conversation.idx AND m2.isDeleted = false" +
       "  )")
List<ChatMessage> findLatestMessagesByConversationIdxs(@Param("conversationIdxs") List<Long> conversationIdxs);
```

### 말할 내용

> "채팅방 목록 조회 시 각 채팅방마다 참여자와 최신 메시지를 개별 조회하면 N+1 문제가 발생합니다. 채팅방 ID 목록을 먼저 추출한 뒤, IN 조건 배치 쿼리 3개(내 참여자 정보, 전체 활성 참여자, 최신 메시지)로 한 번에 로드하고 Java에서 Map으로 매핑해 DTO를 구성했습니다. JOIN FETCH로 연관 엔티티도 함께 로드해 Lazy 로딩 추가 쿼리를 막았습니다."

---

## 4. 실종 제보 채팅방 생성 N+1 제거 — 배치 조회 + 메모리 매칭

### 어필 포인트

실종 제보 채팅방 생성(`createMissingPetChat()`)에서 기존 채팅방 탐색 시 채팅방마다 참여자를 개별 조회하는 N+1이 있었다.

조치 내용 (코드 리뷰 2026-04-14 기록):
- 채팅방 목록(`conversationIdxs`)에 대해 참여자를 `findParticipantsByConversationIdxsAndStatus()` 배치 조회
- Java `groupingBy`로 채팅방별 그룹핑 후 메모리에서 `(reporterId, witnessId)` 조합 매칭
- 채팅방 N개에 대해 N번 쿼리 → 1번 쿼리로 감소

### 말할 내용

> "실종 제보 채팅방을 만들 때 '이미 이 제보자-목격자 조합의 채팅방이 있는지' 확인해야 합니다. 처음에는 기존 채팅방 목록을 루프 돌며 각각 참여자를 조회했는데, N+1이 발생했습니다. 채팅방 ID 목록으로 참여자를 한 번에 배치 조회한 뒤 Java에서 `groupingBy`로 채팅방별 그룹을 만들고 메모리에서 조합을 매칭하는 방식으로 변경했습니다."

---

## 5. FULLTEXT 인덱스 + 2단계 쿼리로 메시지 검색 최적화

### 어필 포인트

메시지 검색을 `LIKE '%keyword%'` 양방향으로 처리하면 B-tree 인덱스를 전혀 사용하지 못해 테이블 풀 스캔이 발생한다.

실제 인덱스 (DB SHOW INDEX 기준):
```sql
CREATE FULLTEXT INDEX idx_chat_message_content ON chatmessage(content) WITH PARSER ngram;
```

실제 쿼리 (`SpringDataJpaChatMessageRepository.findIdxByFulltextContent()`):

```java
@Query(value = "SELECT m.idx FROM chatmessage m "
        + "INNER JOIN users s ON s.idx = m.sender_idx "
        + "WHERE m.conversation_idx = :conversationIdx "
        + "AND (m.is_deleted IS NULL OR m.is_deleted = 0) "
        + "AND (s.is_deleted IS NULL OR s.is_deleted = 0) "
        + "AND MATCH(m.content) AGAINST(:keyword IN NATURAL LANGUAGE MODE) "
        + "ORDER BY m.created_at DESC",
        nativeQuery = true)
List<Long> findIdxByFulltextContent(
        @Param("conversationIdx") Long conversationIdx,
        @Param("keyword") String keyword);
```

2단계 조회 패턴 (`JpaChatMessageAdapter`):
1. FULLTEXT 쿼리로 `idx` 목록만 조회 (경량)
2. `findByIdxInWithAssociations(ids)`로 발신자 JOIN FETCH 포함 본문 조회

```java
@Query("SELECT DISTINCT m FROM ChatMessage m JOIN FETCH m.sender s LEFT JOIN FETCH m.replyToMessage "
        + "WHERE m.idx IN :ids")
List<ChatMessage> findByIdxInWithAssociations(@Param("ids") Collection<Long> ids);
```

ngram 파서로 한글 형태소 분리 없이 n-gram 분할로 검색 가능.

### 말할 내용

> "메시지 검색을 처음에는 LIKE 양방향으로 처리했는데, 인덱스를 전혀 타지 못했습니다. MySQL FULLTEXT 인덱스에 ngram 파서를 적용해 한글도 처리하도록 변경했습니다. 검색 쿼리를 2단계로 분리해서, 먼저 MATCH AGAINST로 idx만 가져오고, 그 idx로 JOIN FETCH 포함 본문 조회를 별도로 수행했습니다. 이렇게 하면 FULLTEXT 검색의 경량 쿼리 이점을 살리면서 연관 엔티티도 N+1 없이 로드할 수 있습니다."

---

## 6. REQUIRES_NEW 트랜잭션 분리 — self-invocation 문제 해결

### 어필 포인트

채팅방 생성 로직에서 실패해도 호출한 트랜잭션(펫케어 요청 처리 등)에 영향을 주지 않도록 `Propagation.REQUIRES_NEW`를 사용하려 했다. 그런데 동일 클래스 내에서 `this.createConversation()`을 호출하면 Spring AOP 프록시를 거치지 않아 `REQUIRES_NEW`가 무시된다.

해결책: `ConversationCreatorService`를 별도 `@Service` 빈으로 분리

```
ConversationService (호출자)
  └─ ConversationCreatorService.createConversation()  ← 별도 빈, REQUIRES_NEW 정상 적용
```

`ConversationCreatorService`는 `actingUserId` 검증도 포함. `MeetupChatRoomEventListener`도 `ConversationService`가 아닌 `ConversationCreatorService` 빈을 직접 호출.

### 말할 내용

> "채팅방 생성이 실패해도 케어 요청 같은 상위 트랜잭션은 롤백되면 안 되는 요구사항이 있었습니다. `@Transactional(propagation = REQUIRES_NEW)`를 붙였는데, 동일 클래스 내에서 호출하면 프록시를 거치지 않아 실제로는 새 트랜잭션이 열리지 않는 self-invocation 문제가 있었습니다. `ConversationCreatorService`를 별도 스프링 빈으로 분리해서 외부 빈 호출로 프록시가 정상 동작하도록 수정했습니다."

---

## 7. 재참여 시 메시지 범위 제어 — joinedAt 필터

### 어필 포인트

산책모임 채팅방에서 나갔다가 재참여하면 이전 대화 내용을 보여주지 않아야 한다. `ConversationParticipant`에 `joinedAt`과 `lastReadMessage` 두 필드로 재참여 여부를 판단한다.

실제 판단 로직 (`ChatMessageService.getMessages()`):

```java
LocalDateTime readFrom = null;
if (participant.getLastReadMessage() == null && participant.getJoinedAt() != null) {
    // 재참여한 경우: lastReadMessage가 null이고 joinedAt이 있으면 재참여로 간주
    readFrom = participant.getJoinedAt();
}

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
```

재참여 초기화 (`joinMeetupChat()`): `leftAt`이 있으면 `lastReadMessage`, `lastReadAt`, `unreadCount` 초기화 + status ACTIVE 변경.

`ConversationParticipant` 엔티티에서 `joinedAt`은 `@PrePersist`에서 자동 설정, `leftAt`은 나가기 시 직접 설정.

### 말할 내용

> "산책모임에서 나갔다가 재참여한 사용자는 이전 대화를 볼 수 없어야 합니다. `lastReadMessage`가 null이고 `joinedAt`이 있으면 재참여로 판단해, `joinedAt` 이후 메시지만 DB에서 필터링해서 반환합니다. 재참여 시에는 `unreadCount`, `lastReadMessage`, `lastReadAt`을 초기화해서 깨끗한 상태로 시작하도록 했습니다."

---

## 8. 인덱스 설계 전략

### 어필 포인트

실제 DB SHOW INDEX 결과 기준 인덱스 설계:

**conversation 테이블**:
```sql
CREATE INDEX idx_conversation_deleted ON conversation(is_deleted, deleted_at);
CREATE INDEX idx_conversation_related ON conversation(related_type, related_idx);
CREATE INDEX idx_conversation_type_status ON conversation(conversation_type, status, last_message_at);
```

**conversationparticipant 테이블**:
```sql
CREATE INDEX idx_participant_conversation ON conversationparticipant(conversation_idx, status);
CREATE INDEX idx_participant_unread ON conversationparticipant(user_idx, unread_count);
CREATE INDEX idx_participant_user_status ON conversationparticipant(user_idx, status, unread_count);
CREATE INDEX last_read_message_idx ON conversationparticipant(last_read_message_idx);
CREATE UNIQUE INDEX uk_participant_conversation_user ON conversationparticipant(conversation_idx, user_idx);
```

**chatmessage 테이블**:
```sql
CREATE FULLTEXT INDEX idx_chat_message_content ON chatmessage(content) WITH PARSER ngram;
CREATE INDEX idx_chat_message_conversation_created ON chatmessage(conversation_idx, created_at);
CREATE INDEX idx_chat_message_deleted ON chatmessage(is_deleted, deleted_at);
CREATE INDEX idx_chat_message_sender ON chatmessage(sender_idx, created_at);
CREATE INDEX idx_chat_message_type ON chatmessage(message_type, created_at);
CREATE INDEX reply_to_message_idx ON chatmessage(reply_to_message_idx);
```

설계 기준:
- 복합 인덱스는 WHERE 조건 순서에 맞게 구성 (선택도 높은 컬럼 우선)
- `(conversation_idx, created_at)` — 채팅방별 시간순 메시지 조회의 핵심 인덱스
- `(user_idx, status, unread_count)` — 읽지 않은 메시지 있는 채팅방 필터링
- `uk_participant_conversation_user` UNIQUE — 동일 (채팅방, 사용자) 중복 방지 (단, LEFT + 소프트 삭제 이력 모델과 충돌 가능성으로 마이그레이션 검토 중)

### 말할 내용

> "채팅에서 가장 많이 발생하는 쿼리 패턴은 '특정 채팅방의 최신 메시지 N개 조회'입니다. `(conversation_idx, created_at)` 복합 인덱스를 걸어 채팅방 필터 후 시간 정렬을 인덱스 범위 스캔으로 처리했습니다. 참여자 테이블에는 `(user_idx, status, unread_count)` 복합 인덱스를 걸어 내 채팅방 목록에서 읽지 않은 메시지 필터를 인덱스만으로 처리할 수 있도록 했습니다."

---

## 핵심 키워드

- `@Modifying @Query` — DB 레벨 원자적 UPDATE (Lost Update 방지)
- `Propagation.REQUIRES_NEW` + 별도 빈 분리 (self-invocation 문제 해결)
- 배치 조회 (`IN` 조건) + Map 매핑으로 N+1 제거
- MySQL FULLTEXT (`MATCH AGAINST`, ngram 파서) + 2단계 idx 재조회
- `joinedAt` 기반 재참여 메시지 범위 제어
- `(conversation_idx, created_at)` 복합 인덱스 — 채팅방별 시간순 조회
- Soft Delete (`isDeleted`, `deletedAt`) — Conversation / ConversationParticipant / ChatMessage 모두 적용
- `@Transactional(readOnly = true)` 클래스 레벨 + 쓰기 메서드 오버라이드

---

## 관련 문서

- `docs/domains/chat.md` — Chat 도메인 전체 상세 (엔티티·API·트랜잭션)
- `docs/refactoring/chat/chat-code-review-2026-04-14.md` — 코드 리뷰 결과 (Critical 5건, 조치 완료)
- `docs/refactoring/chat/chat-backend-security-transaction-2026-04-14.md` — 보안·트랜잭션 정리

---

## 면접 대답 구성

### 질문: "DB 관련 설계나 최적화 경험이 있나요?"

**대답 구조 (2분 기준)**:

1. **원자적 업데이트** (30초)
   - "메시지 전송 시 수신자들의 읽지 않은 메시지 수를 증가시켜야 했는데, Java 레벨에서 조회 후 증가하면 동시 전송 시 Lost Update가 발생합니다. `@Modifying @Query`로 단일 UPDATE 쿼리를 DB에 직접 날려 원자적으로 처리했습니다."

2. **N+1 해결** (30초)
   - "채팅방 목록 조회 시 채팅방마다 참여자·최신 메시지를 개별 조회하면 N+1이 발생합니다. 채팅방 ID 목록으로 배치 쿼리 3개를 날리고 Java에서 Map으로 매핑해 처리했습니다."

3. **FULLTEXT 검색** (30초)
   - "메시지 검색에 LIKE 양방향을 쓰면 인덱스를 전혀 못 씁니다. MySQL FULLTEXT 인덱스에 ngram 파서를 적용하고, idx만 먼저 조회한 뒤 본문을 별도 JOIN FETCH로 가져오는 2단계 쿼리로 변경했습니다."

4. **트랜잭션 격리** (30초)
   - "채팅방 생성 실패가 상위 트랜잭션에 영향을 주지 않도록 REQUIRES_NEW를 썼는데, 동일 클래스 내 호출은 프록시를 거치지 않아 무시됩니다. ConversationCreatorService를 별도 빈으로 분리해서 해결했습니다."
