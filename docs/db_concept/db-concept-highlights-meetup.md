# DB 개념 어필 포인트 — Meetup 도메인

> 코드베이스 실측 데이터 기준 (실제 파일 확인)
> 기준 파일: `MeetupService.java`, `SpringDataJpaMeetupRepository.java`, `Meetup.java`, `MeetupParticipants.java`, `MeetupScheduler.java`, troubleshooting/meetup/, refactoring/meetup/

---

## 1. 동시성 제어 — 원자적 UPDATE 쿼리로 Race Condition 해결

### 어필 포인트

- **문제**: 동시에 여러 사용자가 참가 버튼을 클릭하면 `currentParticipants` 체크와 증가 사이에 다른 트랜잭션이 끼어들어 Lost Update 발생 (최대 인원 3명 모임에 4명 참가 확인)
- **Before (문제 코드)**: `getCurrentParticipants() >= maxParticipants` 체크 → `setCurrentParticipants(+1)` → `save()` 순서는 원자적이지 않음
- **After (실제 구현)**: `@Modifying` + 조건부 UPDATE 쿼리 한 줄로 조건 체크와 증가를 DB 레벨에서 원자적으로 처리

```java
// SpringDataJpaMeetupRepository.java
@Modifying
@Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1 " +
       "WHERE m.idx = :meetupIdx " +
       "  AND m.currentParticipants < m.maxParticipants " +
       "  AND m.status = :recruiting")
int incrementParticipantsIfAvailable(
    @Param("meetupIdx") Long meetupIdx,
    @Param("recruiting") MeetupStatus recruiting);
```

- `updated == 0`이면 status 필드로 역추론 → `meetupNotRecruiting` 또는 `fullCapacity` 분기
- 감소 시도 마찬가지: `currentParticipants > 0` 조건을 걸어 음수 방지

```java
@Modifying
@Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants - 1 " +
       "WHERE m.idx = :meetupIdx AND m.currentParticipants > 0")
int decrementParticipantsIfPositive(@Param("meetupIdx") Long meetupIdx);
```

- **검증**: `MeetupServiceRaceConditionTest.java`로 3명 동시 참가 시도 → 2명 성공, 1명 실패 확인
- **비교 검토한 전략**: 세마포어(분산 환경 미지원), 비관적 락(Deadlock 위험, 성능 저하), 낙관적 락(재시도 로직 필요), Redis 분산 락(인프라 의존성) → 원자적 UPDATE가 분산 환경·성능·단순성 균형에서 최선

### 말할 내용
> "모임 참가 시 동시에 여러 사용자가 클릭하면 최대 인원을 초과하는 Lost Update 문제가 발생했습니다. 비관적 락은 Deadlock 위험과 성능 저하가 있고, 낙관적 락은 재시도 로직이 복잡합니다. 저는 RECRUITING 상태 확인과 인원 증가를 WHERE 절에 동시에 담은 원자적 UPDATE 쿼리를 사용했습니다. DB 레벨에서 조건 체크와 증가가 한 문장으로 처리되기 때문에 다른 트랜잭션이 끼어들 여지가 없습니다. 반환값이 0이면 상태 필드를 보고 '모집 마감'인지 '정원 초과'인지 분기했습니다."

---

## 2. N+1 문제 해결 — JOIN FETCH + EntityGraph + BatchSize

### 어필 포인트

**목록 조회 N+1 (실제 발생)**
- `findAllNotDeleted()`가 JOIN FETCH 없이 organizer를 LAZY 로딩 → 74개 모임 조회 시 organizer 74번 추가 쿼리 → 총 75번 DB 왕복
- 해결: `JOIN FETCH m.organizer` 추가 → 쿼리 75개 → 1개 (98.7% 감소), 실행 시간 200ms → 68ms (66% 개선)

```java
// SpringDataJpaMeetupRepository.java — 실제 적용된 쿼리들
@Query("SELECT m FROM Meetup m JOIN FETCH m.organizer WHERE m.isDeleted = false OR m.isDeleted IS NULL")
List<Meetup> findAllNotDeleted();

@Query("SELECT m FROM Meetup m JOIN FETCH m.organizer WHERE m.idx = :idx AND ...")
Optional<Meetup> findByIdWithOrganizer(@Param("idx") Long idx);
```

