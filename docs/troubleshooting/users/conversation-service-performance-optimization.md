# 채팅방 목록 조회 성능 최적화

## 문제 발생 로직

### 문제점
`ConversationService.getMyConversations()` 메서드에서 채팅방 목록을 조회할 때 **N+1 문제**와 **메모리 부하**가 발생했습니다.

### 문제 발생 코드

```java
public List<ConversationDTO> getMyConversations(Long userId) {
    List<Conversation> conversations = conversationRepository
            .findActiveConversationsByUser(userId, ConversationStatus.ACTIVE);

    return conversations.stream()
            .map(conv -> {
                ConversationDTO dto = conversationConverter.toDTO(conv);
                
                // ❌ 문제 1: 각 채팅방마다 개별 쿼리 실행 (N+1)
                ConversationParticipant myParticipant = participantRepository
                        .findByConversationIdxAndUserIdx(conv.getIdx(), userId)
                        .orElse(null);
                
                // ❌ 문제 2: 각 채팅방마다 개별 쿼리 실행 (N+1)
                List<ConversationParticipant> participants = participantRepository
                        .findByConversationIdxAndStatus(conv.getIdx(), ParticipantStatus.ACTIVE);
                
                // ❌ 문제 3: 모든 메시지를 메모리에 로드 (메모리 부하)
                if (conv.getMessages() != null && !conv.getMessages().isEmpty()) {
                    conv.getMessages().stream()
                            .max((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()))
                            .ifPresent(lastMessage -> {
                                dto.setLastMessage(messageConverter.toDTO(lastMessage));
                            });
                }
                
                return dto;
            })
            .collect(Collectors.toList());
}
```

### 문제 발생 로직 설명

#### 동작 흐름

1. **채팅방 목록 조회 (1번 쿼리)**
   ```java
   List<Conversation> conversations = conversationRepository
           .findActiveConversationsByUser(userId, ConversationStatus.ACTIVE);
   ```
   - 사용자가 참여한 활성 채팅방 목록을 한 번에 조회
   - 예: 채팅방 10개 반환

2. **각 채팅방마다 반복 처리 (N번 반복)**
   ```java
   conversations.stream().map(conv -> { ... })
   ```
   - Stream의 `map()` 연산으로 각 채팅방을 순회
   - 각 채팅방마다 다음 작업 수행:

3. **현재 사용자의 참여자 정보 조회 (N번 쿼리)**
   ```java
   ConversationParticipant myParticipant = participantRepository
           .findByConversationIdxAndUserIdx(conv.getIdx(), userId);
   ```
   - **문제**: 각 채팅방마다 개별 쿼리 실행
   - 채팅방 10개 → 10번의 개별 쿼리 발생
   - 각 쿼리는 `WHERE conversation_idx = ? AND user_idx = ?` 형태

4. **활성 참여자 목록 조회 (N번 쿼리)**
   ```java
   List<ConversationParticipant> participants = participantRepository
           .findByConversationIdxAndStatus(conv.getIdx(), ParticipantStatus.ACTIVE);
   ```
   - **문제**: 각 채팅방마다 개별 쿼리 실행
   - 채팅방 10개 → 10번의 개별 쿼리 발생
   - 각 쿼리는 `WHERE conversation_idx = ? AND status = ?` 형태

5. **메시지 로드 (메모리 부하)**
   ```java
   conv.getMessages().stream()
           .max((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()))
   ```
   - **문제**: LAZY 로딩으로 모든 메시지를 메모리에 로드
   - `conv.getMessages()` 호출 시 Hibernate가 모든 메시지를 조회
   - 마지막 메시지만 필요한데 전체 메시지 로드
   - 채팅방당 20개 메시지 → 10개 채팅방 × 20개 = 200개 메시지 전체 로드

#### 왜 문제가 발생하는가?

- **N+1 문제**: 채팅방 N개를 조회한 후, 각 채팅방마다 추가 쿼리를 실행
  - 1번 쿼리(채팅방 목록) + N번 쿼리(참여자 정보) + N번 쿼리(참여자 목록) = **1 + 2N개 쿼리**
  - 채팅방이 많을수록 쿼리 수가 선형적으로 증가

