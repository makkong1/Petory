# MeetupDomainV2 페이지 드래프트 점검 및 작성

> 목적: 포트폴리오 repo의 `MeetupDomainV2.jsx` 현재 내용과 Petory 코드/문서를 비교해, 실제 페이지에 넣을 문구와 수정 포인트를 확정한다.  
> 작성 기준: 현재 구현을 1순위로 보고, `docs/domains/meetup.md`, 모임 아키텍처, meetup 리팩토링/트러블슈팅 문서는 근거와 맥락으로만 사용한다.

---

## 0. 판단

현재 첨부한 `MeetupDomainV2.jsx`는 큰 구조와 방향이 좋다. Meetup 페이지는 "모임 CRUD"보다 참가 정원 동시성, 채팅방 생성 분리, 위치 기반 조회 최적화, 히스토리 N+1 제거를 포트폴리오 포인트로 잡는 게 맞다.

그대로 진행해도 되는 부분:

- 비관적 락 + 조건부 원자적 update + PK 충돌 복구로 참가 동시성을 설명하는 구성
- 모임 생성과 채팅방 생성을 `afterCommit` 이벤트로 분리한 설명
- 근처 모임을 id 먼저 조회하고, organizer fetch join으로 다시 읽어 순서를 보존하는 2단계 조회 설명
- 참여 히스토리 N+1을 `JOIN FETCH`로 해결한 설명
- 참여 가능 목록을 `currentParticipants < maxParticipants` 직접 비교로 단순화한 설명
- 성능 수치 `486ms -> 273ms`, `241ms -> 143ms`, `1.48MB -> 0.21MB`, `102개 -> 2개`는 테스트 데이터 기준 비교값으로 사용 가능

보완하면 좋은 부분:

- 근처 모임 조회를 "Haversine + Bounding Box"라고 쓰면 현재 repository 구현과 조금 어긋난다. 현재 DB 필터는 `geo_point` 공간 컬럼, `ST_Within`, `ST_Distance_Sphere` 기반이고, Haversine 계산은 응답 DTO의 `distance` 미터 값을 채우는 서비스 보정용이다.
- 채팅방 생성은 단순 비동기 이벤트가 아니라 `afterCommit` 이벤트 발행, `@Async @EventListener`, 3회 retry, 5분 복구 스케줄러까지 묶인 결과적 일관성 설계로 쓰는 게 더 정확하다.
- `joinMeetup()`은 모임 참가만 처리한다. 채팅방 참가는 별도 Chat API인 `POST /api/chat/conversations/meetup/{meetupIdx}/join` 흐름으로 분리되어 있다.
- `nearby` API의 기본 `maxResults`는 500이고, 서비스에서 1~1000으로 보정한다.
- `nearby` 조회는 `COMPLETED`만 제외하고 `RECRUITING`만 요구하지 않는다. 지도 마커용으로 미래 모임을 보여주는 성격이면 괜찮지만, "참여 가능한 모임만"이라는 표현은 `available` API에만 써야 한다.

---

## 1. 페이지 상단

### H1

산책·모임 도메인

### 소개 문단

Meetup 도메인은 반려동물 산책과 오프라인 모임을 생성하고, 사용자가 참가·취소·히스토리 조회를 할 수 있게 하는 영역이다. 구현의 핵심은 모임 자체보다 정원과 후속 흐름의 정합성이었다. 동시에 여러 사용자가 참가해도 `currentParticipants`가 최대 정원을 넘지 않아야 하고, 모임 생성 후 그룹 채팅방은 생성되어야 하지만 채팅 실패가 모임 생성 트랜잭션을 롤백하면 안 된다. 현재 구조는 참가 시 비관적 락과 조건부 원자적 update를 함께 사용하고, 모임 생성 커밋 이후 이벤트로 채팅방 생성을 분리하며, 근처 모임 조회는 공간 조건으로 id를 먼저 제한한 뒤 organizer를 fetch join으로 다시 읽는다.

