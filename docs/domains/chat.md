# Chat 도메인

> 기준: 현재 `backend/main/java/com/linkup/Petory/domain/chat`, `global/websocket`, `frontend/src/api/chatApi.js`, `frontend/src/components/Chat` 코드.  
> 역할: 사용자 간 실시간 대화, 도메인 연동 채팅방, 읽음 상태, 거래 확정 트리거를 관리한다.

---

## 1. 도메인 책임

Chat 도메인은 Petory 안에서 발생하는 대화를 하나의 공통 채팅 모델로 묶는다.

- 일반 1:1 채팅
- 펫케어 거래 채팅
- 실종 제보자-목격자 채팅
- 산책 모임 단체 채팅
- 그룹/관리자 지원 채팅 타입
- REST 메시지 전송/조회/검색/삭제
- STOMP WebSocket 기반 실시간 메시지 수신
- 참여자별 `unreadCount`, `lastReadMessage`, `lastReadAt` 관리
- 펫케어 양측 거래 확정 시 Care/Payment 흐름 호출

채팅은 특정 도메인 엔티티를 직접 상속하거나 분리 테이블로 따로 만들지 않고, `Conversation.relatedType` + `relatedIdx`로 Care, MissingPet, Meetup에 연결한다.

---

## 2. 주요 코드 위치

| 영역                    | 파일                                                                                                        |
| ----------------------- | ----------------------------------------------------------------------------------------------------------- |
| 채팅방 REST API         | `domain/chat/controller/ConversationController.java`                                                        |
| 메시지 REST API         | `domain/chat/controller/ChatMessageController.java`                                                         |
| STOMP 메시지 처리       | `domain/chat/controller/ChatWebSocketController.java`                                                       |
| 채팅방 서비스           | `domain/chat/service/ConversationService.java`                                                              |
| 채팅방 생성 전용 서비스 | `domain/chat/service/ConversationCreatorService.java`                                                       |
| 메시지 서비스           | `domain/chat/service/ChatMessageService.java`                                                               |
| WebSocket 설정          | `global/websocket/config/WebSocketConfig.java`                                                              |
| WebSocket 인증          | `global/websocket/security/WebSocketAuthenticationInterceptor.java`, `WebSocketAuthChannelInterceptor.java` |
| 프론트 API              | `frontend/src/api/chatApi.js`                                                                               |
| 프론트 UI               | `frontend/src/components/Chat/*`                                                                            |

---

## 3. 데이터 모델

### 3.1 Conversation

`Conversation`은 채팅방 자체를 표현한다.

| 필드                     | 의미                                                                        |
| ------------------------ | --------------------------------------------------------------------------- |
| `conversationType`       | `DIRECT`, `GROUP`, `CARE_REQUEST`, `MISSING_PET`, `MEETUP`, `ADMIN_SUPPORT` |
| `title`                  | 그룹/도메인 채팅방 제목                                                     |
| `relatedType`            | `CARE_REQUEST`, `CARE_APPLICATION`, `MISSING_PET_BOARD`, `MEETUP`, `USER`   |
| `relatedIdx`             | 연결된 도메인 엔티티 ID                                                     |
| `status`                 | `ACTIVE`, `CLOSED`, `ARCHIVED`                                              |
| `lastMessageAt`          | 채팅방 목록 정렬용 마지막 메시지 시각                                       |
| `lastMessagePreview`     | 채팅방 목록 표시용 메시지 미리보기                                          |
| `isDeleted`, `deletedAt` | 채팅방 soft delete                                                          |

### 3.2 ConversationParticipant

`ConversationParticipant`는 채팅방별 사용자 상태를 관리한다. 읽음 상태와 거래 확정 상태가 이 테이블에 있다.

