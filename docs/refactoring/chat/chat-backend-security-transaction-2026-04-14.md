# Chat 백엔드 보안·트랜잭션·검색 정리 (2026-04-14)

## 발생 위치

- `domain/chat/controller/*`, `domain/chat/service/ConversationService.java`, `ChatMessageService.java`
- `domain/board/controller/MissingPetBoardController.java` (실종 채팅 시작)
- `domain/meetup/service/MeetupChatRoomEventListener.java`
- `domain/chat/service/ConversationCreatorService.java` (신규)

## 문제 / 개선점

- REST에서 `userId`·`senderIdx` 등 클라이언트 파라미터로 IDOR 가능.
- `getMessagesBefore`·`search`·`PATCH /status` 등 참여자 검증 부재로 정보 노출·무단 상태 변경 가능.
- 동일 클래스 내 `createConversation(REQUIRES_NEW)` self-invocation으로 전파 의도 미달성.
- `createMissingPetChat` 루프 내 참가자 조회 N+1.
- 메시지 검색 `LIKE '%…%'` 풀스캔.

## 개선 코드 (요지)

- 컨트롤러: JWT principal(`Long.parseLong(authentication.getName())`)만 사용자 식별에 사용.
- 서비스: `requireActiveParticipant`로 ACTIVE 참여자 검증 후 메시지 조회·검색·읽음·unread·삭제(방 소속).
- `ConversationCreatorService` + `REQUIRES_NEW`로 채팅방 생성 분리; `actingUserId`가 참여자 목록에 포함되어야 생성 허용.
- 실종 채팅: `POST .../start-chat`는 목격자를 토큰 사용자로만 처리.
- 검색: `MATCH(m.content) AGAINST` + `JpaChatMessageAdapter`에서 idx 순서 재조회.

## 상태

**개선 완료** (2026-04-14). DB에 `idx_chat_message_content` FULLTEXT 미적용 시 검색 쿼리 실패 가능. `ConversationParticipant` 단순 UNIQUE는 소프트 삭제·재참여와 충돌 가능해 별도 설계 후 적용 권장.

## 참고

- 상세 리뷰: `docs/refactoring/chat/chat-code-review-2026-04-14.md`
- 도메인 스펙: `docs/domains/chat.md`