### 핵심 기능 태그 (`corePillars`)

첨부 JSX의 5개 태그는 그대로 사용 가능하다.

```javascript
const corePillars = [
  '참가 동시성 제어',
  '이벤트 기반 채팅방 분리',
  '근처 모임 2단계 조회',
  '히스토리 N+1 제거',
  '참여 가능 목록 단순화',
];
```

---

## 2. `section#intro` - 도메인 개요

### 2-1. 개요 카드 문구

Meetup은 생성 시 주최자를 자동 참가자로 등록하고 `currentParticipants = 1`, `status = RECRUITING` 상태로 시작한다. 생성 트랜잭션이 성공적으로 커밋된 뒤에만 `MeetupCreatedEvent`를 발행하고, 비동기 리스너가 그룹 채팅방을 생성한다. 채팅방 생성은 3회 재시도와 5분 복구 스케줄러로 보강되어 있지만, 모임 생성 트랜잭션과는 분리되어 있다.

참가 흐름은 `findByIdWithLock()`으로 모임을 비관적 락 조회한 뒤, 주최자가 아닌 참가자에 대해서만 `incrementParticipantsIfAvailable()` 조건부 update를 수행한다. 이 update는 `RECRUITING` 상태와 `currentParticipants < maxParticipants` 조건을 DB에서 동시에 검사한다. 이후 참가자 row 저장 중 복합 PK 충돌이 발생하면 이미 증가한 인원 수를 다시 감소시키고 중복 참가로 응답한다.

근처 모임 조회는 DB에서 공간 조건을 걸어 id만 먼저 가져온다. `ST_Within`으로 bounding polygon을 적용하고 `ST_Distance_Sphere`로 반경을 검증한 뒤, 거리와 날짜순으로 정렬해 `LIMIT`을 건다. 서비스는 이 id 목록으로 `findByIdxInWithOrganizer()`를 호출하고, 처음 id 순서를 유지한 상태로 DTO를 만든다.

### 2-2. 구조 테이블

| 항목 | 현재 코드 기준 |
|---|---|
| 사용자 API | `MeetupController`의 `/api/meetups` |
| 인증 정책 | 클래스 레벨 `@PreAuthorize("isAuthenticated()")` |
| 생성 | 주최자 자동 참가, 기본 정원 10명, `RECRUITING` 시작 |
| 이메일 인증 | 모임 생성·참가 모두 `MEETUP` 목적 이메일 인증 필요 |
| 참가 동시성 | `PESSIMISTIC_WRITE` + 조건부 원자적 update + 참가 PK 충돌 복구 |
| 참가 취소 | 참가자 row 삭제 후 `decrementParticipantsIfPositive()` 원자적 감소 |
| 채팅방 생성 | 커밋 이후 이벤트 발행, `@Async` 리스너, 3회 retry, 5분 복구 |
| 채팅방 참가 | Meetup 참가 API와 분리된 Chat API 흐름 |
| 근처 조회 | `ST_Within` + `ST_Distance_Sphere` id 조회 후 organizer fetch |
| near maxResults | 기본 500, 서비스에서 1~1000 보정 |
| 참여 가능 목록 | `date > now`, `status = RECRUITING`, `currentParticipants < maxParticipants` |
| 상태 전이 | 매시 정각 스케줄러가 `CLOSED`, `COMPLETED` bulk update |
| 홈 추천 | 좌표가 있으면 근처 후보를 거리·날짜·남은 정원 점수로 메모리 정렬 |

### 2-3. 성능 테이블

첨부 JSX의 수치는 사용 가능하다. 단, "1,000개 테스트 데이터 기준 리팩토링 비교값"이라는 조건을 같이 둔다.

| 지표 | Before | After |
|---|---|---|
| 근처 모임 전체 실행 시간 | 486ms | 273ms |
| 근처 모임 DB 쿼리 시간 | 241ms | 143ms |
| 근처 모임 메모리 사용량 | 1.48MB | 0.21MB |
| 참여 히스토리 PrepareStatement 수 | 102개 | 2개 |

