# Step 5 — Medium/Low: DTO 정리 · catch 범위 · 경량 쿼리 · 죽은 코드 제거

## 목표
1. `MeetupDTO` — `isDeleted` / `deletedAt` 필드에 `@JsonIgnore` 적용
2. `MeetupService#cancelMeetupParticipation` — `catch (Exception e)` 범위를 `ApiException` + `Exception` 두 단계로 좁힘
3. `UsersRepository` + `MeetupService#isUserParticipating` — `findByIdString` 전체 엔티티 로딩 제거, `idx` 경량 쿼리 추가
4. `MeetupService#getAvailableMeetups()` 무인자 버전 — `@Deprecated` 마킹 후 javadoc 경고 추가
5. `SpringDataJpaMeetupRepository#findByIdWithLock` — 소프트 삭제 조건 추가 (미사용이지만 쿼리 일관성 유지)

---

## 배경 · 위험

| 이슈 | 위치 | 리스크 |
|---|---|---|
| 2-7 | `MeetupDTO:28-29` | `isDeleted`, `deletedAt` 응답 JSON에 노출 — 내부 운영 데이터 클라이언트에 전달 |
| 3-5 | `MeetupService:341` | `catch (Exception e)` 로 프로그래밍 오류(NPE 등)도 삼킴 |
| 2-8 | `MeetupService#isUserParticipating:353` | `findByIdString` → `Users` 전체 엔티티 로딩, `idx` 하나만 사용 |
| 2-10 | `MeetupService#getAvailableMeetups():378` | `Pageable.unpaged()` 무한 버전 잔존, 내부 재사용 위험 |
| 2-6 | `SpringDataJpaMeetupRepository#findByIdWithLock` | 소프트 삭제 조건 없음 — 삭제된 모임도 잠금 가능 |

---

## 변경 상세

### 1. `MeetupDTO` — `@JsonIgnore` 적용

**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/dto/MeetupDTO.java`

```java
import com.fasterxml.jackson.annotation.JsonIgnore;

// ...

@JsonIgnore
private Boolean isDeleted;

@JsonIgnore
private LocalDateTime deletedAt;
```

> `MeetupConverter`에서 이 필드를 세팅하는 코드는 유지해도 무방 — 직렬화 시 제외됨.

---

### 2. `MeetupService#cancelMeetupParticipation` — catch 범위 좁히기

**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupService.java` (`:341-347`)

현재:
```java
try {
    conversationService.leaveMeetupChat(meetupIdx, userIdx);
    log.info("채팅방에서 나가기 완료: meetupIdx={}, userIdx={}", meetupIdx, userIdx);
} catch (Exception e) {
    log.error("채팅방 나가기 실패: meetupIdx={}, userIdx={}, error={}", meetupIdx, userIdx, e.getMessage());
}
```

변경 후:
```java
import com.linkup.Petory.global.exception.ApiException;

// ...

try {
    conversationService.leaveMeetupChat(meetupIdx, userIdx);
    log.info("채팅방에서 나가기 완료: meetupIdx={}, userIdx={}", meetupIdx, userIdx);
} catch (ApiException e) {
    log.warn("채팅방 나가기 실패 (비즈니스): meetupIdx={}, userIdx={}, error={}", meetupIdx, userIdx, e.getMessage());
} catch (Exception e) {
    log.error("채팅방 나가기 예상치 못한 오류: meetupIdx={}, userIdx={}", meetupIdx, userIdx, e);
}
```

---

### 3. `UsersRepository` + `MeetupService#isUserParticipating` — 경량 쿼리

#### 3a. `SpringDataJpaUsersRepository` — `findIdxByIdString` 추가

**파일**: `backend/main/java/com/linkup/Petory/domain/user/repository/SpringDataJpaUsersRepository.java`

```java
@Query("SELECT u.idx FROM Users u WHERE u.id = :id")
Optional<Long> findIdxByIdString(@Param("id") String id);
```

#### 3b. `UsersRepository` 인터페이스 — 메서드 선언 추가

