# Chat 도메인 코드 리뷰 결과

> **최초 작성**: 2026-04-14  
> **최종 갱신**: 2026-04-14 (`/fix`·`/refactor`·`/docs-sync` — REST 보안·REQUIRES_NEW·N+1·FULLTEXT·문서 반영)  
> **대상**: `domain/chat/` 전체  
> **리뷰 기준**: `.claude/skills/review.md` 체크리스트 A~E

---

## 점수판 요약

| 카테고리 | Critical | Warning | Info |
|---------|----------|---------|------|
| JPA/쿼리 (A) | 1 | 1 | 0 |
| 트랜잭션 (B) | 1 | 1 | 0 |
| 보안 (C) | 3 | 0 | 0 |
| 정합성 (D) | 0 | 1 | 0 |
| 코드품질 (E) | 0 | 1 | 1 |
| **합계** | **5** | **4** | **2** |

**판정**: 원본 기준 수정 필요였음. 아래 **조치 요약** 반영 후 Critical·Warning 대부분 해소(잔여: D1 유니크는 소프트 삭제 모델과 충돌 가능으로 보류).

---

## 조치 요약 (2026-04-14)

| 원본 이슈 | 조치 |
|-----------|------|
| C1 IDOR | `ChatMessageController`·`ConversationController`에서 `userId`/`senderIdx` 등 제거, JWT principal만 사용. 펫케어 채팅 생성은 `CareApplication` 기준 당사자 검증. 직접 채팅은 `otherUserId`만 수신. |
| C2 `/before`·`/search` | `ChatMessageService`에 ACTIVE 참여자 검증, `getMessages`·`getUnreadCount`·`deleteMessage`(방 기준) 동일 패턴. |
| C3 `PATCH /status` | `updateConversationStatus`에 ACTIVE 참여자 검증. |
| B5 REQUIRES_NEW | `ConversationCreatorService` 분리 + `MeetupChatRoomEventListener`는 해당 빈 직접 호출. |
| A2 실종 N+1 | `createMissingPetChat`에서 참가자 배치 조회 후 메모리 매칭. |
| A3 검색 | `MATCH(m.content) AGAINST` + `idx_chat_message_content` FULLTEXT(`indexes.sql`). |
| B readOnly | `ConversationService` 클래스 `@Transactional(readOnly = true)`, 쓰기 메서드만 `@Transactional`. |
| E1 WebSocket | 사용자 미존재 시 `UserNotFoundException`으로 통일. |
| 실종 start-chat | `MissingPetBoardController`: 목격자는 토큰 사용자만( `witnessId` 파라미터 제거). |

---

## 🔴 Critical (5건) — 원본 기록

### 1. [C-보안] IDOR — `userId` / `senderIdx`를 `@RequestParam`으로 수신

**파일**: `ChatMessageController.java`, `ConversationController.java` (userId·requesterId·providerId·user1Id·user2Id 등)

**문제**: `@PreAuthorize("isAuthenticated()")`만으로는 **로그인 주체 = 파라미터 userId**가 보장되지 않음. 타인 ID로 메시지 전송·목록·읽음·삭제·채팅방 조작이 가능한 IDOR.

**개선 방향**: `CareRequestController`와 동일하게 **SecurityContext(또는 `@AuthenticationPrincipal`)에서만** 사용자 idx를 얻고, `userId`/`senderIdx` 쿼리 파라미터는 제거.

**조치 (완료)**: 상기와 동일. 프론트 `chatApi.js` 및 `ChatRoom`·`ChatWidget`·`CareLayer` 등 호출부 동기화.

---

### 2. [C-보안] 정보 노출 — `getMessagesBefore` / `searchMessages`에 참여자 검증 없음

**파일**: `ChatMessageController.java` (`/conversation/{id}/before`, `/search`), `ChatMessageService.java` `getMessagesBefore`, `searchMessages`

**문제**: `conversationIdx`(및 검색 키워드)만으로 **인증된 임의 사용자**가 비참여 방의 메시지를 커서 조회·검색할 수 있음. `getMessages`는 `userId`를 받지만 스푸핑 가능하고, **`/before`·`/search`는 userId 자체가 없음**.

**개선 방향**: 서비스 진입 시 `participantRepository.findByConversationIdxAndUserIdx` + `ACTIVE` 검증을 **항상** 수행하고, `userId`는 컨트롤러에서 토큰 기준만 전달.

**조치 (완료)**: `requireActiveParticipant` 패턴으로 통일.

---

### 3. [C-보안] `PATCH .../conversations/{id}/status` — 참여자·역할 검증 없음

**파일**: `ConversationController.java`, `ConversationService.updateConversationStatus`

**문제**: 로그인만 되어 있으면 **임의 채팅방**의 `ACTIVE`/`CLOSED` 등 상태를 변경 가능.

**개선 방향**: (a) 참가자만 허용 + 필요 시 방장만, 또는 (b) 관리자 전용 API로 격리하고 일반 사용자 경로에서는 호출 불가.

**조치 (완료)**: (a) ACTIVE 참여자만 허용. 방장 전용 정책은 미적용(필요 시 별도 이슈).

---

### 4. [B5] Self-invocation으로 `REQUIRES_NEW` 미적용

**파일**: `ConversationService.java` — `createConversation`(`@Transactional(REQUIRES_NEW)`), 호출부 `createCareRequestConversation`, `getOrCreateDirectConversation`, **`createMissingPetChat`**(동일 패턴)

**문제**: 동일 클래스 내 `return createConversation(...)` 호출은 프록시를 거치지 않아 **`Propagation.REQUIRES_NEW`가 무시**됨. 주석의 “별도 트랜잭션” 의도와 실제 동작이 불일치.