| 필드                               | 의미                                |
| ---------------------------------- | ----------------------------------- |
| `conversation`, `user`             | 채팅방-사용자 연결                  |
| `role`                             | `MEMBER`, `ADMIN`, `MODERATOR`      |
| `status`                           | `ACTIVE`, `LEFT`, `KICKED`, `MUTED` |
| `unreadCount`                      | 참여자별 안 읽은 메시지 수          |
| `lastReadMessage`, `lastReadAt`    | 마지막 읽음 위치                    |
| `joinedAt`, `leftAt`               | 참여/퇴장 시각                      |
| `dealConfirmed`, `dealConfirmedAt` | 펫케어 거래 확정 여부               |
| `isDeleted`, `deletedAt`           | 참여자 soft delete                  |

### 3.3 ChatMessage

`ChatMessage`는 메시지 본문을 저장한다.

| 필드                     | 의미                                        |
| ------------------------ | ------------------------------------------- |
| `conversation`           | 소속 채팅방                                 |
| `sender`                 | 발신자                                      |
| `messageType`            | `TEXT`, `IMAGE`, `FILE`, `SYSTEM`, `NOTICE` |
| `content`                | 텍스트 또는 파일 URL                        |
| `replyToMessage`         | 답장 대상 메시지                            |
| `isDeleted`, `deletedAt` | 메시지 soft delete                          |

---

## 4. API

모든 REST API는 인증 사용자를 `AuthenticatedUserIdResolver.requireCurrentUserIdx()`로 읽는다. 클라이언트가 `userId`, `senderIdx`를 넘겨 발신자를 결정하지 않는다.

### 4.1 채팅방 API

| Method   | Path                                                           | 설명                                   |
| -------- | -------------------------------------------------------------- | -------------------------------------- |
| `GET`    | `/api/chat/conversations`                                      | 내 활성 채팅방 목록                    |
| `GET`    | `/api/chat/conversations/{conversationIdx}`                    | 채팅방 상세                            |
| `POST`   | `/api/chat/conversations`                                      | 범용 채팅방 생성                       |
| `POST`   | `/api/chat/conversations/direct?otherUserId=`                  | 1:1 채팅방 조회 또는 생성              |
| `POST`   | `/api/chat/conversations/care-request?careApplicationIdx=`     | 펫케어 지원 기반 채팅방 조회 또는 생성 |
| `POST`   | `/api/chat/conversations/{conversationIdx}/leave`              | 채팅방 나가기                          |
| `DELETE` | `/api/chat/conversations/{conversationIdx}`                    | 채팅방 soft delete                     |
| `PATCH`  | `/api/chat/conversations/{conversationIdx}/status?status=`     | 채팅방 상태 변경                       |
| `POST`   | `/api/chat/conversations/meetup/{meetupIdx}/join`              | 모임 채팅방 참여                       |
| `GET`    | `/api/chat/conversations/meetup/{meetupIdx}/participant-count` | 모임 채팅 활성 참여자 수               |
| `POST`   | `/api/chat/conversations/{conversationIdx}/confirm-deal`       | 펫케어 거래 확정                       |

### 4.2 메시지 API

| Method   | Path                                                                         | 설명                       |
| -------- | ---------------------------------------------------------------------------- | -------------------------- |
| `POST`   | `/api/chat/messages`                                                         | REST 메시지 전송           |
| `GET`    | `/api/chat/messages/conversation/{conversationIdx}?page=&size=`              | 메시지 페이지 조회         |
| `GET`    | `/api/chat/messages/conversation/{conversationIdx}/before?beforeDate=&size=` | 커서 기반 이전 메시지 조회 |
| `POST`   | `/api/chat/messages/conversation/{conversationIdx}/read?lastMessageIdx=`     | 읽음 처리                  |
| `DELETE` | `/api/chat/messages/{messageIdx}`                                            | 본인 메시지 soft delete    |
| `GET`    | `/api/chat/messages/conversation/{conversationIdx}/search?keyword=`          | 메시지 FULLTEXT 검색       |
| `GET`    | `/api/chat/messages/conversation/{conversationIdx}/unread-count`             | 내 안 읽은 메시지 수       |

### 4.3 STOMP WebSocket