보조 설명:

- 근처 모임 수치는 인메모리 필터링 -> DB 반경 필터링 -> Bounding Box/공간 조건 최적화로 비교한 문서 근거값이다.
- 참여 히스토리 수치는 `JOIN FETCH` 적용 전후 PrepareStatement 비교값이다.
- 운영 절대 성능 수치가 아니라, 병목 제거 전후의 상대 비교로 표현한다.

### 2-4. 데이터 흐름 카드

문구:

Meetup 흐름은 도메인 페이지에 모든 시퀀스를 반복하지 않고 통합 흐름 페이지로 분리한다. 모임 생성 후 채팅방 생성, 참가와 취소, 근처 조회, 상태 전이 흐름을 별도 탭에서 확인할 수 있게 한다.

내부 링크:

- `/domains/flows?tab=meetup`
- `/domains/flows?tab=meetup&seq=chat`
- `/domains/flows?tab=chat&seq=meetup`

---

## 3. `section#design` - 기술 결정

첨부 JSX의 A~E 구성은 적절하다. 아래 내용으로 문구와 코드 스니펫만 현재 코드 기준으로 다듬으면 된다.

### A. 참가 동시성 제어

핵심 문구:

모임 참가에서 가장 위험한 지점은 정원 초과와 중복 참가다. 단순히 현재 인원 수를 읽고 Java에서 비교한 뒤 저장하면 동시에 여러 요청이 들어왔을 때 TOCTOU 문제가 발생한다. 현재 구현은 모임 row를 비관적 락으로 조회하고, DB update에서 `RECRUITING` 상태와 정원 미달 조건을 한 번에 검사한다. 마지막으로 참가자 테이블의 복합 PK 충돌까지 처리해 중복 요청을 방어한다.

코드 스니펫 후보:

```java
Meetup meetup = meetupRepository.findByIdWithLock(meetupIdx)
        .orElseThrow(MeetupNotFoundException::new);

if (!meetup.getOrganizer().getIdx().equals(userIdx)) {
    int updated = meetupRepository.incrementParticipantsIfAvailable(
            meetupIdx,
            MeetupStatus.RECRUITING);

    if (updated == 0) {
        if (meetup.getStatus() != MeetupStatus.RECRUITING) {
            throw MeetupConflictException.meetupNotRecruiting();
        }
        throw MeetupConflictException.fullCapacity();
    }
    participantsIncremented = true;
    entityManager.refresh(meetup);
}
```

Repository:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT m FROM Meetup m JOIN FETCH m.organizer " +
       "WHERE m.idx = :idx AND (m.isDeleted = false OR m.isDeleted IS NULL)")
Optional<Meetup> findByIdWithLock(Long idx);

@Modifying
@Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1 " +
       "WHERE m.idx = :meetupIdx " +
       "AND m.currentParticipants < m.maxParticipants " +
       "AND m.status = :recruiting")
