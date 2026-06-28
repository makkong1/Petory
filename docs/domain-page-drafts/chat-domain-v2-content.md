# ChatDomainV2 페이지 드래프트 점검 및 작성

> 목적: 포트폴리오 repo의 `ChatDomainV2.jsx` 현재 내용과 Petory 코드/문서를 비교해, 실제 페이지에 넣을 문구와 수정 포인트를 확정한다.  
> 작성 기준: 현재 구현을 1순위로 보고, `docs/domains/chat.md`, 채팅 아키텍처, chat 리팩토링/트러블슈팅 문서는 근거와 맥락으로만 사용한다.

---

## 0. 판단

현재 첨부한 `ChatDomainV2.jsx`는 큰 구조와 방향이 좋다. Chat 도메인은 단순 WebSocket 메시지 송수신보다, Care·MissingPet·Meetup에서 공통으로 쓰는 채팅 생성 규칙과 참여자 상태 관리가 포트폴리오 포인트다.

그대로 진행해도 되는 부분:

- `ConversationCreatorService`로 채팅 생성 규칙을 중앙화한 설명
- unread count 원자적 증가 설명
- 읽음 처리에서 전체 메시지 조회와 `MessageReadStatus` 기록 로직을 제거한 설명
- 재참여 시 `joinedAt` 이후 메시지만 보여주는 정책
- 채팅방 목록에서 참여자/내 unread/최신 메시지를 배치 조회하는 설명
- Care 거래 확정의 `CARE_APPLICATION` 분기 한계와 에스크로 실패 롤백 미구현 한계

보완하면 좋은 부분:

- `relatedType + relatedIdx` 기존 방 재사용은 "기존 방이 있으면 무조건 재사용"이 아니라, 활성 참여자 집합이 새 참여자 집합과 같을 때 재사용한다.
- REST API는 현재 `AuthenticatedUserIdResolver`로 사용자 idx를 결정한다. 클라이언트가 `senderIdx`, `userId`로 발신자를 정하지 않는다는 점을 강조해도 좋다.
- WebSocket 경로는 `Principal.getName()` 로그인 ID를 `UsersRepository.findByIdString()`으로 다시 조회한다.
- `/before` 커서 조회는 ACTIVE 참여자 검증은 하지만 재참여자의 `joinedAt` 제한을 적용하지 않는다. 한계 섹션에 유지한다.
- Spring SimpleBroker 기반이라 다중 서버 확장 시 외부 브로커가 필요하다는 운영 한계를 추가하면 좋다.

---

## 1. 페이지 상단

### H1

채팅 도메인

### 소개 문단

Chat 도메인은 Petory의 실시간 대화를 담당하지만, 실제 역할은 Care·MissingPet·Meetup의 비즈니스 흐름을 연결하는 공용 인프라에 가깝다. 단순히 WebSocket을 붙이는 것보다, 도메인별 채팅방 생성 규칙을 한 곳으로 모으고, 참여자별 unread count를 원자적으로 갱신하며, 읽음 처리와 재참여 메시지 노출 범위를 정리하는 것이 핵심이었다. 현재 구조는 REST와 WebSocket 전송이 모두 `ChatMessageService.sendMessage()`로 모이고, 채팅방 목록은 참여자·최신 메시지 정보를 배치 조회해 N+1을 줄인다.

### 핵심 기능 태그 (`corePillars`)

첨부 JSX의 5개 태그는 그대로 사용 가능하다.

```javascript
const corePillars = [
  '채팅 생성 규칙 중앙화',
  'unread count 원자적 갱신',
  '읽음 처리 최적화',
  '재참여 메시지 제한',
  '참여자 N+1 개선',
];
```

선택적으로 WebSocket을 첫 화면에서 강조하고 싶으면 `"STOMP WebSocket"`을 추가할 수 있지만, 현재 페이지의 강점은 인프라 연결과 상태 관리이므로 5개 구성이 더 선명하다.

---

## 2. `section#intro` - 도메인 개요

### 2-1. 개요 카드 문구

채팅 기능은 "메시지를 실시간으로 보낸다"에서 끝나지 않는다. Care에서는 거래 확정이 Payment 에스크로로 이어지고, MissingPet에서는 같은 실종 게시글 안에서도 제보자-목격자 조합별 방이 필요하며, Meetup에서는 모임 생성 이후 채팅방 생성과 재참여 정책이 필요하다. Chat 도메인은 이 차이를 `ConversationType`, `RelatedType`, `relatedIdx`, `ConversationParticipant` 상태로 흡수한다.