**상세 조회 N+1 (예방적 해결)**
- 참가자 + 각 참가자의 user까지 LAZY → 이론적으로 1+N+N 쿼리
- 해결: `DISTINCT + LEFT JOIN FETCH` 한 방에 처리

```java
@Query("SELECT DISTINCT m FROM Meetup m " +
       "LEFT JOIN FETCH m.organizer " +
       "LEFT JOIN FETCH m.participants p " +
       "LEFT JOIN FETCH p.user " +
       "WHERE m.idx = :idx AND (m.isDeleted = false OR m.isDeleted IS NULL)")
Optional<Meetup> findByIdWithDetails(@Param("idx") Long idx);
```

**페이징 시 EntityGraph 사용**
- `JOIN FETCH + Pageable` 조합은 Hibernate가 count 쿼리를 메모리에서 처리하는 문제 → `@EntityGraph`로 대체

```java
@EntityGraph(attributePaths = {"organizer"})
@Query("SELECT m FROM Meetup m WHERE (m.isDeleted = false OR m.isDeleted IS NULL)")
Page<Meetup> findAllNotDeleted(Pageable pageable);
```

**BatchSize로 컬렉션 N+1 추가 방어**
```java
// Meetup.java 엔티티
@OneToMany(mappedBy = "meetup", cascade = CascadeType.ALL)
@BatchSize(size = 50)
private List<MeetupParticipants> participants;
```

### 말할 내용
> "모임 목록 조회에서 N+1 문제를 실제 로그로 확인했습니다. 74개 모임 조회 후 organizer를 개별 쿼리로 74번 조회해서 총 75번의 DB 왕복이 발생했습니다. JOIN FETCH를 추가해서 1번으로 줄였고 실행 시간이 200ms에서 68ms로 줄었습니다. 페이징 API에서는 JOIN FETCH와 Pageable 조합이 Hibernate에서 문제를 일으킬 수 있어서 EntityGraph를 사용했습니다. 참가자 컬렉션에는 @BatchSize(size=50)를 추가해서 IN 쿼리로 묶어 처리했습니다."

---

## 3. 인덱스 설계 — 조회 패턴 기반 복합 인덱스

### 어필 포인트

**meetup 테이블 인덱스** (SHOW INDEX 실측 결과):

| 인덱스명 | 컬럼 | 타입 | 용도 |
|---|---|---|---|
| PRIMARY | (idx) | BTREE | 기본키 |
| organizer_idx | (organizer_idx) | BTREE | 주최자 기준 조회 |
| idx_meetup_status | (status) | BTREE | 상태 필터링 |
| idx_meetup_date | (date) | BTREE | 날짜 범위 조회 |
| idx_meetup_date_status | (date, status) | BTREE | 날짜+상태 복합 필터 |
| idx_meetup_location | (latitude, longitude) | BTREE | 위치 기반 Bounding Box 거리 검색 |

```sql
CREATE INDEX idx_meetup_date ON meetup(date);
CREATE INDEX idx_meetup_date_status ON meetup(date, status);
CREATE INDEX idx_meetup_location ON meetup(latitude, longitude);
CREATE INDEX idx_meetup_status ON meetup(status);
CREATE INDEX organizer_idx ON meetup(organizer_idx);
```

**meetupparticipants 테이블** (SHOW INDEX 실측 결과):

| 인덱스명 | 컬럼 | 타입 | 비고 |
|---|---|---|---|
| PRIMARY | (meetup_idx, user_idx) | BTREE | 복합 PK — @IdClass 패턴, 중복 참여 DB 레벨 차단 |
| user_idx | (user_idx) | BTREE | 사용자별 참가 모임 역방향 조회 |
| idx_meetupparticipants_user_liked_joined | (user_idx, liked, joined_at) | BTREE | 특정 사용자의 좋아요 여부·참가일 복합 필터 |

`PRIMARY KEY (meetup_idx, user_idx)`는 `@IdClass(MeetupParticipantsId.class)`로 선언한 복합 기본키와 직접 매핑된다. DB PK 제약이 중복 참여 최종 방어선이며, 서비스 레이어에서 `existsByMeetupIdxAndUserIdx`로 선검사를 추가해 이중 방어한다.

`idx_meetupparticipants_user_liked_joined (user_idx, liked, joined_at)` 복합 인덱스는 "특정 사용자가 좋아요한 모임을 참가일 순으로 조회"하는 패턴에 최적화된 커버링 인덱스 후보다 — `WHERE user_idx = ? AND liked = ?` 조건에서 두 선행 컬럼이 고정값이고 `joined_at`이 정렬/범위 필터로 동작한다.