int incrementParticipantsIfAvailable(Long meetupIdx, MeetupStatus recruiting);
```

PK 충돌 복구:

```java
try {
    savedParticipant = meetupParticipantsRepository.save(participant);
} catch (DataIntegrityViolationException e) {
    if (participantsIncremented) {
        meetupRepository.decrementParticipantsIfPositive(meetupIdx);
    }
    throw MeetupConflictException.alreadyJoined();
}
```

근거:

- `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupService.java`
- `backend/main/java/com/linkup/Petory/domain/meetup/repository/SpringDataJpaMeetupRepository.java`
- `docs/troubleshooting/meetup/race-condition-participants.md`

### B. 이벤트 기반 채팅방 분리

핵심 문구:

모임 생성과 채팅방 생성은 사용자 경험상 연결되어 있지만 트랜잭션 책임은 다르다. 모임 저장이 성공했는데 채팅방 생성만 실패했다고 모임 생성까지 롤백하면 핵심 도메인 성공을 파생 기능 실패가 막게 된다. 현재 구현은 모임 생성 트랜잭션이 커밋된 뒤 `MeetupCreatedEvent`를 발행하고, 비동기 리스너가 채팅방을 생성한다. 실패는 3회 retry와 5분 복구 스케줄러로 보완한다.

코드 스니펫 후보:

```java
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        eventPublisher.publishEvent(new MeetupCreatedEvent(
                MeetupService.this,
                savedMeetup.getIdx(),
                organizer.getIdx(),
                savedMeetup.getTitle()));
    }
});
```

Listener:

```java
@EventListener
@Async
public void handleMeetupCreated(MeetupCreatedEvent event) {
    meetupChatRoomCreationService.createChatRoom(
            event.getMeetupIdx(),
            event.getOrganizerIdx(),
            event.getMeetupTitle());
}
```

생성 서비스:

```java
@Retryable(retryFor = Exception.class, maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2))
public void createChatRoom(Long meetupIdx, Long organizerIdx, String meetupTitle) {
    conversationCreatorService.createConversation(
            ConversationType.MEETUP,
            RelatedType.MEETUP,
            meetupIdx,
            meetupTitle,
            List.of(organizerIdx),
            organizerIdx);

    conversationService.setParticipantRole(
            RelatedType.MEETUP,
            meetupIdx,
            organizerIdx,
            ParticipantRole.ADMIN);
}
```

주의 문구:

- 모임 참가와 채팅방 참가는 같은 API가 아니다.
- `joinMeetup()`은 모임 참가자 row와 인원 수를 다룬다.
- 채팅방 참가는 Chat 도메인의 Meetup 채팅 참가 API에서 처리한다.
- 참가 취소 시에는 채팅방 나가기를 시도하지만, 채팅 실패가 참가 취소를 롤백하지 않게 예외를 로깅하고 계속 진행한다.

근거:

- `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupChatRoomEventListener.java`
- `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupChatRoomCreationService.java`
- `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupChatRoomRecoveryScheduler.java`
- `docs/architecture/meetup/산책 & 오프라인 모임 아키텍처.md`

### C. 근처 모임 2단계 조회

핵심 문구:

지도용 근처 모임은 위치 필터, 날짜 필터, 주최자 정보, 거리 정렬이 한 번에 필요하다. 처음부터 엔티티 전체를 가져오면 반경 밖 데이터와 organizer 로딩 비용이 커진다. 현재 구현은 DB에서 공간 조건으로 id만 먼저 제한하고, 두 번째 쿼리에서 organizer를 fetch join으로 가져온다. 이후 서비스에서 id 순서를 다시 맞춰 DB의 거리 정렬 결과를 보존한다.

코드 스니펫 후보:

```java
int limit = Math.min(Math.max(maxResults, 1), 1000);

List<Long> ids = meetupRepository.findNearbyMeetupIds(
        lat,
        lng,
        radiusKm,
        LocalDateTime.now(),
        limit);

List<Meetup> loaded = meetupRepository.findByIdxInWithOrganizer(ids);
Map<Long, Meetup> byId = loaded.stream()
        .collect(Collectors.toMap(Meetup::getIdx, m -> m));

return ids.stream()
        .map(byId::get)
        .filter(Objects::nonNull)
        .map(meetup -> {
            MeetupDTO dto = converter.toDTO(meetup);
            dto.setDistance(calculateDistanceMeters(
                    lat, lng, meetup.getLatitude(), meetup.getLongitude()));
            return dto;
        })
        .toList();