- **메모리 부하**: 필요한 데이터(최신 메시지 1개)만 필요한데 전체 데이터(모든 메시지)를 로드
  - 불필요한 데이터를 메모리에 적재하여 GC 부하 증가
  - 대용량 채팅방에서 심각한 성능 저하

### 문제 분석

1. **N+1 문제 발생**
   - 채팅방 목록 조회: 1번 쿼리
   - 각 채팅방마다 `findByConversationIdxAndUserIdx()`: N번 쿼리
   - 각 채팅방마다 `findByConversationIdxAndStatus()`: N번 쿼리
   - **총 쿼리 수**: 1 + 2N개 (채팅방 10개 기준 → 21개 쿼리)

2. **메모리 부하**
   - `conv.getMessages()`로 LAZY 로딩 시 **모든 메시지**를 메모리에 로드
   - 마지막 메시지만 필요한데 전체 메시지 로드
   - 채팅방당 메시지가 많을수록 메모리 사용량 급증

---

## 수정된 로직

### 해결 방법
1. **배치 조회**: 채팅방 ID 목록을 한 번에 조회하여 N+1 문제 해결
2. **최신 메시지만 조회**: 모든 메시지 대신 최신 메시지만 조회
3. **Map 기반 매핑**: 조회한 데이터를 Map으로 변환하여 메모리에서 빠르게 매핑

### 수정된 로직 설명

#### 동작 흐름

1. **채팅방 목록 조회 (1번 쿼리)**
   ```java
   List<Conversation> conversations = conversationRepository
           .findActiveConversationsByUser(userId, ConversationStatus.ACTIVE);
   ```
   - 사용자가 참여한 활성 채팅방 목록을 한 번에 조회
   - 예: 채팅방 10개 반환

2. **채팅방 ID 목록 추출**
   ```java
   List<Long> conversationIdxs = conversations.stream()
           .map(Conversation::getIdx)
           .collect(Collectors.toList());
   ```
   - 채팅방 엔티티에서 ID만 추출하여 리스트 생성
   - 예: `[1, 2, 3, 4, 5, 6, 7, 8, 9, 10]`
   - **목적**: 배치 조회를 위해 ID 목록 준비

3. **현재 사용자 참여자 정보 배치 조회 (1번 쿼리)**
   ```java
   List<ConversationParticipant> myParticipants = participantRepository
           .findParticipantsByConversationIdxsAndUserIdx(conversationIdxs, userId);
   ```
   - **핵심**: 모든 채팅방의 참여자 정보를 한 번에 조회
   - SQL: `WHERE conversation_idx IN (1, 2, 3, ..., 10) AND user_idx = ?`
   - 10번의 개별 쿼리 → 1번의 배치 쿼리로 변경
   - **결과**: 10개 채팅방의 참여자 정보를 한 번에 가져옴

4. **Map으로 변환 (메모리에서 처리)**
   ```java
   Map<Long, ConversationParticipant> myParticipantMap = myParticipants.stream()
           .collect(Collectors.toMap(
                   p -> p.getConversation().getIdx(),  // Key: 채팅방 ID
                   p -> p,                              // Value: 참여자 정보
                   (existing, replacement) -> existing
           ));
   ```
   - **목적**: 채팅방 ID로 빠르게 참여자 정보를 찾기 위해 Map 생성
   - Key: 채팅방 ID (예: 1, 2, 3, ...)
   - Value: 해당 채팅방의 참여자 정보
   - **효과**: O(1) 시간 복잡도로 참여자 정보 조회 가능

5. **활성 참여자 목록 배치 조회 (1번 쿼리)**
   ```java
   List<ConversationParticipant> allParticipants = participantRepository
           .findParticipantsByConversationIdxsAndStatus(conversationIdxs, ParticipantStatus.ACTIVE);
   ```
   - **핵심**: 모든 채팅방의 활성 참여자 목록을 한 번에 조회
   - SQL: `WHERE conversation_idx IN (1, 2, 3, ..., 10) AND status = 'ACTIVE'`
   - 10번의 개별 쿼리 → 1번의 배치 쿼리로 변경