**위치 기반 검색 Bounding Box 최적화** — `idx_meetup_location (latitude, longitude)` 활용, 3단계 리팩토링 실측:
- 1단계 (인메모리 필터링): `idx_meetup_location` 미사용, 스캔 행 2958개
- 2단계 (DB 쿼리, `IS NOT NULL` 조건): 인덱스 여전히 미사용, 스캔 행 동일
- 3단계 (BETWEEN 조건으로 변경): 인덱스 활용, 스캔 행 117개 (96% 감소)

```sql
-- IS NOT NULL → BETWEEN으로 바꿔야 idx_meetup_location 인덱스가 동작
AND m.latitude  BETWEEN (:lat - :radius / 111.0) AND (:lat + :radius / 111.0)
AND m.longitude BETWEEN (:lng - :radius / (111.0 * cos(radians(:lat))))
                    AND (:lng + :radius / (111.0 * cos(radians(:lat))))
```

- 최종 성과: 전체 실행 시간 43.8% 감소 (486ms→273ms), DB 쿼리 40.7% 감소, 메모리 85.8% 감소

### 말할 내용
> "위치 기반 검색을 처음에는 전체 모임을 메모리에 올려서 Java로 거리 계산을 했습니다. DB 쿼리로 옮겼는데도 IS NOT NULL 조건 때문에 `idx_meetup_location (latitude, longitude)` 복합 인덱스가 사용되지 않아 2958행을 전부 스캔했습니다. BETWEEN 조건으로 Bounding Box를 구성하면 두 컬럼이 range 스캔으로 동작해서 117행으로 줄었습니다. 인덱스가 사용되는지 여부는 조건 작성 방식에 따라 완전히 달라진다는 걸 직접 확인했습니다. 참가자 테이블은 `(meetup_idx, user_idx)` 복합 PK와 `(user_idx, liked, joined_at)` 복합 인덱스를 별도로 두어 역방향 조회와 좋아요 필터 패턴도 인덱스로 처리합니다."

---

## 4. 트랜잭션 설계 — 핵심 도메인과 파생 도메인 분리

### 어필 포인트

**문제**: 모임 생성 시 채팅방 생성이 같은 트랜잭션에 있으면, 채팅방 생성 실패 시 모임 생성까지 롤백됨

**실제 구현**: `TransactionSynchronization.afterCommit()` + `@Async` + `@Transactional(REQUIRES_NEW)` 조합

```java
// MeetupService.createMeetup() — 트랜잭션 커밋 후 이벤트 발행 보장
TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
    @Override
    public void afterCommit() {
        eventPublisher.publishEvent(new MeetupCreatedEvent(
            MeetupService.this, savedMeetup.getIdx(), organizer.getIdx(), savedMeetup.getTitle()));
    }
});
```

```java
// MeetupChatRoomEventListener.java — 별도 트랜잭션에서 채팅방 생성
@EventListener
@Async
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void handleMeetupCreated(MeetupCreatedEvent event) {
    try {
        conversationService.createConversation(...);
        conversationService.setParticipantRole(..., ParticipantRole.ADMIN);
    } catch (Exception e) {
        log.error("채팅방 생성 실패 — 모임은 이미 커밋됨", e);
    }
}
```

**트랜잭션 전략**:
- 읽기 메서드: 클래스 레벨 `@Transactional(readOnly = true)`
- 쓰기 메서드: 메서드 레벨 `@Transactional` 오버라이드
- `@Modifying` 쿼리는 Service의 트랜잭션 전파를 사용 (Repository에 별도 선언 없음)

**참가 취소 시 채팅방 나가기 에러 격리**:
```java
// MeetupService.cancelMeetupParticipation()
try {
    conversationService.leaveMeetupChat(meetupIdx, userIdx);
} catch (ApiException e) {
    log.warn("채팅방 나가기 실패 (비즈니스): ...", e.getMessage());
} catch (Exception e) {
    log.error("채팅방 나가기 예상치 못한 오류", e);
}
// 어느 경우든 모임 취소는 성공으로 처리
```

