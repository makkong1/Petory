---
date: 2026-07-12
domains: [chat]
type: performance-evidence
problem: n-plus-one
status: verified
metric: "21→4 queries (-80.95%), 130ms→44ms (-66.15%), 438KB→152KB (-65.22%)"
related: [docs/troubleshooting/users/login-n-plus-one-issue.md, docs/troubleshooting/chat/n-plus-one-conversationparticipant.md]
---

# Chat 채팅방 목록 N+1 재검증 — 통합테스트 + EXPLAIN (2026-07-12)

> 목적: `troubleshooting/users/login-n-plus-one-issue.md`(21→4쿼리, 305ms→55ms)를 현재 코드로 다시 실행해 재현성을 확인한다. 함께 `troubleshooting/chat/n-plus-one-conversationparticipant.md`가 "해결 방향만 제시"라고 남긴 케이스A(`getConversation()`)가 실제로 N+1인지도 코드로 재확인했다.

## 0. 방법론

- 재현 대상: 기존 테스트 `backend/test/.../chat/service/ConversationServicePerformanceTest.java` (신규 작성 아님, 그대로 재실행)
- Fixture: 채팅방 10개 · 참여자 3명/채팅방 · 메시지 20개/채팅방
- Before: `getMyConversationsBefore()` — 채팅방마다 `findByConversationIdxAndUserIdx`/`findByConversationIdxAndStatus` 개별 호출 + 전체 메시지 로드
- After: `getMyConversationsAfter()` — `findParticipantsByConversationIdxsAndUserIdx`/`findParticipantsByConversationIdxsAndStatus`/`findLatestMessagesByConversationIdxs` 배치(IN절) 호출. 실제 `ConversationService.getMyConversations()`와 동일한 배치 패턴.
- 환경: 로컬 MySQL 8(`petory`), `@Transactional` 롤백으로 실데이터(conversationparticipant 10,397건) 비영향

## 1. 통합테스트 재실행 결과

| 항목 | Before | After | 원 문서(참고) |
|---|---|---|---|
| 쿼리 수 | 21개 | 4개 (**-80.95%**) | 21→4 (동일) |
| 실행 시간 | 130ms | 44ms (**-66.15%**) | 305→55ms (-81.97%) |
| 메모리 | 438,152B | 152,400B (**-65.22%**) | 607,968→138,384B (-77.24%) |

쿼리 수 감소율은 원 문서와 **정확히 일치**(21→4, 80.95%)한다. 절대 시간·메모리는 실행마다 달라지는 값이라 다르지만 개선 방향과 크기는 같은 대역이다.

## 2. 케이스A(`getConversation()`) 재확인 — 이건 N+1이 아니다

`n-plus-one-conversationparticipant.md`는 `getConversation()`(단건 채팅방 조회 API)을 "채팅방 수만큼 쿼리가 반복되는 케이스A"로 분류하고 해결을 보류해뒀다. 현재 코드(`ConversationService.java` L150-178)를 읽어보면:

```java
public ConversationDTO getConversation(Long conversationIdx, Long userId) {
    Conversation conversation = conversationRepository.findById(conversationIdx)...
    ConversationParticipant participant = participantRepository
            .findByConversationIdxAndUserIdx(conversationIdx, userId)...
    List<ConversationParticipant> participants = participantRepository
            .findByConversationIdxAndStatus(conversationIdx, ParticipantStatus.ACTIVE);
    ...
}
```

이 메서드는 `GET /api/conversations/{idx}` **단건 조회 API**다. 한 요청 안에서 같은 `conversationIdx`에 대해 참여자 쿼리 2번(본인 참여자 확인 1번 + 활성 참여자 목록 1번)을 쓸 뿐, 레코드 수(채팅방 개수)에 비례해 쿼리가 늘어나는 구조가 아니다. 원 문서의 "채팅방 수만큼 반복"이라는 설명은 이 API를 여러 채팅방에 대해 반복 호출하는 상위 시나리오를 가리킨 것으로 보이는데, 그건 단건 조회 API의 정상적인 특성이지 N+1 안티패턴이 아니다.

