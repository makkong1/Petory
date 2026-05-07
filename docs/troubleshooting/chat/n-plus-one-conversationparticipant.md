# 채팅방 참여자 조회 N+1 쿼리 문제

## 📋 요약

**케이스 A** (`getConversation`): `participantRepository.findByConversationIdxAndStatus()` 단건 조회 반복

**케이스 B** (`getMyConversations`): `ConversationConverter.toDTO()` 내 `conversation.getParticipants()` 호출로 `@OneToMany(LAZY)` 컬렉션 개별 lazy load 발생 → **수정 완료**

**추가 발견**: `findLatestMessagesByConversationIdxs` 서브쿼리의 컬럼명 오타 (`conversation_ids_deleted` → `is_deleted`)

---

## 1. 증상

### 케이스 A — `getConversation()` 단건 참여자 쿼리

```
-- status 필터 포함, 채팅방 수만큼 반복
select ... from conversationparticipant where conversation_idx=? and status=?
select ... from conversationparticipant where conversation_idx=? and status=?
select ... from conversationparticipant where conversation_idx=? and status=?
```

### 케이스 B — `getMyConversations()` Converter lazy load ✅ 수정 완료

배치 쿼리 3개가 정상 실행된 직후, **status/isDeleted 필터 없는** lazy load 쿼리가 채팅방 수만큼 추가 발생:

```
-- 배치 쿼리 (정상)
[Repository] 채팅 참여자: 채팅방 목록+상태 조회 (배치)  → IN (?,?,?)
[Repository] 채팅 메시지: 채팅방별 최신 메시지 배치 조회 → IN (?,?,?)

-- 그 뒤 N+1 (비정상, 수정 전)
select ... from conversationparticipant where conversation_idx=?  ← lazy load
select ... from conversationparticipant where conversation_idx=?
select ... from conversationparticipant where conversation_idx=?
```

### 영향 범위

- **케이스 A**: `GET /api/conversations/{idx}` (채팅방 단건 조회)
- **케이스 B**: `GET /api/conversations` (채팅방 목록 조회) — 수정 완료
- **심각도**: 채팅방이 많을수록 DB 쿼리 수 선형 증가

---

## 2. 원인 분석

### 2.1 N+1 발생 위치

**파일**: `backend/main/java/com/linkup/Petory/domain/chat/service/ConversationService.java`

**문제 코드** (line 153–154):
```java
// getConversation() 내부 — conversationIdx 하나씩 반복 조회
List<ConversationParticipant> participants =
    participantRepository.findByConversationIdxAndStatus(conversationIdx, ParticipantStatus.ACTIVE);
```

`conversationIdx` 하나를 받는 단건 쿼리를 루프 안에서 호출하므로, 채팅방 수만큼 쿼리가 발생한다.

### 2.2 케이스 B 원인 — Converter에서 LAZY 컬렉션 직접 접근 ✅ 수정 완료

**파일**: `backend/main/java/com/linkup/Petory/domain/chat/converter/ConversationConverter.java`

**문제 코드** (수정 전):
```java
.participantCount(conversation.getParticipants() != null
    ? conversation.getParticipants().size() : 0);  // ← LAZY 컬렉션 접근 → N개 쿼리
```

`getMyConversations()`에서 `Conversation` 엔티티는 `participants` 컬렉션을 초기화하지 않은 채 로드된다. 이 상태에서 컨버터가 `getParticipants()`를 호출하면 Hibernate가 채팅방마다 별도 SELECT를 발행한다.

**수정 내용**:
```java
// ConversationConverter — getParticipants() 접근 제거
.participantCount(0);  // service에서 배치 로드 후 setParticipantCount()로 덮어씀

// ConversationService.getMyConversations() — 배치 데이터로 세팅
dto.setParticipantCount(participants.size());  // participantsMap에서 이미 로드된 값 사용
```

### 2.3 배치 조회가 이미 존재 (케이스 A 참고)

**같은 파일** `getMyConversations()` (line 70–137)는 올바르게 배치 쿼리를 사용 중:
```java
// 이미 올바른 방법이 존재함
List<ConversationParticipant> allParticipants =
    participantRepository.findParticipantsByConversationIdxsAndStatus(
        conversationIdxs, ParticipantStatus.ACTIVE);
```

### 2.3 SQL 오타 (추가 발견)

**위치**: `ConversationRepository` — `findLatestMessagesByConversationIdxs` JPQL/쿼리 서브쿼리

```sql
-- 오타 (실제 컬럼명이 아님)
cm2_0.conversation_ids_deleted = 0

-- 올바른 컬럼명
cm2_0.is_deleted = 0
```

이 오타가 있는 경우 삭제된 메시지가 필터링되지 않아 최신 메시지 표시가 잘못될 수 있음.

---

## 3. 해결 방향

### ⚡ 빠른 해결 — 배치 쿼리 메서드로 교체

`getConversation()` 내부 단건 조회를 배치 조회로 교체:

```java
// 수정 전
List<ConversationParticipant> participants =
    participantRepository.findByConversationIdxAndStatus(conversationIdx, ParticipantStatus.ACTIVE);

// 수정 후
List<ConversationParticipant> participants =
    participantRepository.findParticipantsByConversationIdxsAndStatus(
        List.of(conversationIdx), ParticipantStatus.ACTIVE);
```

메서드 시그니처가 `List<Long>`을 받으므로 `List.of(conversationIdx)` 래핑으로 기존 배치 메서드를 재사용 가능.

### 🔧 근본 해결 — JOIN FETCH 또는 @EntityGraph 적용

참여자 정보를 채팅방과 함께 한 번에 조회:

```java
// ConversationRepository에 추가
@Query("SELECT c FROM Conversation c JOIN FETCH c.participants p WHERE c.idx = :idx AND p.status = :status")
Optional<Conversation> findByIdxWithActiveParticipants(@Param("idx") Long idx, @Param("status") ParticipantStatus status);
```

### SQL 오타 수정

`findLatestMessagesByConversationIdxs` 쿼리에서:
```sql
-- 수정 전
AND cm2_0.conversation_ids_deleted = 0
-- 수정 후
AND cm2_0.is_deleted = 0
```

---

## 4. 재발 방지

- **루프 안 단건 Repository 호출 금지**: `findByXxx(singleId)` 패턴은 N+1의 직접적인 원인
- **Converter에서 LAZY 컬렉션 접근 금지**: `entity.getCollection().size()` 형태는 호출 시점에 DB 쿼리를 유발한다. `participantCount` 같은 집계 값은 Service에서 이미 로드된 데이터로 직접 세팅할 것
- **배치 쿼리 우선 작성**: 컬렉션을 받는 `findXxxByIdxIn(List<Long> idxs)` 형태의 메서드를 먼저 정의
- **쿼리 로그 활성화**: `spring.jpa.show-sql=true` + `logging.level.org.hibernate.SQL=DEBUG` 설정으로 개발 중 N+1 조기 탐지