**개선 방향**: `ConversationCreatorService` 등 별도 빈으로 분리, 또는 `@Lazy` self 주입 후 `self.createConversation(...)`.

**조치 (완료)**: `ConversationCreatorService` 신설, `actingUserId` 검증 포함.

---

### 5. [A2] `createMissingPetChat()` — 스트림 내부 N+1

**파일**: `ConversationService.java` (실종 제보 채팅 조회 루프)

**문제**: 채팅방마다 `participantRepository.findByConversationIdxAndStatus` 반복 호출.

**개선 방향**: `conversationIdx` 목록으로 참가자 일괄 조회 후 메모리에서 `(reporterId, witnessId)` 매칭(아래 Warning 섹션의 배치 조회 예시와 동일).

**조치 (완료)**: `findParticipantsByConversationIdxsAndStatus` 배치 조회 + `groupingBy` 매칭.

---

## 🟡 Warning (4건)

### 1. [A3] 메시지 검색 — 양방향 `LIKE '%…%'`

**파일**: `SpringDataJpaChatMessageRepository.java` (`searchMessagesByKeyword`)

**문제**: B-tree 인덱스 미활용 → 데이터 증가 시 성능 저하.

**개선**: MySQL FULLTEXT + `MATCH ... AGAINST` 등(기존 문서 예시 유지).

**조치 (완료)**: 네이티브 `MATCH` + `JpaChatMessageAdapter`에서 idx 순서 재조회. DB 인덱스명은 운영과 동일하게 `idx_chat_message_content`(`docs/migration/db/indexes.sql`).

---

### 2. [D1] DB 레벨 유니크 제약 부족

**파일**: `ConversationParticipant.java`, `Conversation.java`

**문제**: 동시 요청 시 동일 `(conversation, user)` 또는 동일 `(related_type, related_idx)` 중복 가능성.

**개선**: `@UniqueConstraint` + `DataIntegrityViolationException` → 도메인 예외 변환.

**조치 (보류)**: `conversationparticipant`에 LEFT·소프트 삭제 이력이 남는 모델에서는 단순 `(conversation_idx, user_idx)` UNIQUE가 중복 데이터·재참여와 충돌할 수 있음. 데이터 정리 또는 부분 유니크 전략 수립 후 별도 마이그레이션 권장.

---

### 3. [B] `ConversationService` — 클래스 레벨 `@Transactional(readOnly = true)` 미적용

**파일**: `ConversationService.java` (`getConversation` 등 일부 조회만 개별 readOnly)

**문제**: 조회 메서드 간 트랜잭션 경계 불균형 → LAZY·커넥션 사용 패턴 불안정 가능.

**개선**: 클래스에 `readOnly = true`, 쓰기 메서드만 `@Transactional`로 오버라이드.

**조치 (완료)**: 클래스에 `@Transactional(readOnly = true)` 적용, 기존 쓰기 메서드에 `@Transactional` 유지.

---

### 4. [E1] WebSocket — 일반 `RuntimeException` 사용

**파일**: `ChatWebSocketController.java` (사용자 조회 실패 등)

**문제**: 도메인 예외와 혼재, 에러 계약 불명확.

**개선**: `UserNotFoundException` 등 기존 예외로 통일.

**조치 (완료)**: `sendMessage` 경로에서 `UserNotFoundException` 사용.

---

## 🟢 Info (2건)

### 1. [E3] `SendMessageRequest`, `CreateConversationRequest` — record 변환 가능

setter 미사용 불변 DTO이면 `record` 검토.

### 2. WebSocket 발신자 결정

`Principal` + `findByIdString`으로 **서버가 sender 결정** — REST `sendMessage`와 대비되는 양호한 패턴(문서 `docs/domains/chat.md` 참고).

---

## ✅ 잘된 점

- **FetchType.LAZY**: 주요 `@ManyToOne`에 LAZY 명시 (A1)
- **`getMyConversations()`**: 배치 조회로 N+1 완화
- **`incrementUnreadCount()`**: 원자적 UPDATE (B3)
- **`confirmCareDeal()`**: 비관적 락으로 동시 확정 제어
- **`ChatMessageService`**: 클래스 레벨 `@Transactional(readOnly = true)` + 쓰기 메서드만 `@Transactional`
- **Repository 쿼리**: 다수 쿼리에 소프트 삭제·발신자 탈퇴 필터 포함
- **도메인 Repository**: 인터페이스 + JPA 어댑터 패턴

---

## 수정 우선순위

| 순위 | 항목 | 난이도 | 영향도 |
|------|------|--------|--------|
| 1 | 보안: REST 토큰 기반 userId + `/before`·`/search`·`PATCH /status` 권한 검증 | 중~상 | 높음 |
| 2 | B5: `createConversation` REQUIRES_NEW 실효 (별도 빈 등) | 중간 | 높음 |
| 3 | A2: `createMissingPetChat` 배치 조회 | 낮음 | 높음 |
| 4 | D1: 유니크 제약 + 예외 매핑 | 낮음 | 중간 |
| 5 | ConversationService readOnly 정리 | 낮음 | 중간 |
| 6 | A3: FULLTEXT 등 검색 최적화 | 높음 | 데이터량 의존 |

---

## 관련 문서

- 도메인 스펙: `docs/domains/chat.md` (API·보안·트랜잭션 주의사항 반영)
- 채팅 아키텍처: `docs/architecture/채팅 시스템 설계.md`
- 채팅 예외처리: `docs/refactoring/exception/chat/채팅예외처리.md`
- DB 개념: `docs/db_concept/db-concept-highlights-chat.md`