**파일**: `backend/main/java/com/linkup/Petory/domain/user/repository/UsersRepository.java`

```java
Optional<Long> findIdxByIdString(String id);
```

#### 3c. `JpaUsersAdapter` — 위임 구현 추가

**파일**: `backend/main/java/com/linkup/Petory/domain/user/repository/JpaUsersAdapter.java`

```java
@Override
public Optional<Long> findIdxByIdString(String id) {
    return jpaRepository.findIdxByIdString(id);
}
```

#### 3d. `MeetupService#isUserParticipating` — 경량 쿼리로 교체

**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupService.java` (`:353-359`)

현재:
```java
public boolean isUserParticipating(Long meetupIdx, String userId) {
    Users user = usersRepository.findByIdString(userId)
            .orElseThrow(UserNotFoundException::new);
    Long userIdx = user.getIdx();
    return meetupParticipantsRepository.existsByMeetupIdxAndUserIdx(meetupIdx, userIdx);
}
```

변경 후:
```java
public boolean isUserParticipating(Long meetupIdx, String userId) {
    Long userIdx = usersRepository.findIdxByIdString(userId)
            .orElseThrow(UserNotFoundException::new);
    return meetupParticipantsRepository.existsByMeetupIdxAndUserIdx(meetupIdx, userIdx);
}
```

`Users` 전체 엔티티 SELECT 없이 `idx` 스칼라 값만 SELECT.

---

### 4. `MeetupService#getAvailableMeetups()` 무인자 버전 — `@Deprecated` 마킹

**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupService.java` (`:377-381`)

```java
/**
 * @deprecated 컨트롤러는 {@link #getAvailableMeetups(Pageable)} 를 사용하세요.
 *             이 메서드는 Pageable.unpaged()로 전량 조회합니다.
 */
@Deprecated
@Timed("getAvailableMeetups")
public List<MeetupDTO> getAvailableMeetups() {
    List<Meetup> meetups = meetupRepository.findAvailableMeetups(
            LocalDateTime.now(), MeetupStatus.RECRUITING, Pageable.unpaged());
    return convertToDTOs(meetups);
}
```

> 삭제하지 않고 `@Deprecated`만 마킹 — 내부에서 실제로 호출하는 곳이 있으면 컴파일 경고로 식별 가능.

---

### 5. `SpringDataJpaMeetupRepository#findByIdWithLock` — 소프트 삭제 조건 추가

**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/repository/SpringDataJpaMeetupRepository.java`

현재:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT m FROM Meetup m WHERE m.idx = :idx")
Optional<Meetup> findByIdWithLock(@Param("idx") Long idx);
```

변경 후:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT m FROM Meetup m WHERE m.idx = :idx AND (m.isDeleted = false OR m.isDeleted IS NULL)")
Optional<Meetup> findByIdWithLock(@Param("idx") Long idx);
```

> 현재 `MeetupService` 내에서 직접 호출하지 않지만, 다른 도메인 또는 향후 코드에서 사용될 때 일관성 보장.

---

## Acceptance Criteria

```bash
# 컴파일 통과
./gradlew compileJava

# 수동 시나리오 (서버 기동 후)
# a. 모임 단건 조회 응답 JSON에 isDeleted, deletedAt 키 없는지 확인
# b. 참가 취소 API 호출 후 채팅방 나가기 실패 시 로그 레벨 확인 (warn vs error 분리)
# c. isUserParticipating 호출 시 SQL 로그에 Users 전체 SELECT 없고 idx 스칼라 SELECT만 나오는지 확인
```

---

## 주의 사항

- Step 3 완료 후 진행 (`findAvailableMeetups` 시그니처 변경이 선행됨).
- `findIdxByIdString`은 `Users` 엔티티의 `id` 필드(String, 로그인 아이디)로 조회 — `idx`(Long, PK)와 구분 주의.
- `ApiException` import가 `MeetupService`에 없으면 추가.
- `JpaUsersAdapter`에서 `findIdxByIdString`을 구현할 때 `SpringDataJpaUsersRepository` 인터페이스 메서드와 시그니처가 동일한지 확인.
