# Chat 도메인 코드 리뷰 결과

> **날짜**: 2026-04-14
> **대상**: `domain/chat/` 전체
> **리뷰 기준**: `.claude/skills/review.md` 체크리스트 A~E

---

## 점수판 요약

| 카테고리 | Critical | Warning | Info |
|---------|----------|---------|------|
| JPA/쿼리 (A) | 1 | 1 | 0 |
| 트랜잭션 (B) | 1 | 1 | 0 |
| 보안 (C) | 1 | 0 | 0 |
| 정합성 (D) | 0 | 1 | 0 |
| 코드품질 (E) | 0 | 0 | 1 |
| **합계** | **3** | **3** | **1** |

**판정**: 🔴 수정 필요 (Critical 3개)

---

## 🔴 Critical (3건)

### 1. [C-보안] IDOR 취약점 — userId/senderIdx를 RequestParam으로 수신

**파일**: `ChatMessageController.java:33`, `ConversationController.java:30`, `ChatMessageController.java:57,82,94`

**문제**: 모든 API에서 `userId`, `senderIdx`를 `@RequestParam`으로 받음. 인증된 사용자가 타인의 ID를 파라미터로 전달하면 다른 사람 이름으로 메시지 전송·조회 가능한 IDOR(Insecure Direct Object Reference) 취약점.

**현재 코드**:
```java
@PostMapping
@PreAuthorize("isAuthenticated()")
public ResponseEntity<ChatMessageDTO> sendMessage(
        @RequestBody SendMessageRequest request,
        @RequestParam Long senderIdx) { // ← 클라이언트가 임의 senderIdx 전달 가능
    ...
}
```

**개선 코드**:
```java
@PostMapping
@PreAuthorize("isAuthenticated()")
public ResponseEntity<ChatMessageDTO> sendMessage(
        @RequestBody SendMessageRequest request,
        @AuthenticationPrincipal UserDetails userDetails) {
    Long senderIdx = Long.parseLong(userDetails.getUsername());
    ...
}
```

**영향 범위**: `ChatMessageController` 전체, `ConversationController`의 userId 수신 엔드포인트 전체

---

### 2. [B5] Self-invocation으로 `REQUIRES_NEW` 트랜잭션 미동작

**파일**: `ConversationService.java:306`, `ConversationService.java:328`

**문제**: `createCareRequestConversation()`과 `getOrCreateDirectConversation()`이 내부에서 `this.createConversation()`을 호출. Spring AOP 프록시를 거치지 않으므로 `createConversation()`의 `Propagation.REQUIRES_NEW`가 무시됨. 결과적으로 호출자 트랜잭션 안에서 실행되어 롤백 격리 의도가 깨짐.

**현재 코드**:
```java
// createCareRequestConversation() — @Transactional
public ConversationDTO createCareRequestConversation(...) {
    ...
    return createConversation(...); // ← self-invocation, REQUIRES_NEW 미적용
}

// createConversation() — @Transactional(propagation = REQUIRES_NEW) 선언되어 있으나 무시됨
```

**개선 방법 (권장 순)**:
1. `createConversation()` 로직을 별도 `@Service`(`ConversationCreatorService`)로 분리
2. `@Autowired private ConversationService self` self-reference 주입 후 `self.createConversation()` 호출

```java
// 방법 1: 별도 서비스 분리
@Service
public class ConversationCreatorService {
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ConversationDTO createConversation(...) { ... }
}
```

---

### 3. [A2] `createMissingPetChat()` — 스트림 내부 N+1 쿼리

**파일**: `ConversationService.java:522~534`

**문제**: 실종제보 채팅방을 검색할 때 `conversations` 목록을 순회하며 각 채팅방마다 `participantRepository.findByConversationIdxAndStatus()` 쿼리가 실행됨. 채팅방 수 N만큼 SELECT 발생.

**현재 코드**:
```java
Optional<Conversation> existing = conversations.stream()
    .filter(conv -> {
        List<ConversationParticipant> participants = participantRepository
            .findByConversationIdxAndStatus(conv.getIdx(), ParticipantStatus.ACTIVE); // N회 쿼리
        Set<Long> ids = participants.stream()
            .map(p -> p.getUser().getIdx()).collect(Collectors.toSet());
        return ids.contains(reporterId) && ids.contains(witnessId);
    }).findFirst();
```

**개선 코드**:
```java
// 배치 조회로 1회 쿼리
List<Long> convIdxs = conversations.stream().map(Conversation::getIdx).toList();
List<ConversationParticipant> allParticipants = participantRepository
    .findParticipantsByConversationIdxsAndStatus(convIdxs, ParticipantStatus.ACTIVE);

Map<Long, Set<Long>> participantIdsByConv = allParticipants.stream()
    .collect(Collectors.groupingBy(
        p -> p.getConversation().getIdx(),
        Collectors.mapping(p -> p.getUser().getIdx(), Collectors.toSet())));

Optional<Conversation> existing = conversations.stream()
    .filter(conv -> {
        Set<Long> ids = participantIdsByConv.getOrDefault(conv.getIdx(), Set.of());
        return ids.contains(reporterId) && ids.contains(witnessId);
    }).findFirst();
```