| 구분              | 경로                                                   |
| ----------------- | ------------------------------------------------------ |
| 연결 엔드포인트   | `/ws`, `/chat`                                         |
| 클라이언트 → 서버 | `/app/chat.send`, `/app/chat.read`, `/app/chat.typing` |
| 메시지 구독       | `/topic/conversation/{conversationIdx}`                |
| 타이핑 구독       | `/topic/conversation/{conversationIdx}/typing`         |
| 에러 큐           | `/user/{principal}/queue/errors`                       |

클라이언트는 SockJS로 `http://localhost:8080/ws?token=<JWT>`에 연결하고, STOMP `Authorization: Bearer <token>` 헤더도 함께 보낸다.

---

## 5. 주요 흐름

### 5.1 내 채팅방 목록

`ConversationService.getMyConversations()`는 N+1을 피하기 위해 채팅방 목록을 가져온 뒤 필요한 부가 정보를 배치 조회한다.

1. `ConversationRepository.findActiveConversationsByUser(userId, ACTIVE)`
2. 채팅방 ID 목록 추출
3. 내 `ConversationParticipant` 배치 조회
4. 모든 ACTIVE 참여자 배치 조회
5. 각 채팅방 최신 메시지 배치 조회
6. DTO에 `unreadCount`, `participantCount`, `participants`, `lastMessage` 조립

정렬 기준은 `lastMessageAt DESC NULLS LAST, createdAt DESC`다.

### 5.2 채팅방 생성

채팅방 생성은 `ConversationService`가 직접 만들지 않고 `ConversationCreatorService.createConversation()`에 위임한다.

핵심 규칙:

- `@Transactional(REQUIRES_NEW)`로 생성 트랜잭션을 분리한다.
- `actingUserId`는 반드시 `participantUserIds`에 포함되어야 한다.
- 삭제된 사용자는 참여자에서 제외한다.
- `MEETUP`은 최소 1명, 그 외 타입은 최소 2명이 필요하다.
- `relatedType + relatedIdx`가 있으면 기존 채팅방을 먼저 찾는다.
- `DIRECT` 2인 채팅은 기존 방이 있으면 재사용한다.
- 기존 DIRECT 방에 `relatedType/relatedIdx`가 비어 있으면 관련 도메인 정보를 채울 수 있다.

### 5.3 메시지 전송

REST 전송과 WebSocket 전송 모두 최종적으로 `ChatMessageService.sendMessage()`를 호출한다.

1. 발신자 존재 및 탈퇴 여부 확인
2. 채팅방 존재 확인
3. 발신자가 `ACTIVE` 참여자인지 확인
4. `ChatMessage` 저장
5. 발신자를 제외한 ACTIVE 참여자 `unreadCount` 원자적 증가
6. `Conversation.lastMessageAt`, `lastMessagePreview` 갱신
7. WebSocket 경로에서는 `/topic/conversation/{conversationIdx}`로 브로드캐스트

`unreadCount`는 참여자를 루프 돌며 읽고 쓰지 않고 DB `UPDATE conversationparticipant SET unread_count = unread_count + 1`로 증가시켜 Lost Update 위험을 줄인다.

### 5.4 메시지 조회와 재참여 제한

`ChatMessageService.getMessages()`는 ACTIVE 참여자만 접근 가능하다.

- 기본 페이지 조회는 `createdAt DESC`다.
- 참여자의 `lastReadMessage == null`이고 `joinedAt`이 있으면 재참여로 간주한다.
- 이 경우 `joinedAt` 이후 메시지만 조회한다.

단, `/before` 커서 API는 현재 `joinedAt` 기준 필터를 적용하지 않는다. ACTIVE 참여자 검증은 하지만, 재참여 사용자의 과거 커서 조회 제한은 추가 보완 지점이다.

### 5.5 읽음 처리

`markAsRead()`는 전체 메시지를 스캔하지 않는다.

1. ACTIVE 참여자 검증
2. 해당 참여자의 `unreadCount = 0`
3. `lastMessageIdx`가 있으면 `lastReadMessage`, `lastReadAt` 갱신

