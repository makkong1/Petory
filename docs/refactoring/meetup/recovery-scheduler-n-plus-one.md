# 복구 스케줄러 SELECT users 분석 및 리팩토링

## 📋 개요

**발견 일시**: 2026-05-12  
**대상**: `MeetupChatRoomRecoveryScheduler` → `ConversationCreatorService`  
**목적**: 로그 분석 결과 기록 + JPQL 전환의 실제 가치 정의

---

## 🔍 최초 가설 (오진)

복구 스케줄러 실행 시 아래 패턴이 반복됨을 확인:

```
INFO  : 모임 채팅방 생성 완료: meetupIdx=15619
DEBUG : Crud.findById
Hibernate: select u1_0.idx, ... from users where u1_0.idx=?
```

**최초 가설**: `MeetupChatRoomRecoveryScheduler` 루프에서
`meetup.getOrganizer().getIdx()` 호출 시 Lazy 프록시 초기화 → N+1 발생.

---

## 🔬 가설 검증 결과 (오진 확정)

`ConversationCreatorService.createConversation()`을 확인한 결과:

```java
// ConversationCreatorService.java:61-63
List<Users> participants = participantUserIds.stream()
        .map(userId -> usersRepository.findById(userId)   // ← 명시적 SELECT users
                .orElseThrow(UserNotFoundException::new))
        .collect(Collectors.toList());
```

`createConversation()`이 시작되자마자 **참여자 ID 목록으로 Users를 명시적으로 로드**함.

### 실제 로그 순서 재해석

처음에 "완료 로그 직후 SELECT users"로 보였던 이유는 로그가 중간부터 잘렸기 때문:

```
[잘린 부분: meetupIdx=15619의 createConversation() 첫 줄 SELECT users]
SELECT conversation (existing check for 15619)
INSERT conversation
INSERT conversationparticipant
SELECT conversation (setParticipantRole for 15619)
SELECT conversationparticipant
UPDATE conversationparticipant
INFO: 모임 채팅방 생성 완료: meetupIdx=15619

SELECT users              ← meetupIdx=15620의 createConversation() 첫 줄
SELECT conversation (existing check for 15620)
INSERT conversation
...
```

**결론**: SELECT users는 Lazy Load가 아니라 `ConversationCreatorService`의 **명시적 findById**.  
`meetup.getOrganizer().getIdx()`는 해당 SELECT를 유발하지 않음.

---

## 🧩 실제 구조 분석

### 모임 1건당 실제 쿼리 흐름

```
createChatRoom(meetupIdx, organizerIdx, title)
  └─ createConversation()   [REQUIRES_NEW]
       ├─ SELECT users WHERE idx = organizerIdx      ← 명시적 findById (1)
       ├─ SELECT conversation (existing check)       ← relatedType + relatedIdx (2)
       ├─ INSERT conversation                        (3)
       └─ INSERT conversationparticipant             (4)
  └─ setParticipantRole()   [REQUIRES_NEW]
       ├─ SELECT conversation                        (5)
       ├─ SELECT conversationparticipant             (6)
       └─ UPDATE conversationparticipant             (7)
```

모임 N건 기준 총 7N 쿼리. SELECT users(1)는 기술적 N+1이 아니라 **createConversation 로직상 필수 조회**.

### meetup.getOrganizer()는 실제로 Lazy Load를 트리거하나?

OSIV가 이미 꺼진 상태(`spring.jpa.open-in-view=false`)이고 스케줄러에 `@Transactional` 없음에도  
`LazyInitializationException`이 발생하지 않는 이유는 아직 불명확.  
추정: `@BatchSize(size=50)`이 선언된 Users 엔티티에 대해 Hibernate가 암묵적으로 세션을 열 수 있음.  
→ 동작은 하지만 **어떤 암묵적 메커니즘에 의존하고 있다는 사실 자체가 위험 신호**.

---

## ✅ JPQL JOIN FETCH 전환의 실제 가치

최초 가설(N+1 제거)은 틀렸으나, native SQL → JPQL 전환은 **다른 이유로 여전히 유효**:

| 기존 (native SQL) | 변경 후 (JPQL JOIN FETCH) |
|-------------------|--------------------------|
| organizer Lazy 프록시 상태로 반환 | organizer 즉시 로드 |
| OSIV·Hibernate 내부 메커니즘에 암묵적 의존 | 명시적, 환경 독립적 |
| `'MEETUP'` 문자열 리터럴 (JPQL 전환 시 타입 불일치 위험) | `RelatedType.MEETUP` enum 상수 |
| 쿼리 수 변화 | 없음 (organizer N+1이 없었으므로) |

**리팩토링 가치 재정의:**  
~~N+1 제거~~ → **코드 일관성 확보 + 암묵적 메커니즘 의존 제거**  
→ OSIV 설정 변경, Hibernate 버전 업그레이드 시 잠재 장애 1건 사전 제거

---

## 🛠️ 적용된 변경

```java
// SpringDataJpaMeetupRepository.java — Before (native SQL)
@Query(value = "SELECT m.* FROM meetup m " +
        "JOIN users u ON u.idx = m.organizer_idx " +
        "WHERE (m.is_deleted = false OR m.is_deleted IS NULL) " +
        "AND NOT EXISTS (SELECT 1 FROM conversation c " +
        "WHERE c.related_type = 'MEETUP' AND c.related_idx = m.idx AND c.is_deleted = false)",
        nativeQuery = true)
List<Meetup> findWithoutChatRoom();

// After (JPQL + JOIN FETCH + 풀 경로 enum 상수)
@Query("SELECT m FROM Meetup m JOIN FETCH m.organizer " +
       "WHERE (m.isDeleted = false OR m.isDeleted IS NULL) " +
       "AND NOT EXISTS (" +
       "  SELECT c FROM Conversation c " +
       "  WHERE c.relatedType = com.linkup.Petory.domain.chat.entity.RelatedType.MEETUP " +
       "  AND c.relatedIdx = m.idx AND c.isDeleted = false" +
       ")")
List<Meetup> findWithoutChatRoom();
```

---

## 📌 미해결 이슈 (별도 검토)

`ConversationCreatorService.createConversation()`의 `usersRepository.findById()`:  
- 현재는 참여자 유효성 검증을 위해 필수적으로 User 엔티티를 로드
- 복구 스케줄러처럼 신뢰된 내부 호출에서도 동일하게 적용됨
- 개선 방향 검토 가능: `existsById()`로 대체하거나 내부 전용 오버로드 분리
- **단, 이는 ConversationCreatorService 설계 이슈이므로 별도 리팩토링 대상**