---

## 🟡 Warning (3건)

### 1. [A3] 메시지 검색 — 양방향 LIKE로 인덱스 미사용

**파일**: `SpringDataJpaChatMessageRepository.java:117`

**문제**: `LIKE CONCAT('%', :keyword, '%')` — 앞에 `%`가 붙으면 B-tree 인덱스를 타지 못해 Full Table Scan 발생. 메시지 수 증가 시 검색 성능 급격히 저하.

**개선**: MySQL FULLTEXT 인덱스 + `MATCH ... AGAINST` 전환 권장
```sql
ALTER TABLE chatmessage ADD FULLTEXT INDEX ft_content (content);
```
```java
@Query(value = "SELECT * FROM chatmessage " +
               "WHERE conversation_idx = :convIdx " +
               "AND MATCH(content) AGAINST(:keyword IN BOOLEAN MODE) " +
               "AND is_deleted = false " +
               "ORDER BY created_at DESC", nativeQuery = true)
List<ChatMessage> searchMessagesByKeyword(...);
```

---

### 2. [D1] DB 레벨 유니크 제약 없음

**파일**: `ConversationParticipant.java`, `Conversation.java`

**문제**: 코드 레벨에서 중복 체크를 하지만 동시 요청 시 중복 레코드 생성 가능. DB 제약이 없으므로 Race Condition에 취약.

**개선 코드**:
```java
// ConversationParticipant.java
@Table(name = "conversationparticipant",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_conversation_user",
        columnNames = {"conversation_idx", "user_idx"}))

// Conversation.java
@Table(name = "conversation",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_related_type_idx",
        columnNames = {"related_type", "related_idx"}))
```
유니크 제약 추가 후 `DataIntegrityViolationException` catch → 도메인 예외 변환도 함께 추가.

---

### 3. [트랜잭션] `ConversationService` 조회 메서드에 `@Transactional(readOnly = true)` 누락

**파일**: `ConversationService.java:140`(`getConversation`), `ConversationService.java:469`(`getMeetupChatParticipantCount`)

**문제**: `ChatMessageService`는 클래스 레벨에 `@Transactional(readOnly = true)`가 있지만, `ConversationService`는 없음. 트랜잭션 없이 조회 메서드가 실행되면 Lazy loading 시마다 별도 커넥션이 열려 성능 저하 및 `LazyInitializationException` 위험.

**개선**:
```java
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true) // 추가
public class ConversationService {
    // 쓰기 메서드는 개별 @Transactional로 오버라이드 (기존과 동일)
}
```

---

## 🟢 Info (1건)

### 1. [E3] `SendMessageRequest`, `CreateConversationRequest` — record 변환 가능

두 DTO 모두 setter 미사용, 생성자 접근만 함. `record`로 전환 시 코드량 감소.

---

## ✅ 잘된 점

- **FetchType.LAZY 완벽 준수**: 모든 `@ManyToOne` / `@OneToOne`에 LAZY 명시 (A1)
- **`getMyConversations()` 배치 조회 최적화**: 3번의 일괄 SELECT로 N+1 방지 — 내 참여자 정보·전체 참여자·최신 메시지를 각각 IN 쿼리로 처리
- **`incrementUnreadCount()` 원자적 UPDATE**: DB 레벨 원자적 증가 쿼리로 Lost Update 방지 (B3)
- **`confirmCareDeal()` 비관적 락**: `findByIdWithLock(PESSIMISTIC_WRITE)` 적용으로 동시 거래 확정 제어
- **소프트 삭제 일관 적용**: 대부분의 Repository 쿼리에 `isDeleted = false` 조건 포함
- **도메인 Repository 분리**: 인터페이스 + JPA 어댑터 패턴 일관 적용

---

## 수정 우선순위

| 순위 | 항목 | 난이도 | 영향도 |
|------|------|--------|--------|
| 1 | 보안 (C): userId → JWT 추출 방식 전환 | 중간 | 🔴 높음 |
| 2 | 트랜잭션 (B5): createConversation 서비스 분리 | 중간 | 🔴 높음 |
| 3 | N+1 (A2): createMissingPetChat 배치 조회 | 낮음 | 🔴 높음 |
| 4 | 유니크 제약 (D1): DB 레벨 제약 추가 | 낮음 | 🟡 중간 |
| 5 | readOnly 트랜잭션 (B): ConversationService 클래스 레벨 추가 | 낮음 | 🟡 중간 |
| 6 | 검색 최적화 (A3): FULLTEXT 전환 | 높음 | 🟢 낮음 (데이터량 의존) |

---

## 관련 문서

- 도메인 스펙: `docs/domains/chat.md`
- 채팅 아키텍처: `docs/architecture/채팅 시스템 설계.md`
- 채팅 예외처리: `docs/refactoring/exception/chat/채팅예외처리.md`
- DB 개념: `docs/db_concept/db-concept-highlights-chat.md`