메시지 전송은 REST와 WebSocket 모두 최종적으로 `ChatMessageService.sendMessage()`를 호출한다. 서비스는 발신자, 채팅방, ACTIVE 참여자를 검증한 뒤 메시지를 저장하고, 발신자를 제외한 ACTIVE 참여자의 unread count를 DB 원자적 update로 증가시킨다. 읽음 처리는 전체 메시지를 다시 읽지 않고 `ConversationParticipant.unreadCount`, `lastReadMessage`, `lastReadAt`만 갱신한다.

채팅방 목록은 `ConversationService.getMyConversations()`에서 채팅방 목록을 가져온 뒤, 내 참여자 정보, 전체 ACTIVE 참여자, 최신 메시지를 각각 배치 조회해 DTO에 직접 세팅한다. 이 구조는 Converter에서 LAZY 컬렉션을 건드려 채팅방 수만큼 추가 쿼리가 발생하던 문제를 피한다.

### 2-2. 구조 테이블

| 항목 | 현재 코드 기준 |
|---|---|
| 채팅방 API | `ConversationController`의 `/api/chat/conversations` |
| 메시지 API | `ChatMessageController`의 `/api/chat/messages` |
| WebSocket API | `ChatWebSocketController`, `/app/chat.send`, `/app/chat.read`, `/app/chat.typing` |
| REST 사용자 식별 | `AuthenticatedUserIdResolver.requireCurrentUserIdx()` |
| WebSocket 사용자 식별 | `Principal.getName()` 로그인 ID -> `UsersRepository.findByIdString()` |
| 생성 중앙화 | `ConversationCreatorService.createConversation()` |
| 생성 트랜잭션 | `@Transactional(propagation = REQUIRES_NEW)` |
| 도메인 연결 | `relatedType + relatedIdx` |
| unread 증가 | `ConversationParticipant` DB 원자적 update |
| 읽음 처리 | 참여자 상태 `unreadCount`, `lastReadMessage`, `lastReadAt` 갱신 |
| 재참여 제한 | `lastReadMessage == null && joinedAt != null`이면 `joinedAt` 이후 메시지만 조회 |
| 검색 | `ChatMessage.content` FULLTEXT idx 검색 후 fetch 재조회 |

### 2-3. Before/After 테이블

첨부 JSX의 표는 그대로 사용 가능하다. 수치형 성능 표보다 구조 개선 표가 이 페이지에 더 적합하다.

| 항목 | Before | After |
|---|---|---|
| 읽음 처리 메시지 조회 | 전체 메시지 조회, Java 필터링 | `unreadCount`, `lastReadMessage` 필드만 갱신 |
| unread count 갱신 | 참여자별 루프 save, Lost Update 위험 | DB 원자적 update 1회 |
| 채팅방 목록 참여자 조회 | Converter LAZY 컬렉션 접근으로 N+1 | 참여자/최신 메시지 배치 조회 후 서비스에서 DTO 세팅 |

보조 설명:

- 읽음 처리 개선은 `docs/troubleshooting/chat/read-status-performance.md`에 근거가 있다.
- 참여자 N+1 개선은 `docs/troubleshooting/chat/n-plus-one-conversationparticipant.md`에 근거가 있다.
- 특정 ms 수치를 새로 만들지 말고 구조 개선 중심으로 표현한다.

### 2-4. 데이터 흐름 카드

문구:

Care, MissingPet, Meetup 연계 플로우는 통합 흐름 페이지에 모아 둔다. 같은 Chat 기반이지만 생성 조건, 참여자 검증, 재참여 정책, 거래 확정 후속 처리가 도메인별로 달라지는 지점을 비교해 보여준다.

내부 링크:

- `/domains/flows?tab=chat&seq=care`
- `/domains/flows?tab=chat&seq=missingpet`
- `/domains/flows?tab=chat&seq=meetup`
- `/domains/flows`

---

## 3. `section#design` - 기술 결정

첨부 JSX의 A~E 구성은 적절하다. 아래 문구와 조건만 보완하면 된다.

### A. 채팅 생성 규칙 중앙화

핵심 문구:

채팅방 생성 규칙은 여러 도메인에 흩어지면 중복 방, 권한 우회, 트랜잭션 전파 문제가 쉽게 생긴다. 현재 구조는 실제 생성 로직을 `ConversationCreatorService`로 분리하고 `REQUIRES_NEW` 트랜잭션을 적용한다. 이 분리는 동일 클래스 내부 호출로는 트랜잭션 프록시가 적용되지 않는 self-invocation 문제를 피하기 위한 선택이다.