프론트 `ChatRoom.js`는 초기 로드 후 마지막 메시지를 즉시 읽음 처리하고, 실시간 수신 메시지는 500ms 디바운스로 묶어서 호출한다.

### 5.6 메시지 검색

`searchMessages()`는 ACTIVE 참여자 검증 후 `MATCH(m.content) AGAINST(:keyword IN NATURAL LANGUAGE MODE)`를 사용한다.

구현 방식:

1. 네이티브 FULLTEXT 쿼리로 메시지 `idx` 목록 조회
2. `findByIdxInWithAssociations()`로 발신자/답장 연관을 fetch
3. 1단계 검색 결과 순서를 유지해 DTO 변환

DB에 `chatmessage(content)` FULLTEXT 인덱스가 없으면 검색 쿼리가 실패할 수 있다.

---

## 6. 도메인 연동

### 6.1 Care

`POST /api/chat/conversations/care-request?careApplicationIdx=`는 `CareApplication`을 조회해 요청자와 제공자만 방을 만들 수 있게 한다.

현재 생성 결과:

- `conversationType = CARE_REQUEST`
- `relatedType = CARE_APPLICATION`
- `relatedIdx = careApplicationIdx`
- 참여자: 보호자 + 제공자

`confirmCareDeal()`은 채팅방을 비관적 락으로 조회하고, 참여자별 `dealConfirmed`를 기록한다. 양쪽 모두 확정하면 `relatedType == CARE_REQUEST`인 경우에만 다음 처리를 수행한다.

- `CareRequest` 상태 `OPEN → IN_PROGRESS`
- 기존 지원이 있으면 `CareApplication.accept()`
- 지원이 없으면 `CareApplication` 생성 후 `ACCEPTED`
- `PetCoinEscrowService.createEscrow()` 호출

주의: 현재 `createCareRequestConversation()`이 만드는 방은 `relatedType = CARE_APPLICATION`이다. 이 분기에서는 `confirmCareDeal()`이 상태 변경/에스크로 생성을 하지 않고 로그만 남긴다. Care 거래 확정 플로우를 실제 운영 흐름으로 쓰려면 `CARE_REQUEST`/`CARE_APPLICATION` 연결 정책을 정리해야 한다.

### 6.2 Meetup

모임 채팅방은 모임 생성 후 이벤트로 생성된다.

1. `MeetupService.createMeetup()`
2. 트랜잭션 커밋 이후 `MeetupCreatedEvent` 발행
3. `MeetupChatRoomEventListener`가 비동기로 수신
4. `MeetupChatRoomCreationService.createChatRoom()`이 채팅방 생성
5. 주최자를 `ADMIN` 역할로 설정

모임 참여 API인 `POST /api/meetups/{meetupIdx}/participants`는 모임 참여 인원만 처리한다. 채팅방 입장은 별도 API인 `POST /api/chat/conversations/meetup/{meetupIdx}/join`에서 처리한다.

`joinMeetupChat()`은 먼저 사용자가 실제 모임 참여자인지 `MeetupParticipantsRepository.existsByMeetupIdxAndUserIdx()`로 확인한다. 기존 참여자가 `LEFT` 상태면 `ACTIVE`로 되돌리고 `joinedAt`, `lastReadMessage`, `lastReadAt`, `unreadCount`를 초기화한다.

`leaveMeetupChat()`은 모임 채팅방이 없으면 무시하고, 참여자가 있으면 `LEFT`로 변경한다. 모임 참여 취소 흐름에서 호출되지만, 채팅방 나가기 실패가 모임 취소를 롤백하지는 않는다.

### 6.3 MissingPet

실종 제보 채팅은 `ConversationService.createMissingPetChat(boardIdx, reporterId, witnessId)`로 생성된다.

규칙:

- 제보자와 목격자가 같으면 생성 불가
- 같은 실종 게시글(`MISSING_PET_BOARD + boardIdx`) 안에서도 제보자-목격자 조합별로 별도 채팅방을 만든다.
- 기존 방 탐색 시 관련 채팅방 목록을 먼저 가져오고, 참여자들을 배치 조회한 뒤 메모리에서 조합을 찾는다.