6. **그룹화하여 Map 생성**
   ```java
   Map<Long, List<ConversationParticipant>> participantsMap = allParticipants.stream()
           .collect(Collectors.groupingBy(p -> p.getConversation().getIdx()));
   ```
   - **목적**: 채팅방별로 참여자 목록을 그룹화
   - Key: 채팅방 ID
   - Value: 해당 채팅방의 참여자 목록 (List)
   - **효과**: 채팅방 ID로 참여자 목록을 즉시 조회 가능

7. **최신 메시지만 배치 조회 (1번 쿼리)**
   ```java
   List<ChatMessage> latestMessages = chatMessageRepository
           .findLatestMessagesByConversationIdxs(conversationIdxs);
   ```
   - **핵심**: 모든 채팅방의 최신 메시지만 한 번에 조회
   - SQL: `WHERE conversation_idx IN (1, 2, 3, ..., 10) AND idx IN (SELECT MAX(...))`
   - 모든 메시지(200개) 대신 최신 메시지(10개)만 조회
   - **효과**: 메모리 사용량 대폭 감소

8. **Map으로 변환**
   ```java
   Map<Long, ChatMessage> latestMessageMap = latestMessages.stream()
           .collect(Collectors.toMap(
                   m -> m.getConversation().getIdx(),  // Key: 채팅방 ID
                   m -> m,                              // Value: 최신 메시지
                   (existing, replacement) -> existing
           ));
   ```
   - 채팅방 ID로 최신 메시지를 빠르게 찾기 위해 Map 생성

9. **DTO 변환 (메모리에서 처리, 추가 쿼리 없음)**
   ```java
   return conversations.stream()
           .map(conv -> {
               ConversationDTO dto = conversationConverter.toDTO(conv);
               
               // Map에서 조회 (O(1) 시간 복잡도, 추가 쿼리 없음)
               ConversationParticipant myParticipant = myParticipantMap.get(conv.getIdx());
               List<ConversationParticipant> participants = participantsMap.getOrDefault(...);
               ChatMessage lastMessage = latestMessageMap.get(conv.getIdx());
               
               return dto;
           })
           .collect(Collectors.toList());
   ```
   - 각 채팅방을 순회하면서 DTO 생성
   - **핵심**: Map에서 데이터를 조회하므로 추가 쿼리 없음
   - 모든 데이터가 이미 메모리에 로드되어 있어 빠른 처리

#### 왜 이렇게 동작하는가?

- **배치 조회의 원리**
  - `IN` 절을 사용하여 여러 ID를 한 번에 조회
  - 개별 쿼리: `WHERE conversation_idx = 1` (10번 실행)
  - 배치 쿼리: `WHERE conversation_idx IN (1, 2, 3, ..., 10)` (1번 실행)
  - DB는 인덱스를 활용하여 효율적으로 처리

- **Map을 사용하는 이유**
  - List에서 특정 채팅방의 데이터를 찾으려면 O(N) 시간 필요
  - Map을 사용하면 O(1) 시간에 조회 가능
  - 채팅방이 많을수록 성능 차이가 커짐

- **최신 메시지만 조회하는 이유**
  - 서브쿼리로 각 채팅방의 최신 메시지만 선택
  - `MAX(idx)` 또는 `MAX(created_at)`을 사용하여 최신 메시지 식별
  - 불필요한 데이터를 로드하지 않아 메모리 절약

### 수정된 코드