규칙:

- `actingUserId`는 반드시 참여자 목록에 포함되어야 한다.
- 삭제된 사용자는 참여자에서 제외한다.
- `MEETUP`은 최소 1명, 그 외 타입은 최소 2명이 필요하다.
- `relatedType + relatedIdx` 기존 방은 활성 참여자 집합이 새 참여자 집합과 같을 때 재사용한다.
- `DIRECT` 2인 채팅은 기존 1:1 방이 있으면 재사용한다.
- 기존 DIRECT 방에 `relatedType/relatedIdx`가 비어 있으면 관련 도메인 정보를 채울 수 있다.

코드 스니펫 후보:

```java
@Transactional(propagation = Propagation.REQUIRES_NEW)
public ConversationDTO createConversation(
        ConversationType conversationType,
        RelatedType relatedType,
        Long relatedIdx,
        String title,
        List<Long> participantUserIds,
        Long actingUserId) {

    if (!participants.stream().map(Users::getIdx).anyMatch(actingUserId::equals)) {
        throw ChatForbiddenException.notAllowedToCreateConversation();
    }

    if (relatedType != null && relatedIdx != null) {
        Optional<Conversation> existing =
                conversationRepository.findByRelatedTypeAndRelatedIdxAndIsDeletedFalse(
                        relatedType, relatedIdx);
        // 활성 참여자 집합이 같을 때 재사용
    }
}
```

근거:

- `backend/main/java/com/linkup/Petory/domain/chat/service/ConversationCreatorService.java`
- `docs/refactoring/chat/chat-backend-security-transaction-2026-04-14.md`

### B. unread count 원자적 갱신

핵심 문구:

메시지 전송 시 unread count를 참여자별 엔티티로 읽고 저장하면, 동시에 메시지가 들어올 때 Lost Update가 발생할 수 있다. 현재 구현은 메시지를 저장한 뒤 발신자를 제외한 ACTIVE 참여자 전체에 대해 DB update 한 번으로 unread count를 증가시킨다. 동시에 `Conversation.lastMessageAt`, `lastMessagePreview`를 갱신해 채팅방 목록 정렬과 미리보기도 최신화한다.

코드 스니펫 후보:

```java
ChatMessage message = chatMessageRepository.save(message);

participantRepository.incrementUnreadCount(conversationIdx, senderIdx);

conversation.setLastMessageAt(LocalDateTime.now());
conversation.setLastMessagePreview(preview);
conversationRepository.save(conversation);
```

Repository:

```java
@Modifying
@Query("UPDATE ConversationParticipant p SET p.unreadCount = p.unreadCount + 1 " +
       "WHERE p.conversation.idx = :conversationIdx " +
       "AND p.user.idx != :senderUserId " +
       "AND p.status = 'ACTIVE'")
void incrementUnreadCount(Long conversationIdx, Long senderUserId);
```

근거:

- `backend/main/java/com/linkup/Petory/domain/chat/service/ChatMessageService.java`
- `backend/main/java/com/linkup/Petory/domain/chat/repository/SpringDataJpaConversationParticipantRepository.java`

### C. 읽음 처리 최적화

핵심 문구:

이전 읽음 처리는 채팅방의 전체 메시지를 가져와 Java에서 필터링하고, 실제로 쓰지 않는 `MessageReadStatus` 기록 로직까지 포함했다. 현재 구현은 ACTIVE 참여자 검증 후 해당 참여자의 상태 필드만 갱신한다. `lastMessageIdx`가 있으면 해당 메시지만 조회해 `lastReadMessage`, `lastReadAt`을 업데이트하고, unread count를 0으로 초기화한다.

코드 스니펫 후보:

```java
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
```

프론트 보조 설명:

- `ChatRoom.js`는 초기 메시지 로드 후 마지막 메시지를 읽음 처리한다.
- 실시간 수신 메시지는 디바운스로 묶어 호출한다.

근거:

- `backend/main/java/com/linkup/Petory/domain/chat/service/ChatMessageService.java`
- `docs/troubleshooting/chat/read-status-performance.md`
- `docs/architecture/chat/채팅 시스템 설계.md`

### D. 재참여 메시지 제한

핵심 문구:

모임 채팅에서는 사용자가 나갔다가 다시 들어올 수 있다. 이때 이전 대화 전체를 다시 보여주면 참여 시점의 의미가 흐려진다. 현재 구현은 `lastReadMessage == null`이고 `joinedAt`이 있으면 재참여로 보고, 기본 메시지 페이지 조회에서 `joinedAt` 이후 메시지만 반환한다.

코드 스니펫 후보:

```java
LocalDateTime readFrom = null;
if (participant.getLastReadMessage() == null && participant.getJoinedAt() != null) {
    readFrom = participant.getJoinedAt();
}

if (readFrom != null) {
    messages = chatMessageRepository
            .findByConversationIdxAndCreatedAtAfterOrderByCreatedAtDesc(
                    conversationIdx, readFrom, pageable);
} else {
    messages = chatMessageRepository
            .findByConversationIdxOrderByCreatedAtDesc(conversationIdx, pageable);
}
```

한계:

- `/before` 커서 조회는 현재 `joinedAt` 기준 필터를 적용하지 않는다.
- 이 한계는 첨부 JSX의 limits에 유지하는 게 맞다.

근거:

- `backend/main/java/com/linkup/Petory/domain/chat/service/ChatMessageService.java`
- `backend/main/java/com/linkup/Petory/domain/chat/service/ConversationService.java`
- `docs/domains/chat.md`

### E. 참여자 조회 N+1 개선

핵심 문구:

채팅방 목록은 각 방마다 참여자 수, 내 unread count, 최신 메시지가 필요하다. Converter에서 `conversation.getParticipants()` 같은 LAZY 컬렉션을 건드리면 채팅방 수만큼 추가 쿼리가 발생한다. 현재 구현은 채팅방 ID 목록을 기준으로 내 참여자, 전체 ACTIVE 참여자, 최신 메시지를 배치 조회하고 서비스 레이어에서 DTO에 직접 세팅한다.

코드 스니펫 후보:

```java
List<Long> conversationIdxs = conversations.stream()
        .map(Conversation::getIdx)
        .collect(Collectors.toList());

List<ConversationParticipant> myParticipants =
        participantRepository.findParticipantsByConversationIdxsAndUserIdx(conversationIdxs, userId);

List<ConversationParticipant> allParticipants =
        participantRepository.findParticipantsByConversationIdxsAndStatus(
                conversationIdxs, ParticipantStatus.ACTIVE);

List<ChatMessage> latestMessages =
        chatMessageRepository.findLatestMessagesByConversationIdxs(conversationIdxs);
```

근거:

- `backend/main/java/com/linkup/Petory/domain/chat/service/ConversationService.java`
- `docs/troubleshooting/chat/n-plus-one-conversationparticipant.md`

---

## 4. `section#limits` - 한계 & 다음 개선

문구:

Chat 도메인은 공통 채팅 인프라와 주요 성능 병목은 정리했지만, Care 거래 확정 정책과 운영 확장성에는 아직 남은 과제가 있다.

목록:

- Care 거래 확정: `CARE_REQUEST` 경로는 `IN_PROGRESS` 전환과 에스크로 생성을 시도하지만, 에스크로 실패를 롤백하지 않는다.
- Care 거래 확정: `CARE_APPLICATION` 관련 `confirmCareDeal()` 분기는 현재 로그 기록 중심이고 상태 전이/에스크로 후속 처리가 완성되어 있지 않다.
- Meetup 채팅 참여: 모임 참여자 검증은 추가되어 있지만, 채팅 참여 실패가 모임 참여 취소/복구 흐름과 어떻게 결합될지는 정책 여지가 있다.
- 재참여 메시지 제한: 기본 페이지 조회는 `joinedAt` 이후만 보지만, 커서 기반 과거 조회(`/before`)에는 `joinedAt` 제한이 없다.
- 채팅방 상태 변경: `PATCH /status`는 ACTIVE 참여자면 가능하다. 방장/관리자 전용 정책은 아직 없다.
- WebSocket 브로커: 현재 Spring SimpleBroker 기반이다. 다중 서버 확장 시 외부 브로커, 세션 공유, 메시지 전달 보장이 별도 설계 대상이다.
- FULLTEXT 검색: `chatmessage(content)` FULLTEXT 인덱스가 실제 DB에 적용되어야 검색이 안정적으로 동작한다.
- `ConversationParticipant` 유니크 제약은 LEFT/soft delete 이력과 재참여 정책을 먼저 정리해야 한다.
- `ADMIN_SUPPORT`, `GROUP`, `SYSTEM`, `NOTICE`, `FILE` 타입은 모델에 있지만 사용자 플로우는 일부만 구현되어 있다.