### 말할 내용
> "모임 생성과 채팅방 생성을 같은 트랜잭션에 두면 채팅방 생성 실패 시 모임까지 롤백되는 문제가 있었습니다. 이벤트 기반으로 분리해서, 모임 트랜잭션이 커밋된 후에 afterCommit 콜백에서 이벤트를 발행하고, 리스너가 @Async + REQUIRES_NEW 트랜잭션에서 채팅방을 생성합니다. 채팅방 생성이 실패해도 모임은 이미 커밋된 상태입니다. 참가 취소 시 채팅방 나가기도 try-catch로 격리해서 채팅 실패가 비즈니스 핵심 로직을 막지 않도록 했습니다."

---

## 5. 영속성 컨텍스트 동기화 — entityManager.refresh()

### 어필 포인트

- **문제**: `@Modifying` UPDATE 쿼리는 DB를 직접 수정하지만 JPA 영속성 컨텍스트는 업데이트하지 않음 → 이후 코드에서 엔티티의 `currentParticipants`가 이전 값
- **Before**: 원자적 UPDATE 후 `findById()` 재호출 → 불필요한 DB 쿼리 1회 추가
- **After**: `entityManager.refresh(meetup)` → 영속성 컨텍스트를 DB 상태로 동기화, 코드 의도 명확

```java
// MeetupService.joinMeetup() — 실제 코드
int updated = meetupRepository.incrementParticipantsIfAvailable(meetupIdx, MeetupStatus.RECRUITING);
if (updated == 0) { ... }
// [리팩토링] findById 2회 호출 제거 → entityManager.refresh()로 영속성 컨텍스트 동기화
entityManager.refresh(meetup);
```

### 말할 내용
> "@Modifying 쿼리는 DB만 수정하고 영속성 컨텍스트는 그대로입니다. 원래 코드는 업데이트 후 findById를 다시 호출했는데, 이건 같은 데이터를 두 번 조회하는 비효율입니다. entityManager.refresh()로 영속성 컨텍스트만 DB 상태로 동기화해서 중복 쿼리를 제거했습니다."

---

## 6. 스케줄러 기반 상태 자동 전이 — Batch UPDATE

### 어필 포인트

- **MeetupStatus**: `RECRUITING` → `CLOSED` → `COMPLETED` 3단계 전이
- **MeetupScheduler**: 매시 정각(`cron = "0 0 * * * ?"`) 실행, 개별 행을 반복 처리하지 않고 Batch UPDATE 2회로 처리

```java
// MeetupScheduler.java — 실제 코드
@Scheduled(cron = "0 0 * * * ?")
@Transactional
public void transitionMeetupStatuses() {
    LocalDateTime now = LocalDateTime.now();
    int closed = meetupRepository.closeFullRecruitingMeetups(now);
    int completed = meetupRepository.completePastMeetups(now);
    if (closed > 0 || completed > 0) {
        log.info("모임 상태 자동 전이: CLOSED={}, COMPLETED={}", closed, completed);
    }
}
```

```java
// 정원 마감 → CLOSED
@Modifying(clearAutomatically = true)
@Query("UPDATE Meetup m SET m.status = :closed WHERE m.status = :recruiting " +
       "AND m.currentParticipants >= m.maxParticipants AND m.date >= :now " +
       "AND (m.isDeleted = false OR m.isDeleted IS NULL)")
int closeFullRecruitingMeetups(...);

// 일시 경과 → COMPLETED
@Modifying(clearAutomatically = true)
@Query("UPDATE Meetup m SET m.status = :completed WHERE m.date < :now " +
       "AND m.status <> :completed AND (m.isDeleted = false OR m.isDeleted IS NULL)")
int completePastMeetups(...);
```

- `clearAutomatically = true`: 배치 UPDATE 후 영속성 컨텍스트 자동 초기화

### 말할 내용
> "모임 상태 전이를 Application에서 매 요청마다 처리하면 N개의 row를 N번 SELECT+UPDATE하는 비효율이 생깁니다. 스케줄러가 매시 정각에 조건에 맞는 행을 WHERE 절로 걸러 한 번의 UPDATE로 일괄 처리합니다. 또 @Modifying에 clearAutomatically=true를 주어 배치 UPDATE 후 영속성 컨텍스트가 자동으로 비워지도록 했습니다."

---

## 7. 복합 기본키와 중복 참여 방지 — @IdClass

### 어필 포인트

- `MeetupParticipants`는 `(meetup_idx, user_idx)` 복합 PK → `@IdClass(MeetupParticipantsId.class)`
- DB PK 제약이 중복 참여 최종 방어선, 서비스에서 `existsByMeetupIdxAndUserIdx`로 선검사