```

Repository 설명:

- `ST_Within(m.geo_point, polygon)`으로 bounding polygon을 먼저 적용한다.
- `ST_Distance_Sphere(m.geo_point, point) <= radius * 1000`으로 실제 반경을 검증한다.
- `ORDER BY ST_Distance_Sphere(...) ASC, m.date ASC`로 거리와 날짜 정렬을 DB에서 처리한다.
- 조회 조건은 미래 모임, 소프트 삭제 제외, 좌표 필수, `COMPLETED` 제외다.
- 응답 DTO의 `distance`는 서비스에서 미터 단위로 다시 계산해 세팅한다.

주의 문구:

- 포트폴리오 페이지에서는 "Haversine으로 DB 필터링"이라고 쓰지 않는다.
- "지도 마커용 근처 모임"과 "참여 가능한 모임"을 구분한다. 참여 가능 목록은 별도 `/available` API에서 `RECRUITING + 정원 미달` 조건을 사용한다.

근거:

- `backend/main/java/com/linkup/Petory/domain/meetup/repository/SpringDataJpaMeetupRepository.java`
- `docs/refactoring/meetup/nearby-meetups/performance-comparison.md`
- `docs/domains/meetup.md`

### D. 히스토리 N+1 제거

핵심 문구:

내 모임 히스토리는 참여 row, 모임, 주최자, 사용자 정보를 함께 보여준다. 이 관계를 DTO 변환 단계에서 하나씩 따라가면 참여 기록 수만큼 추가 select가 발생한다. 현재 구현은 참여자 repository에서 `meetup`, `meetup.organizer`, `user`를 모두 `JOIN FETCH`로 가져온다. 테스트 문서 기준 PrepareStatement 수는 102개에서 2개로 줄었다.

코드 스니펫 후보:

```java
@Query("SELECT mp FROM MeetupParticipants mp " +
       "JOIN FETCH mp.meetup m " +
       "JOIN FETCH m.organizer o " +
       "JOIN FETCH mp.user u " +
       "WHERE mp.user.idx = :userIdx " +
       "AND (m.isDeleted = false OR m.isDeleted IS NULL) " +
       "ORDER BY mp.joinedAt DESC")
List<MeetupParticipants> findByUserIdxOrderByJoinedAtDesc(Long userIdx);
```

근거:

- `backend/main/java/com/linkup/Petory/domain/meetup/repository/SpringDataJpaMeetupParticipantsRepository.java`
- `docs/refactoring/meetup/participants-query/performance-comparison-participants.md`
- `docs/troubleshooting/meetup/n-plus-one-query-issue.md`

### E. 참여 가능 목록 단순화

핵심 문구:

참여 가능 목록은 "현재 참여자 수가 최대 정원보다 작고, 모집 중이며, 날짜가 미래인 모임"이라는 조건으로 표현할 수 있다. 이전처럼 참여자 테이블을 집계하거나 Java에서 필터링하면 페이징이 흐려지고 쿼리 비용이 커진다. 현재 구현은 `Meetup.currentParticipants`를 기준으로 DB에서 직접 비교하고, `Pageable`의 LIMIT/OFFSET을 그대로 적용한다.

코드 스니펫 후보:

```java
@Query("SELECT m FROM Meetup m JOIN FETCH m.organizer " +
       "WHERE m.date > :currentDate " +
       "AND m.currentParticipants < m.maxParticipants " +
       "AND m.status = :recruiting " +
       "AND (m.isDeleted = false OR m.isDeleted IS NULL) " +
       "ORDER BY m.date ASC")
List<Meetup> findAvailableMeetups(
        LocalDateTime currentDate,
        MeetupStatus recruiting,
        Pageable pageable);
```

서비스 응답은 `Slice` 형태다. 전체 count가 꼭 필요하지 않은 목록이므로 `hasNext`, `page`, `size`, `count` 중심으로 응답한다.

---

## 4. `section#limits` - 한계와 운영 메모

첨부 JSX의 한계 섹션은 유지하되, 아래 항목을 추가하거나 문구를 더 정확히 하면 좋다.