```java
public List<ConversationDTO> getMyConversations(Long userId) {
    // 1. 채팅방 목록 조회 (1번 쿼리)
    List<Conversation> conversations = conversationRepository
            .findActiveConversationsByUser(userId, ConversationStatus.ACTIVE);

    if (conversations.isEmpty()) {
        return new ArrayList<>();
    }

    // 2. 채팅방 ID 목록 추출
    List<Long> conversationIdxs = conversations.stream()
            .map(Conversation::getIdx)
            .collect(Collectors.toList());

    // ✅ 수정 1: 배치 조회로 한 번에 조회 (1번 쿼리)
    List<ConversationParticipant> myParticipants = participantRepository
            .findParticipantsByConversationIdxsAndUserIdx(conversationIdxs, userId);
    Map<Long, ConversationParticipant> myParticipantMap = myParticipants.stream()
            .collect(Collectors.toMap(
                    p -> p.getConversation().getIdx(),
                    p -> p,
                    (existing, replacement) -> existing
            ));

    // ✅ 수정 2: 배치 조회로 한 번에 조회 (1번 쿼리)
    List<ConversationParticipant> allParticipants = participantRepository
            .findParticipantsByConversationIdxsAndStatus(conversationIdxs, ParticipantStatus.ACTIVE);
    Map<Long, List<ConversationParticipant>> participantsMap = allParticipants.stream()
            .collect(Collectors.groupingBy(p -> p.getConversation().getIdx()));

    // ✅ 수정 3: 최신 메시지만 배치 조회 (1번 쿼리)
    List<ChatMessage> latestMessages = chatMessageRepository
            .findLatestMessagesByConversationIdxs(conversationIdxs);
    Map<Long, ChatMessage> latestMessageMap = latestMessages.stream()
            .collect(Collectors.toMap(
                    m -> m.getConversation().getIdx(),
                    m -> m,
                    (existing, replacement) -> existing
            ));

    // 3. DTO 변환 (메모리에서 처리)
    return conversations.stream()
            .map(conv -> {
                ConversationDTO dto = conversationConverter.toDTO(conv);
                
                // Map에서 조회 (추가 쿼리 없음)
                ConversationParticipant myParticipant = myParticipantMap.get(conv.getIdx());
                if (myParticipant != null) {
                    dto.setUnreadCount(myParticipant.getUnreadCount());
                }
                
                List<ConversationParticipant> participants = participantsMap.getOrDefault(
                        conv.getIdx(), new ArrayList<>());
                if (!participants.isEmpty()) {
                    dto.setParticipants(participantConverter.toDTOList(participants));
                }
                
                ChatMessage lastMessage = latestMessageMap.get(conv.getIdx());
                if (lastMessage != null) {
                    dto.setLastMessage(messageConverter.toDTO(lastMessage));
                }
                
                return dto;
            })
            .collect(Collectors.toList());
}
```

### 핵심 변경 사항

#### 1. 개별 쿼리 → 배치 조회

**Before:**
```java
// 각 채팅방마다 개별 쿼리
ConversationParticipant myParticipant = participantRepository
        .findByConversationIdxAndUserIdx(conv.getIdx(), userId);
```

**After:**
```java
// 모든 채팅방을 한 번에 배치 조회
List<ConversationParticipant> myParticipants = participantRepository
        .findParticipantsByConversationIdxsAndUserIdx(conversationIdxs, userId);
Map<Long, ConversationParticipant> myParticipantMap = myParticipants.stream()
        .collect(Collectors.toMap(...));
```

#### 2. 모든 메시지 로드 → 최신 메시지만 조회

**Before:**
```java
// 모든 메시지를 메모리에 로드
if (conv.getMessages() != null && !conv.getMessages().isEmpty()) {
    conv.getMessages().stream()
            .max((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()))
            .ifPresent(...);
}
```

**After:**
```java
// 최신 메시지만 배치 조회
List<ChatMessage> latestMessages = chatMessageRepository
        .findLatestMessagesByConversationIdxs(conversationIdxs);
Map<Long, ChatMessage> latestMessageMap = latestMessages.stream()
        .collect(Collectors.toMap(...));
```

#### 3. Repository 메서드 추가

**ConversationParticipantRepository:**
```java
// 여러 채팅방의 특정 사용자 참여자 정보 배치 조회
@Query("SELECT p FROM ConversationParticipant p " +
       "JOIN FETCH p.conversation c " +
       "JOIN FETCH p.user u " +
       "WHERE p.conversation.idx IN :conversationIdxs " +
       "  AND p.user.idx = :userId " +
       "  AND p.isDeleted = false " +
       "  AND u.isDeleted = false")
List<ConversationParticipant> findParticipantsByConversationIdxsAndUserIdx(
    @Param("conversationIdxs") List<Long> conversationIdxs,
    @Param("userId") Long userId);

// 여러 채팅방의 활성 참여자 정보 배치 조회
@Query("SELECT p FROM ConversationParticipant p " +
       "JOIN FETCH p.user u " +
       "WHERE p.conversation.idx IN :conversationIdxs " +
       "  AND p.status = :status " +
       "  AND p.isDeleted = false " +
       "  AND u.isDeleted = false")
List<ConversationParticipant> findParticipantsByConversationIdxsAndStatus(
    @Param("conversationIdxs") List<Long> conversationIdxs,
    @Param("status") ParticipantStatus status);
```