```java
// MeetupParticipants.java
@Entity
@Table(name = "meetupparticipants")
@IdClass(MeetupParticipantsId.class)
public class MeetupParticipants {
    @Id
    @ManyToOne
    @JoinColumn(name = "meetup_idx", nullable = false)
    private Meetup meetup;

    @Id
    @ManyToOne
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user;

    private LocalDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        this.joinedAt = LocalDateTime.now();
    }
}
```

- 소프트 삭제: `Meetup`에 `isDeleted`, `deletedAt` 필드 — 모든 조회 쿼리에 `isDeleted = false OR isDeleted IS NULL` 조건 명시

### 말할 내용
> "모임 참가자 테이블은 (모임 ID, 사용자 ID) 복합 기본키를 @IdClass로 선언해서 DB 레벨에서 중복 참여를 막습니다. 서비스에서도 existsByMeetupIdxAndUserIdx로 사전 체크를 하지만 복합 PK가 최종 안전망입니다. 모임 삭제는 Soft Delete 방식이라 isDeleted 컬럼으로 관리하고, 모든 조회 쿼리에 해당 조건을 명시적으로 포함했습니다."

---

## 핵심 키워드

- **원자적 UPDATE 쿼리 (Lost Update 해결)**
- **@Modifying + 조건부 WHERE절 동시성 제어**
- **N+1 문제 → JOIN FETCH / EntityGraph / @BatchSize 조합 해결**
- **Bounding Box + 복합 인덱스로 위치 검색 인덱스 활용**
- **TransactionSynchronization.afterCommit() + @Async + REQUIRES_NEW 트랜잭션 분리**
- **entityManager.refresh()로 영속성 컨텍스트 동기화**
- **Batch UPDATE 스케줄러로 상태 자동 전이**
- **@IdClass 복합 PK 중복 방지**
- **Soft Delete**

---

## 관련 문서

- `docs/troubleshooting/meetup/race-condition-participants.md` — Race Condition 발생 원인, 4가지 전략 비교, 선택 근거
- `docs/troubleshooting/meetup/n-plus-one-query-issue.md` — N+1 실제 로그, Before/After 쿼리 수 비교
- `docs/refactoring/meetup/nearby-meetups/performance-comparison.md` — 3단계 리팩토링 실측 수치 (486ms→273ms, 스캔 행 2958→117)
- `docs/refactoring/meetup/duplicate-query-removal.md` — entityManager.refresh() 도입 배경
- `docs/refactoring/meetup/transaction-annotation-guide.md` — @Transactional 위치 가이드
- `docs/domains/meetup.md` — 전체 도메인 상세 (서비스 메서드 목록, API 설계, 인덱스 SQL)

---

## 면접 대답 구성

### 질문: "DB 관련 경험이 있나요?"

**대답 구조 (3분)**:

1. **동시성 제어** (1분)
   - "모임 참가 시 동시 요청으로 최대 인원을 초과하는 문제가 발생했습니다."
   - "비관적 락, 낙관적 락, Redis 분산 락을 검토했고, 원자적 UPDATE 쿼리로 해결했습니다. WHERE 절에 `currentParticipants < maxParticipants AND status = RECRUITING` 조건을 함께 넣어 DB 레벨에서 체크와 증가를 한 문장으로 처리합니다. 반환값이 0이면 상태 필드로 원인을 역추론해서 분기했습니다."

2. **N+1 문제** (45초)
   - "74개 모임 조회 시 organizer를 각각 조회해 총 75번 DB 왕복이 발생했습니다."
   - "JOIN FETCH로 1번으로 줄였고, 페이징 API에서는 EntityGraph를 사용했습니다. 참가자 컬렉션에는 @BatchSize를 추가했습니다."

3. **인덱스 + 위치 검색** (45초)
   - "IS NOT NULL 조건이 복합 인덱스를 타지 못해서 2958행 전부를 스캔했습니다."
   - "BETWEEN 조건으로 Bounding Box를 구성하면 인덱스가 range 스캔으로 동작해서 117행으로 줄었습니다."

4. **트랜잭션 분리** (30초)
   - "채팅방 생성을 이벤트 기반 비동기로 분리해서, 채팅방 생성 실패가 모임 생성 롤백으로 이어지지 않도록 했습니다."
