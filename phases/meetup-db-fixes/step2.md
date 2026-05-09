# Step 2 — @Table 인덱스 선언 추가 + findUpcomingMeetupsByUser JOIN FETCH 수정

## 배경

### @Table 인덱스 미선언
`Meetup`, `MeetupParticipants` 엔티티에 `@Table(indexes=...)` 선언이 없다.
DB에는 실측 인덱스가 이미 존재하므로 런타임 동작은 정상이나,
`ddl-auto=create` 또는 스키마 재생성 시 인덱스가 소실된다.
엔티티에 선언해두면 Hibernate가 DDL 생성 시 인덱스도 함께 생성한다.

**meetup 테이블 실존 인덱스 (SHOW INDEX 실측, db-concept-highlights-meetup.md 기준):**
- `organizer_idx (organizer_idx)`
- `idx_meetup_status (status)`
- `idx_meetup_date (date)`
- `idx_meetup_date_status (date, status)`
- `idx_meetup_location (latitude, longitude)`

**meetupparticipants 테이블 실존 인덱스:**
- `user_idx (user_idx)`
- `idx_meetupparticipants_user_liked_joined (user_idx, liked, joined_at)`

### findUpcomingMeetupsByUser JOIN FETCH 없음
```java
// 현재 — JOIN FETCH 없음
@Query("SELECT mp FROM MeetupParticipants mp " +
        "JOIN mp.meetup m " +
        "WHERE mp.user.idx = :userIdx AND m.date > CURRENT_TIMESTAMP " +
        "ORDER BY m.date ASC")
List<MeetupParticipants> findUpcomingMeetupsByUser(@Param("userIdx") Long userIdx);
```
현재 서비스에서 호출하지 않는 데드 메서드이지만,
결과에서 `mp.getMeetup()` 또는 `mp.getUser()` 접근 시 N+1 발생 구조다.
JOIN FETCH로 수정해서 미래 호출 시 안전하게 만든다.

## 수정 대상 파일

1. `backend/main/java/com/linkup/Petory/domain/meetup/entity/Meetup.java`
2. `backend/main/java/com/linkup/Petory/domain/meetup/entity/MeetupParticipants.java`
3. `backend/main/java/com/linkup/Petory/domain/meetup/repository/SpringDataJpaMeetupParticipantsRepository.java`

## 수정 내용

### 1. Meetup.java — @Table에 indexes 추가

```java
// Before
@Table(name = "meetup")

// After
@Table(name = "meetup", indexes = {
    @Index(name = "organizer_idx",             columnList = "organizer_idx"),
    @Index(name = "idx_meetup_status",          columnList = "status"),
    @Index(name = "idx_meetup_date",            columnList = "date"),
    @Index(name = "idx_meetup_date_status",     columnList = "date, status"),
    @Index(name = "idx_meetup_location",        columnList = "latitude, longitude")
})
```

Import: `jakarta.persistence.Index` — 이미 `jakarta.persistence.*`가 있으면 추가 불필요.

### 2. MeetupParticipants.java — @Table에 indexes 추가

```java
// Before
@Table(name = "meetupparticipants")

// After
@Table(name = "meetupparticipants", indexes = {
    @Index(name = "user_idx",                               columnList = "user_idx"),
    @Index(name = "idx_meetupparticipants_user_liked_joined", columnList = "user_idx, liked, joined_at")
})
```

### 3. SpringDataJpaMeetupParticipantsRepository.java — findUpcomingMeetupsByUser JOIN FETCH 추가

```java
// Before
@Query("SELECT mp FROM MeetupParticipants mp " +
        "JOIN mp.meetup m " +
        "WHERE mp.user.idx = :userIdx AND m.date > CURRENT_TIMESTAMP " +
        "ORDER BY m.date ASC")
List<MeetupParticipants> findUpcomingMeetupsByUser(@Param("userIdx") Long userIdx);

// After
@Query("SELECT mp FROM MeetupParticipants mp " +
        "JOIN FETCH mp.meetup m " +
        "JOIN FETCH m.organizer " +
        "JOIN FETCH mp.user " +
        "WHERE mp.user.idx = :userIdx AND m.date > CURRENT_TIMESTAMP " +
        "ORDER BY m.date ASC")
List<MeetupParticipants> findUpcomingMeetupsByUser(@Param("userIdx") Long userIdx);
```

## 검증

```bash
./gradlew compileJava
```

## AC (Acceptance Criteria)

- [ ] `Meetup.java` `@Table`에 5개 인덱스 선언
- [ ] `MeetupParticipants.java` `@Table`에 2개 인덱스 선언
- [ ] `findUpcomingMeetupsByUser` 쿼리가 `JOIN FETCH mp.meetup`, `JOIN FETCH m.organizer`, `JOIN FETCH mp.user` 포함
- [ ] `./gradlew compileJava` 성공