---

## 5. `section#docs` - 관련 페이지

### 내부 링크

- `/domains/flows?tab=chat&seq=care` - Chat과 Care 거래 확정 흐름
- `/domains/flows?tab=chat&seq=missingpet` - 실종 제보 채팅 흐름
- `/domains/flows?tab=chat&seq=meetup` - Meetup 채팅 생성/참여 흐름
- `/domains/chat/optimization` - 읽음 처리, 참여자 N+1
- `/domains/chat/refactoring` - 생성 규칙 분리, 트랜잭션/보안 정리
- `/domains/care` - 거래 확정과 에스크로 연결
- `/domains/meetup` - 모임 생성 후 채팅방 생성
- `/domains/missing-pet` - 제보자-목격자 채팅

### GitHub 링크 상수 후보

첨부 JSX의 두 상수는 유지 가능하다. 도메인/아키텍처 문서와 WebSocket 설정 링크를 추가하면 근거가 더 선명해진다.

```javascript
const PETORY_CHAT_DOMAIN_DOC =
  'https://github.com/makkong1/Petory/blob/main/docs/domains/chat.md';
const PETORY_CHAT_ARCH_DOC =
  'https://github.com/makkong1/Petory/blob/main/docs/architecture/chat/%EC%B1%84%ED%8C%85%20%EC%8B%9C%EC%8A%A4%ED%85%9C%20%EC%84%A4%EA%B3%84.md';
const PETORY_CHAT_MESSAGE_SERVICE =
  'https://github.com/makkong1/Petory/blob/main/backend/main/java/com/linkup/Petory/domain/chat/service/ChatMessageService.java';
const PETORY_CONVERSATION_SERVICE =
  'https://github.com/makkong1/Petory/blob/main/backend/main/java/com/linkup/Petory/domain/chat/service/ConversationService.java';
const PETORY_CONVERSATION_CREATOR =
  'https://github.com/makkong1/Petory/blob/main/backend/main/java/com/linkup/Petory/domain/chat/service/ConversationCreatorService.java';
const PETORY_CHAT_READ_DOC =
  'https://github.com/makkong1/Petory/blob/main/docs/troubleshooting/chat/read-status-performance.md';
const PETORY_CHAT_N1_DOC =
  'https://github.com/makkong1/Petory/blob/main/docs/troubleshooting/chat/n-plus-one-conversationparticipant.md';
```

관련 문서:

- `docs/domains/chat.md`
- `docs/architecture/chat/채팅 시스템 설계.md`
- `docs/troubleshooting/chat/read-status-performance.md`
- `docs/troubleshooting/chat/n-plus-one-conversationparticipant.md`
- `docs/refactoring/chat/chat-backend-security-transaction-2026-04-14.md`
- `docs/refactoring/chat/chat-code-review-2026-04-14.md`
- `docs/refactoring/exception/chat/채팅예외처리.md`
- `docs/refactoring/recordType/chat/dto-record-refactoring.md`

---

## 6. 첨부 `ChatDomainV2.jsx` 반영 체크

그대로 진행해도 되는 부분:

- 섹션 순서: `pillars -> intro -> design -> limits -> docs`
- 핵심 태그 5개 구성
- Chat을 Care/MissingPet/Meetup 공용 인프라로 설명하는 방향
- Before/After 테이블을 구조 개선 중심으로 둔 방식
- 생성 규칙 중앙화, unread 원자적 갱신, 읽음 처리 최적화, 재참여 제한, 참여자 N+1 개선 카드
- Care 거래 확정의 에스크로 실패 롤백 미구현 한계
- `CARE_APPLICATION` confirmDeal 분기 미완성 한계
- Meetup 참여자 검증 개선 완료 문구
- `/domains/flows?tab=chat&seq=*` 링크

수정하고 진행할 부분:

- `relatedType + relatedIdx` 기반 기존 채팅방 재사용은 활성 참여자 집합이 같을 때 재사용한다고 보완한다.
- 읽음 처리 프론트 디바운스는 실제 프론트 구현 기준으로 500ms라고 쓰는 편이 문서와 맞다.
- WebSocket 사용 설명을 추가하려면 Spring SimpleBroker 기반이며 다중 서버 확장 한계를 함께 적는다.
- `/before` 커서 조회 한계는 유지한다.
- REST는 JWT principal 기반이고, WebSocket은 Principal 로그인 ID를 user idx로 재조회한다는 차이를 구분한다.