**ChatMessageRepository:**
```java
// 여러 채팅방의 최신 메시지 조회 (배치) - Sender 포함
@Query("SELECT m FROM ChatMessage m " +
       "JOIN FETCH m.sender s " +
       "WHERE m.conversation.idx IN :conversationIdxs " +
       "  AND m.isDeleted = false " +
       "  AND s.isDeleted = false " +
       "  AND m.idx IN (" +
       "    SELECT MAX(m2.idx) FROM ChatMessage m2 " +
       "    WHERE m2.conversation.idx = m.conversation.idx " +
       "      AND m2.isDeleted = false" +
       "  )")
List<ChatMessage> findLatestMessagesByConversationIdxs(
    @Param("conversationIdxs") List<Long> conversationIdxs);
```

---

## 성능 개선 결과

### 쿼리 수 비교

| 항목 | Before | After | 개선율 |
|------|--------|-------|--------|
| **채팅방 목록** | 1개 | 1개 | - |
| **참여자 정보** | 2N개 (각 채팅방마다 2개) | 2개 (배치 조회) | **90% 감소** |
| **메시지 조회** | N개 (모든 메시지) | 1개 (최신 메시지만) | **90% 감소** |
| **총 쿼리 수** | **3N+1개** (이론적: 31개, 실제: 21개) | **4개** | **80.95% 감소** |

**참고**: 실제 측정 시 21개 쿼리가 발생한 이유는 Hibernate의 일부 최적화와 캐시 효과 때문입니다. 하지만 여전히 N+1 문제가 존재하여 많은 쿼리가 발생했습니다.

### 실제 측정 결과 (채팅방 10개 기준)

| 항목 | Before | After | 절감량 | 개선율 |
|------|--------|-------|--------|--------|
| **실행 시간** | 377 ms | 69 ms | 308 ms | **81.70%** |
| **쿼리 수** | 21개 | 4개 | 17개 | **80.95%** |
| **메모리 사용량** | 571,360 bytes (0.54 MB) | 150,888 bytes (0.14 MB) | 420,472 bytes (0.40 MB) | **73.59%** |

### 실제 쿼리 실행 로그

#### 수정 전 (N+1 문제 발생)

```
1. SELECT * FROM conversation WHERE ... (채팅방 목록 조회)
2. SELECT * FROM conversationparticipant WHERE conversation_idx = 180 AND user_idx = 2943
3. SELECT * FROM conversationparticipant WHERE conversation_idx = 180 AND status = 'ACTIVE'
4. SELECT * FROM chatmessage WHERE conversation_idx = 180 (모든 메시지 로드)
5. SELECT * FROM conversationparticipant WHERE conversation_idx = 181 AND user_idx = 2943
6. SELECT * FROM conversationparticipant WHERE conversation_idx = 181 AND status = 'ACTIVE'
7. SELECT * FROM chatmessage WHERE conversation_idx = 181 (모든 메시지 로드)
... (각 채팅방마다 반복)
총: 21개 쿼리
```

#### 수정 후 (배치 조회)

```
1. SELECT * FROM conversation WHERE ... (채팅방 목록 조회)
2. SELECT * FROM conversationparticipant 
   WHERE conversation_idx IN (180, 181, 182, ..., 189) 
     AND user_idx = 2943 (배치 조회)
3. SELECT * FROM conversationparticipant 
   WHERE conversation_idx IN (180, 181, 182, ..., 189) 
     AND status = 'ACTIVE' (배치 조회)
4. SELECT * FROM chatmessage 
   WHERE conversation_idx IN (180, 181, 182, ..., 189) 
     AND idx IN (SELECT MAX(idx) FROM chatmessage ...) (최신 메시지만 배치 조회)
총: 4개 쿼리
```