**결론**: 케이스A는 재검증 결과 실제 N+1이 아님을 확인했다. 배치 메서드(`findParticipantsByConversationIdxsAndStatus`)로 바꿀 이유가 없다 — 단건 조회에 배치 메서드(`List.of(conversationIdx)` 래핑)를 쓰면 오히려 IN절 오버헤드만 추가된다.

## 3. EXPLAIN — 개별조회 vs 배치조회

### 인덱스 상황

```
conversationparticipant:
  PRIMARY(idx)
  UNIQUE(conversation_idx, user_idx)      ← 개별조회가 타는 인덱스
  INDEX(user_idx, status, unread_count)   ← 배치조회가 타는 인덱스
  INDEX(conversation_idx, status)
```
Board/Care와 달리 필요한 인덱스가 이미 다 갖춰져 있다.

### Before — 개별조회 (`findByConversationIdxAndUserIdx`, 10번 반복됨)

```sql
EXPLAIN ANALYZE SELECT * FROM conversationparticipant WHERE conversation_idx=1 AND user_idx=16;
```

```
-> Rows fetched before execution  (cost=0..0 rows=1) (actual time=249e-6..291e-6 rows=1 loops=1)
```

`(conversation_idx, user_idx)` UNIQUE 인덱스라 옵티마이저가 **상수 조회(const lookup)로 실행 전에 답을 확정**한다. `actual time` 0.0003ms — 극도로 빠르다.

### After — 배치조회 (`findParticipantsByConversationIdxsAndUserIdx`, IN절 1회)

```sql
EXPLAIN ANALYZE SELECT * FROM conversationparticipant WHERE conversation_idx IN (1,137) AND user_idx=16;
```

```
-> Filter: (conversation_idx in (1,137))  (cost=0.505 rows=0.05) (actual time=0.021..0.0235 rows=2 loops=1)
    -> Index lookup on idx_participant_user_status (user_idx = 16)  (cost=0.505 rows=2) (actual time=0.0197..0.0218 rows=2 loops=1)
```

`actual time` 0.02ms — 개별조회 1건보다 오히려 약간 느리다(더 넓은 인덱스를 스캔하고 필터링하기 때문). **이 케이스가 Board/Care와 다른 지점이다**: 인덱스가 이미 완벽해서 배치조회가 "쿼리 자체를 더 빠르게" 만들지 않는다.

### 해석 — 여기서 이득은 DB 실행시간이 아니라 순수하게 왕복 횟수

| | 실행 횟수 | 1회당 DB 실행시간 | 왕복(라운드트립) |
|---|---|---|---|
| Before(개별) | 채팅방 수만큼(10회) | 0.0003ms | 10회 |
| After(배치) | 1회 | 0.02ms | 1회 |

Board/Care는 "인덱스 없음/전체스캔"이 겹쳐서 개선 효과가 컸다면, Chat은 **인덱스가 완벽한데도 왕복 횟수만으로 21→4쿼리, 130→44ms(66%) 개선**된다. 이건 N+1의 순수한 형태를 보여주는 케이스다 — DB가 아무리 빨라도 같은 트랜잭션 안에서 쿼리를 N번 왕복하면 그 자체가 비용이라는 것.

## 4. 재현 방법

```bash
./gradlew test --tests "com.linkup.Petory.domain.chat.service.ConversationServicePerformanceTest" --rerun --info

# EXPLAIN
mysql -uroot -p petory <<'SQL'
SELECT user_idx, conversation_idx INTO @uid, @one FROM conversationparticipant LIMIT 1;
EXPLAIN ANALYZE SELECT * FROM conversationparticipant WHERE conversation_idx=@one AND user_idx=@uid;
-- 배치는 동일 user_idx의 conversation_idx 여러 개를 IN절로 묶어 실행
SQL
```

## 5. 관련 문서

- 원본(로그인 N+1, 21→4쿼리 최초 측정): [`troubleshooting/users/login-n-plus-one-issue.md`](../../../troubleshooting/users/login-n-plus-one-issue.md)
- 케이스A/B 분석(케이스B만 해결 완료로 표시돼 있던 문서): [`troubleshooting/chat/n-plus-one-conversationparticipant.md`](../../../troubleshooting/chat/n-plus-one-conversationparticipant.md)
