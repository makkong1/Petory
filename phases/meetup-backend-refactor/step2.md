# Step 2 — High: updateMeetup 개선 + 예외 팩토리 통합

## 목표
1. `MeetupService#updateMeetup` — `findByIdWithDetails` → `findByIdWithOrganizer` 교체
2. `MeetupService#updateMeetup` — 날짜 변경 시 과거 날짜 검증 추가
3. `MeetupService#updateMeetup` — `maxParticipants` 축소 검증 추가
4. `MeetupValidationException` — `maxBelowCurrent()`, `invalidMaxParticipants()` 팩토리 추가
5. `MeetupConflictException` — `alreadyJoined()` / `fullCapacity()` errorCode 분리

---

## 배경 · 위험

| 이슈 | 위치 | 리스크 |
|---|---|---|
| 2-2 | `MeetupService#updateMeetup:130` | `findByIdWithDetails`는 참가자 전체를 FETCH — 수정 시 불필요한 쿼리 |
| 2-3 | `MeetupService#updateMeetup:161` | 현재 인원(예: 5명)보다 작은 값(예: 3명)으로 줄이면 `CLOSED` 즉시 전이 |
| 3-3 | `MeetupService#updateMeetup:158` | 과거 날짜로 변경 → 스케줄러가 즉시 `COMPLETED` 전이 |
| 3-2 | `MeetupConflictException` | `alreadyJoined` · `fullCapacity` 모두 errorCode `MEETUP_CONFLICT` — 프론트 구분 불가 |

---

## 변경 상세

### 1. `MeetupService#updateMeetup` — 불필요 페치 교체

**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupService.java` (`:130`)

```java
// 변경 전
Meetup meetup = meetupRepository.findByIdWithDetails(meetupIdx)
        .orElseThrow(MeetupNotFoundException::new);

// 변경 후
Meetup meetup = meetupRepository.findByIdWithOrganizer(meetupIdx)
        .orElseThrow(MeetupNotFoundException::new);
```

`updateMeetup`에서 사용하는 것은 `meetup.getOrganizer().getIdx()`뿐 — 참가자 FETCH 불필요.

---

### 2. `MeetupService#updateMeetup` — 날짜 과거 검증 추가

**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupService.java` (`:158` 근방)

현재 코드:
```java
if (meetupDTO.getDate() != null) {
    meetup.setDate(meetupDTO.getDate());
}
```

변경 후:
```java
if (meetupDTO.getDate() != null) {
    if (meetupDTO.getDate().isBefore(LocalDateTime.now())) {
        throw MeetupValidationException.dateMustBeFuture();
    }
    meetup.setDate(meetupDTO.getDate());
}
```

> `createMeetup`과 동일한 `dateMustBeFuture()` 예외 재사용.

---

### 3. `MeetupService#updateMeetup` — maxParticipants 축소 검증 추가

**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupService.java` (`:161` 근방)

현재 코드:
```java
if (meetupDTO.getMaxParticipants() != null) {
    meetup.setMaxParticipants(meetupDTO.getMaxParticipants());
}
```

변경 후:
```java
if (meetupDTO.getMaxParticipants() != null) {
    int newMax = meetupDTO.getMaxParticipants();
    if (newMax < 1) {
        throw MeetupValidationException.invalidMaxParticipants();
    }
    if (newMax < meetup.getCurrentParticipants()) {
        throw MeetupValidationException.maxBelowCurrent();
    }
    meetup.setMaxParticipants(newMax);
}
```

---

### 4. `MeetupValidationException` — 팩토리 메서드 추가

**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/exception/MeetupValidationException.java`

추가:
```java
public static MeetupValidationException invalidMaxParticipants() {
    return new MeetupValidationException("최대 참여 인원은 1 이상이어야 합니다.");
}

public static MeetupValidationException maxBelowCurrent() {
    return new MeetupValidationException("최대 인원은 현재 참여자 수보다 작을 수 없습니다.");
}
```

---

### 5. `MeetupConflictException` — errorCode 분리

**파일**: `backend/main/java/com/linkup/Petory/domain/meetup/exception/MeetupConflictException.java`

전제: Step 1에서 `MeetupConflictException(String message, String errorCode)` 생성자가 이미 추가됨.

변경 후:
```java
public static MeetupConflictException alreadyJoined() {
    return new MeetupConflictException("이미 참가한 모임입니다.", "MEETUP_ALREADY_JOINED");
}

public static MeetupConflictException fullCapacity() {
    return new MeetupConflictException("모임 인원이 가득 찼습니다.", "MEETUP_FULL");
}
```

> `ERROR_CODE = "MEETUP_CONFLICT"` 상수는 raw 생성자 호출 시 기본값으로만 남겨두거나 제거.

---

## Acceptance Criteria

```bash
# 컴파일 통과
./gradlew compileJava

# 수동 시나리오 (서버 기동 후)
# a. updateMeetup에 과거 날짜 전달 → 400 + "모임 일시는 현재 시간 이후여야 합니다."
# b. updateMeetup에 currentParticipants보다 작은 maxParticipants 전달 → 400 + "최대 인원은 현재 참여자 수보다 작을 수 없습니다."
# c. joinMeetup 중복 참가 → 409 + errorCode=MEETUP_ALREADY_JOINED
# d. joinMeetup 인원 초과 → 409 + errorCode=MEETUP_FULL
```

---

## 주의 사항

- Step 1이 완료(컴파일 통과)된 상태에서 진행.
- `MeetupService`에 `MeetupStatus` import가 없으면 추가.
