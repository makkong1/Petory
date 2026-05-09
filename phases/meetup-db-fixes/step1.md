# Step 1 — joinMeetup TOCTOU 동시성 버그 수정

## 배경

`MeetupService.joinMeetup`의 현재 흐름:

```
1. findByIdWithOrganizer(meetupIdx)   ← 락 없음
2. existsByMeetupIdxAndUserIdx()      ← 락 없는 중복 체크
3. incrementParticipantsIfAvailable() ← 원자적 UPDATE
4. save(participant)                  ← 복합 PK INSERT
```

같은 사용자가 동시에 두 번 요청하면:
- 1,2단계를 둘 다 통과 → 3단계 둘 다 +1 성공 → `currentParticipants` +2
- 4단계에서 하나가 복합 PK 중복으로 `DataIntegrityViolationException` 발생
- 예외가 잡히지 않으면 트랜잭션 롤백 → 하지만 `incrementParticipantsIfAvailable`의 UPDATE는 이미 커밋 전 상태라 같이 롤백 → 실제로는 카운터도 롤백됨
- 문제: 예외가 호출자까지 500으로 전파됨 + 명시적 `alreadyJoined` 응답이 없음

`findByIdWithLock`(PESSIMISTIC_WRITE)으로 교체하면 동시 요청이 직렬화되어 두 번째 요청은 1단계에서 대기 → 첫 번째 커밋 후 2단계에서 `existsBy` true → `alreadyJoined` 정상 응답.

## 수정 대상 파일

- `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupService.java`

## 수정 내용

### MeetupService.java — joinMeetup

**변경 1:** `findByIdWithOrganizer` → `findByIdWithLock`

```java
// Before
Meetup meetup = meetupRepository.findByIdWithOrganizer(meetupIdx)
        .orElseThrow(MeetupNotFoundException::new);

// After
Meetup meetup = meetupRepository.findByIdWithLock(meetupIdx)
        .orElseThrow(MeetupNotFoundException::new);
```

> `findByIdWithLock`은 organizer를 JOIN FETCH하지 않지만, @Transactional 범위 내이므로
> `meetup.getOrganizer()`는 LAZY 로딩으로 정상 접근 가능 (LazyInitializationException 없음).

**변경 2:** `save(participant)` 전후에 `DataIntegrityViolationException` catch — 이중 안전망

```java
// Before
MeetupParticipants savedParticipant = meetupParticipantsRepository.save(participant);

// After
MeetupParticipants savedParticipant;
try {
    savedParticipant = meetupParticipantsRepository.save(participant);
} catch (org.springframework.dao.DataIntegrityViolationException e) {
    // 비관적 락이 있어도 극단적 경쟁 조건의 최후 안전망
    meetupRepository.decrementParticipantsIfPositive(meetupIdx);
    log.warn("중복 참가 시도 감지 (PK 충돌): meetupIdx={}, userIdx={}", meetupIdx, userIdx);
    throw MeetupConflictException.alreadyJoined();
}
```

## Import 추가 필요

`MeetupService.java` 상단에 없으면 추가:
```java
import org.springframework.dao.DataIntegrityViolationException;
```

## 검증

```bash
./gradlew compileJava
```

컴파일 성공 + 기존 테스트 통과 확인:
```bash
./gradlew test --tests "*MeetupService*"
```

## AC (Acceptance Criteria)

- [ ] `joinMeetup`이 `findByIdWithLock`을 사용한다
- [ ] `save(participant)` 실패 시 `decrementParticipantsIfPositive` 호출 후 `alreadyJoined` 예외를 던진다
- [ ] `./gradlew compileJava` 성공