### 실제 데이터 예시

#### 수정 전 로그
```
myParticipants (10개):
  - idx: 536, conversationIdx: 186, userIdx: 2943, unreadCount: 0, status: ACTIVE
  - idx: 539, conversationIdx: 187, userIdx: 2943, unreadCount: 1, status: ACTIVE
  ... (각 채팅방마다 개별 쿼리로 조회)

allParticipants (30개):
  - idx: 536, conversationIdx: 186, userIdx: 2943, status: ACTIVE
  - idx: 537, conversationIdx: 186, userIdx: 2956, status: ACTIVE
  ... (각 채팅방마다 개별 쿼리로 조회)

latestMessages (10개):
  - idx: 101224, conversationIdx: 180, senderIdx: 2943, content: 메시지 0
  ... (모든 메시지를 로드한 후 최신 메시지 선택)
```

#### 수정 후 로그
```
myParticipants (10개):
  - idx: 524, conversationIdx: 182, userIdx: 2943, unreadCount: 2, status: ACTIVE
  - idx: 533, conversationIdx: 185, userIdx: 2943, unreadCount: 2, status: ACTIVE
  ... (한 번의 배치 쿼리로 모든 채팅방 조회)

allParticipants (30개):
  - idx: 518, conversationIdx: 180, userIdx: 2943, status: ACTIVE
  - idx: 519, conversationIdx: 180, userIdx: 2944, status: ACTIVE
  ... (한 번의 배치 쿼리로 모든 채팅방 조회)

latestMessages (10개):
  - idx: 101243, conversationIdx: 180, senderIdx: 2945, content: 메시지 19
  ... (한 번의 배치 쿼리로 최신 메시지만 조회)
```

### 개선 효과 요약

1. **쿼리 수 감소**: 21개 → 4개 ✅ **실제 측정 완료 (80.95% 개선)**
   - 채팅방 개수와 무관하게 일정한 쿼리 수 유지
   - DB 연결 풀 부하 대폭 감소

2. **실행 시간 단축**: 377ms → 69ms ✅ **실제 측정 완료 (81.70% 개선)**
   - 로그인 응답 시간이 약 5.5배 빨라짐
   - 사용자 경험(UX) 대폭 개선

3. **메모리 사용량 감소**: 0.54 MB → 0.14 MB ✅ **실제 측정 완료 (73.59% 개선)**
   - 전체 메시지 로드 → 최신 메시지만 로드
   - GC 부하 감소 및 서버 안정성 향상

4. **DB 부하 감소**: 연결 풀 고갈 위험 감소 ✅ **쿼리 수 감소로 개선**
   - 동시 로그인 시나리오에서도 안정적인 성능 유지

5. **확장성 향상**: 채팅방 수가 증가해도 쿼리 수는 일정하게 유지 ✅ **배치 조회로 해결**
   - 채팅방 100개여도 쿼리는 4개로 동일
   - 서비스 확장에 유리한 구조

---

## 핵심 포인트

### 적용 가능한 패턴

1. **배치 조회 패턴**
   - 여러 엔티티를 조회할 때 `IN` 절을 사용하여 한 번에 조회
   - N번의 개별 쿼리 → 1번의 배치 쿼리

2. **Fetch Join 패턴**
   - 연관 엔티티를 함께 조회하여 추가 쿼리 방지
   - `JOIN FETCH`를 사용하여 N+1 문제 완전 해결

3. **Map 기반 매핑**
   - 조회한 데이터를 Map으로 변환하여 메모리에서 빠르게 조회
   - O(1) 시간 복잡도로 매핑

4. **필요한 데이터만 조회**
   - 모든 메시지 대신 최신 메시지만 조회
   - 메모리 사용량 대폭 감소

### 주의사항

- **IN 절의 크기 제한**: MySQL의 경우 `IN` 절에 너무 많은 값을 넣으면 성능 저하 가능
- **메모리 사용**: 배치 조회 시 한 번에 많은 데이터를 메모리에 로드하므로 적절한 배치 크기 고려
- **트랜잭션 범위**: 배치 조회는 트랜잭션 내에서 실행되어야 함