- 정원 마감 상태 전이는 즉시가 아니라 매시 정각 스케줄러로 처리된다.
- 참가 취소로 자리가 비어도 `CLOSED -> RECRUITING` 자동 복구는 현재 없다.
- Meetup 사용자 API는 클래스 레벨 인증 필수다. 공개 지도용 API처럼 표현하면 안 된다.
- 모임 참가와 Meetup 채팅방 참가는 분리되어 있다. 모임 참가가 곧바로 채팅 참여를 의미하지 않는다.
- 채팅방 생성은 결과적 일관성이다. retry와 복구 스케줄러가 있지만, 생성 직후 아주 짧은 시간 동안 채팅방이 없을 수 있다.
- 근처 모임 조회는 `COMPLETED`만 제외한다. "참여 가능" 조건이 필요하면 `/available` 또는 홈 추천의 `RECRUITING` 필터와 구분해야 한다.
- 성능 수치는 테스트 데이터와 문서화된 비교 기준이다. 운영 환경의 절대 응답 시간으로 표현하지 않는다.
- 홈 추천은 DB 점수 정렬이 아니라 근처 후보를 가져온 뒤 서비스 메모리에서 거리·날짜·남은 정원 점수를 계산한다.
- 키워드/주최자 목록 등 일부 목록은 풀 페이징 전환 전 임시 상한 `MAX_LIST_SIZE = 500`을 둔다.

---

## 5. `section#docs` - 연결 문서와 소스

### 내부 페이지 링크

- `/domains/flows?tab=meetup`
- `/domains/flows?tab=meetup&seq=chat`
- `/domains/meetup/optimization`
- `/domains/meetup/refactoring`
- `/domains/chat`

### GitHub 소스 링크 후보

첨부 JSX의 `PETORY_MEETUP_SERVICE`, `PETORY_MEETUP_REPO` 링크는 유지한다. 추가하면 좋은 링크:

```javascript
const PETORY_MEETUP_CONTROLLER =
  'https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/meetup/controller/MeetupController.java';
const PETORY_MEETUP_PARTICIPANTS_REPO =
  'https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/meetup/repository/SpringDataJpaMeetupParticipantsRepository.java';
const PETORY_MEETUP_CHAT_CREATION =
  'https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupChatRoomCreationService.java';
const PETORY_MEETUP_CHAT_RECOVERY =
  'https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupChatRoomRecoveryScheduler.java';
const PETORY_MEETUP_SCHEDULER =
  'https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupScheduler.java';
```

### 문서 근거

- `docs/domains/meetup.md`
- `docs/architecture/meetup/산책 & 오프라인 모임 아키텍처.md`
- `docs/troubleshooting/meetup/race-condition-participants.md`
- `docs/troubleshooting/meetup/n-plus-one-query-issue.md`
- `docs/refactoring/meetup/nearby-meetups/performance-comparison.md`
- `docs/refactoring/meetup/participants-query/performance-comparison-participants.md`

---

## 6. JSX 반영 체크리스트

`MeetupDomainV2.jsx`를 고칠 때 우선순위:

1. `근처 모임 2단계 조회` 카드의 "Haversine + Bounding Box" 표현을 `ST_Within + ST_Distance_Sphere + 서비스 distance 계산`으로 수정한다.
2. 채팅방 분리 카드에 `@Retryable(maxAttempts = 3)`과 5분 복구 스케줄러를 추가한다.
3. `joinMeetup()`이 채팅방 참가까지 처리하는 것처럼 보이는 문구가 있으면 제거하고, Chat API와 분리된다고 명시한다.
4. `nearby`는 지도 마커용 미래 모임 조회, `/available`은 참여 가능한 모집 중 모임 조회로 구분한다.
5. 성능 표 아래에 "1,000개 테스트 데이터 기준 리팩토링 비교값"이라는 보조 문구를 유지한다.
6. 한계 섹션에 `CLOSED -> RECRUITING` 자동 복구 없음, 결과적 일관성, 인증 필수 API를 남긴다.