프론트에서는 `chatApi.startMissingPetChat()`이 `missingPetApi.startChat(boardIdx)`를 호출한다. 실제 채팅 시작 엔드포인트는 MissingPetBoard 컨트롤러 쪽에 있다.

---

## 7. 보안과 권한

- REST API는 모두 인증 사용자 기준으로 동작한다.
- 메시지 조회, 커서 조회, 검색, 읽음, unread 조회, 삭제는 `requireActiveParticipant()`를 통과해야 한다.
- 메시지 삭제는 발신자 본인만 가능하다.
- 채팅방 상세 조회는 ACTIVE 참여자만 가능하며, 참여자 중 탈퇴 사용자가 있으면 유효하지 않은 채팅방으로 처리한다.
- WebSocket 연결은 핸드셰이크 단계에서 JWT를 검증한다.
- STOMP `CONNECT`, `SUBSCRIBE`, `SEND`는 채널 인터셉터에서 다시 인증을 확인한다.
- `ConversationCreatorService`는 요청자가 참여자 목록에 없는 채팅방 생성을 막는다.

---

## 8. 현재 한계와 주의사항

- `createCareRequestConversation()`은 `CARE_APPLICATION` 기반 방을 만들지만, 실제 거래 확정 상태 변경/에스크로 생성은 `CARE_REQUEST` 분기에서만 동작한다.
- `confirmCareDeal()`에서 에스크로 생성 실패를 잡아서 로그만 남기고 거래 확정 자체는 진행한다.
- `confirmCareDeal()` 일부 예외는 아직 `IllegalArgumentException`, `IllegalStateException`, `RuntimeException`을 사용한다.
- `PATCH /conversations/{id}/status`는 ACTIVE 참여자면 가능하다. 방장/관리자 전용 정책은 아직 없다.
- `/before` 커서 메시지 조회는 ACTIVE 참여자 검증은 하지만 재참여자의 `joinedAt` 이후 제한을 적용하지 않는다.
- `ConversationParticipant`에 단순 `(conversation_idx, user_idx)` 유니크 제약을 걸기 어렵다. LEFT/soft delete 이력과 재참여 정책을 먼저 정리해야 한다.
- `ADMIN_SUPPORT`, `GROUP`, `SYSTEM`, `NOTICE`, `FILE` 타입은 모델에 있지만 사용자 플로우는 일부만 구현되어 있다.
- WebSocket은 Spring SimpleBroker 기반이다. 다중 서버 확장 시 외부 브로커, 세션 공유, 메시지 전달 보장이 별도 설계 대상이다.
- FULLTEXT 검색은 DB 인덱스가 실제 환경에 적용되어야 안정적으로 동작한다.

---

## 9. DomainV2 페이지에 넣을 포인트

- 채팅방 생성 규칙을 `ConversationCreatorService`로 분리해 `REQUIRES_NEW` self-invocation 문제를 제거했다.
- REST 발신자/사용자 식별을 클라이언트 파라미터가 아니라 JWT principal 기준으로 고정했다.
- `ConversationParticipant.unreadCount`를 DB 원자적 UPDATE로 증가시켜 동시 메시지 전송 시 Lost Update 위험을 줄였다.
- 읽음 처리는 `MessageReadStatus` 테이블 없이 참여자 상태만 갱신하도록 단순화했다.
- 채팅방 목록은 참여자, 내 unread, 최신 메시지를 배치 조회해 N+1을 줄였다.
- 실종 제보 채팅은 관련 방 목록을 먼저 모은 뒤 참여자 배치 조회로 제보자-목격자 조합을 찾는다.
- 모임 채팅방은 모임 생성 트랜잭션과 분리된 after-commit 이벤트/비동기 생성/복구 흐름으로 처리한다.
- Care 거래 확정과 Payment 에스크로 연결에는 현재 `CARE_REQUEST`/`CARE_APPLICATION` 연결 정책 불일치가 남아 있다.
