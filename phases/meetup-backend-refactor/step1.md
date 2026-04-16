# Step 1 — Critical: joinMeetup 상태 검증 + handleException null 반환 수정

## 목표
1. `handleException`에서 `return null` 제거 → `AsyncRequestTimeoutException` 재throw
2. `incrementParticipantsIfAvailable` 쿼리에 `AND m.status = 'RECRUITING'` 조건 추가
3. `MeetupConflictException.meetupNotRecruiting()` 팩토리 메서드 추가
4. `MeetupService#joinMeetup`에서 `updated == 0` 분기를 "가득참 vs 모집 마감" 두 가지로 구분

---

## 배경 · 위험

| 이슈 | 위치 | 리스크 |
|---|---|---|
| 2-1 | `MeetupService#joinMeetup:286` | 스케줄러가 `CLOSED`/`COMPLETED`로 전이한 후에도 참가 가능 |
| 3-1 | `GlobalExceptionHandler:147` | `@ExceptionHandler`에서 `null` 반환 → Spring MVC NPE 또는 빈 응답 |

`AsyncRequestTimeoutException`은 위에 `void` 전용 핸들러가 이미 있으므로, 폴백 핸들러까지 라우팅되는 경우는 극히 드물지만 Spring 버전/설정에 따라 라우팅이 달라질 수 있어 안전하게 처리해야 한다.

---

## 변경 상세

### 1. `GlobalExceptionHandler` — null → rethrow

**파일**: `backend/main/java/com/linkup/Petory/global/exception/GlobalExceptionHandler.java`

현재 코드 (`:147`):
```java
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, Object>> handleException(Exception e) {
    if (e instanceof AsyncRequestTimeoutException) {
        return null;   // ← 위험
    }
    ...
}
```

변경 후:
```java
@ExceptionHandler(Exception.class)
public ResponseEntity<Map<String, Object>> handleException(Exception e) throws AsyncRequestTimeoutException {
    if (e instanceof AsyncRequestTimeoutException ate) {
        throw ate;
    }
    log.error("예상치 못한 오류 발생", e);
    Map<String, Object> response = new HashMap<>();
    response.put("error", "서버 오류가 발생했습니다.");
    response.put("message", "잠시 후 다시 시도해주세요.");
    response.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
}
```

> `throws AsyncRequestTimeoutException` 선언 필요. Java 패턴 매칭 instanceof 사용(Java 16+, 프로젝트 Java 17 적용).

---

### 2. `SpringDataJpaMeetupRepository#incrementParticipantsIfAvailable` — RECRUITING 조건 추가

이 메서드는 도메인 인터페이스 → 어댑터 → JPA Repository 3단계를 거치므로 **세 파일을 모두 수정**해야 한다.

#### 2a. `MeetupRepository` (도메인 인터페이스)
**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/repository/MeetupRepository.java`

```java
// 변경 전
int incrementParticipantsIfAvailable(Long meetupIdx);

// 변경 후 (MeetupStatus import 추가)
int incrementParticipantsIfAvailable(Long meetupIdx, MeetupStatus recruiting);
```

#### 2b. `JpaMeetupAdapter` (어댑터)
**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/repository/JpaMeetupAdapter.java`

```java
// 변경 전
@Override
public int incrementParticipantsIfAvailable(Long meetupIdx) {
    return jpaRepository.incrementParticipantsIfAvailable(meetupIdx);
}

// 변경 후
@Override
public int incrementParticipantsIfAvailable(Long meetupIdx, MeetupStatus recruiting) {
    return jpaRepository.incrementParticipantsIfAvailable(meetupIdx, recruiting);
}
```

#### 2c. `SpringDataJpaMeetupRepository` (JPA 인터페이스)
**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/repository/SpringDataJpaMeetupRepository.java`

현재 쿼리:
```sql
UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1
WHERE m.idx = :meetupIdx
  AND m.currentParticipants < m.maxParticipants
```

변경 후:
```java
@Modifying
@Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1 " +
       "WHERE m.idx = :meetupIdx " +
       "  AND m.currentParticipants < m.maxParticipants " +
       "  AND m.status = :recruiting")
int incrementParticipantsIfAvailable(
    @Param("meetupIdx") Long meetupIdx,
    @Param("recruiting") MeetupStatus recruiting);
```

> `MeetupStatus` import 추가 필요.

---

### 3. `MeetupConflictException` — `meetupNotRecruiting()` 팩토리 추가

**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/exception/MeetupConflictException.java`

추가:
```java
public static MeetupConflictException meetupNotRecruiting() {
    return new MeetupConflictException("모집이 마감된 모임입니다.", "MEETUP_NOT_RECRUITING");
}
```

이 팩토리를 추가하려면 `MeetupConflictException(String message, String errorCode)` 생성자가 필요하다.
현재 생성자는 `MeetupConflictException(String message)` 이고 `ERROR_CODE` 상수를 고정 사용.

변경 방향:
```java
public MeetupConflictException(String message, String errorCode) {
    super(message, HttpStatus.CONFLICT, errorCode);
}

public MeetupConflictException(String message) {
    this(message, "MEETUP_CONFLICT");
}
```

> 기존 `alreadyJoined()` · `fullCapacity()`는 errorCode가 `"MEETUP_CONFLICT"` 그대로 유지됨. errorCode 분리는 Step 2에서 처리.

---

### 4. `MeetupService#joinMeetup` — updated == 0 분기

**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupService.java`

현재 `:284-291`:
```java
int updated = meetupRepository.incrementParticipantsIfAvailable(meetupIdx);
if (updated == 0) {
    throw MeetupConflictException.fullCapacity();
}
```

변경 후 (모임 상태 확인 후 예외 분기):
```java
int updated = meetupRepository.incrementParticipantsIfAvailable(meetupIdx, MeetupStatus.RECRUITING);
if (updated == 0) {
    // RECRUITING 상태가 아니면 모집 마감, 맞지만 인원이 찼으면 fullCapacity
    if (meetup.getStatus() != MeetupStatus.RECRUITING) {
        throw MeetupConflictException.meetupNotRecruiting();
    }
    throw MeetupConflictException.fullCapacity();
}
```

> `meetup`은 이미 위에서 `findByIdWithOrganizer`로 로딩됨. `entityManager.refresh` 없이 로딩 시점의 status로 분기해도 충분 — 원자적 UPDATE가 0을 반환한 이유를 status로 역추론.

---

## Acceptance Criteria

```bash
# 1. 컴파일 통과
./gradlew compileJava

# 2. 수동 시나리오 확인 (서버 기동 후 curl 등)
# a. CLOSED/COMPLETED 상태 모임에 joinMeetup 호출 → 409 + errorCode=MEETUP_NOT_RECRUITING
# b. 인원 가득 찬 RECRUITING 모임 joinMeetup → 409 + errorCode=MEETUP_CONFLICT (Step 2에서 MEETUP_FULL로 교체)
# c. 500 오류 유발 엔드포인트에서 응답 정상 반환 확인 (null 반환 없음)
```

---

## 주의 사항

- `MeetupStatus` import가 `MeetupService`에 없으면 추가.
- `incrementParticipantsIfAvailable` 시그니처 변경 → `JpaMeetupAdapter`에서 위임하는 경우 같이 수정.
- `AsyncRequestTimeoutException` rethrow 시 메서드 시그니처에 `throws` 선언 추가.
