# Meetup 백엔드 도메인 로직 점검 · 리팩토링 백로그

**최초 작성일**: 2026-04-12  
**재분석일**: 2026-04-16 (2차 — 예외 처리 포함)  
**문서 동기화 (3차)**: 2026-04-16 — `phases/meetup-backend-refactor` step 1~5 완료를 본 문서에 반영. §2·§3 원문은 당시 스냅샷으로 두었으니, **현재 코드와의 대응은 아래 §0 표**를 우선한다.  
**목적**: 백엔드 meetup 폴더(서비스·리포지토리·컨트롤러·엔티티·컨버터·예외) 및 글로벌 예외 처리까지 직접 읽고 이슈를 재정리한다.  
**분석 대상 파일**: `MeetupService`, `SpringDataJpaMeetupRepository`, `JpaMeetupAdapter`, `MeetupRepository`, `MeetupController`, `MeetupScheduler`, `MeetupChatRoomEventListener`, `MeetupConverter`, `MeetupDTO`, `Meetup`, `Meetup*Exception`, `ApiException`, `GlobalExceptionHandler`

---

## 0. `phases/meetup-backend-refactor` 반영 현황 (2026-04-16)

- **Phase 상태**: 루트 `phases/index.json`의 `meetup-backend-refactor`는 **completed**. 스텝별 요약·변경 근거는 `phases/meetup-backend-refactor/index.json`, `step1.md` ~ `step5.md`.
- **전역 문서 정리**: meetup 한 파일만이 아니라 `agent-docs/` · `docs/domains/` · `docs/refactoring/` 교차·목차 통합은 **별도(반나절~1일)** 작업으로 두는 것을 권장.

### 0.1 §2·§3 세부 이슈 ↔ 코드 (요약)

| 구간 | 상태 | 비고 |
|------|------|------|
| §2-1 `joinMeetup` RECRUITING | ✅ | `incrementParticipantsIfAvailable(..., RECRUITING)`, `meetupNotRecruiting()` |
| §2-2 `updateMeetup` FETCH | ✅ | `findByIdWithOrganizer` |
| §2-3 `maxParticipants` 축소 | ✅ | `currentParticipants` 초과 시 검증 |
| §2-4 위치·키워드·주최자 전량 List | ⚠️ 부분 | DB 전량 조회 후 서비스에서 **`MAX_LIST_SIZE`(500)** 잘라 응답. **Pageable API**는 `/available` 등과 달리 미적용 |
| §2-5 `findAvailableMeetups` GROUP BY | ✅ | JPQL 단순화 + `Pageable` LIMIT (Slice 응답) |
| §2-6 `findByIdWithLock` 소프트삭제 | ✅ | `isDeleted` 조건 추가 (프로덕션 서비스 경로에서 미사용이면 테스트 전용) |
| §2-7 `MeetupDTO` 삭제 메타 노출 | ✅ | `isDeleted` / `deletedAt` → `@JsonIgnore` |
| §2-8 `isUserParticipating` 풀 Users | ✅ | `findIdxByIdString` |
| §2-9 `getMeetupParticipants` 존재 확인 | ✅ | 선행 `findByIdWithOrganizer` |
| §2-10 무페이징 `getAvailableMeetups()` | ✅ | `@Deprecated`, 컨트롤러는 Slice 버전 |
| §2-11 `@Timed` 이름 충돌 | ✅ | 페이징 버전 `@Timed("getAllMeetupsPaged")` 등 구분 |
| §3-1 `handleException` null | ✅ | `AsyncRequestTimeoutException` 재throw |
| §3-2 `MeetupConflictException` errorCode | ✅ | `MEETUP_ALREADY_JOINED` / `MEETUP_FULL` 등 분리 |
| §3-3 `updateMeetup` 과거 날짜 | ✅ | `dateMustBeFuture` 등 |
| §3-4 `meetupNotRecruiting` 팩토리 | ✅ | §2-1과 함께 반영 |
| §3-5 `cancelMeetupParticipation` catch | ✅ | `ApiException` / `Exception` 분리 로깅 |
| §3-7 응답 `error`/`message` 중복 | ✅ | `handleApiException` 등 3-key 구조 (step4) |
| §3-6 채팅방 생성 실패 복구 | ⚠️ 미해결 | 재시도·보상 트랜잭션은 백로그 |
| §3-8·§3-9 검증·응답 구조 | ⚠️ 검토 | 필요 시 별도 이슈로 쪼개기 |

### 0.2 §1 표와 겹치는 **남은 백로그** (코드 기준)

- **§1 #5** — `cancelMeetupParticipation` → `conversationService.leaveMeetupChat` 직접 호출(도메인 경계).
- **§1 #7** — 컨트롤러 `Authentication` null 체크 반복.
- **§1 #8** — `MeetupDTO` Bean Validation 미흡.
- **§1 #9** — `findByIdWithDetails` 다중 FETCH 구조 유지(의도적 트레이드오프).
- **§2-4** — 위치·키워드·주최자 API에 **DB 페이징**을 둘지 제품 결정 후 진행.

---

## 1. 이전 분석 대비 구현 상태 확인

| #   | 이슈 (2026-04-12)                             | 현재 상태        | 비고                                                                                              |
| --- | --------------------------------------------- | ---------------- | ------------------------------------------------------------------------------------------------- |
| 1   | `findNearbyMeetups()` organizer N+1           | ✅ **해결됨**    | `findNearbyMeetupIds` + `findByIdxInWithOrganizer` 2-step                                         |
| 2   | 상태 전이 부재 (`CLOSED`/`COMPLETED`)         | ✅ **해결됨**    | `MeetupScheduler` 매시 정각 bulk UPDATE                                                           |
| 3   | 생성 시 `currentParticipants` 이중 저장       | ✅ **해결됨**    | 빌더에서 `currentParticipants(1)` 단일 INSERT                                                     |
| 4   | 목록 페이징 없음                              | ✅ **부분 해결** | `getAllMeetups(Pageable)`, `getAvailableMeetups` → Slice+Pageable. 위치·키워드·주최자는 **서비스 `MAX_LIST_SIZE`(500) 상한**만 적용, DB OFFSET 페이징 API는 미적용 |
| 5   | 참가 취소 Chat 도메인 직접 결합               | ⚠️ **미해결**    | `cancelMeetupParticipation` → `conversationService.leaveMeetupChat()` 직접 호출 유지              |
| 6   | `findByIdWithOrganizer` 소프트 삭제 필터 없음 | ✅ **해결됨**    | `AND (m.isDeleted = false OR m.isDeleted IS NULL)` 추가됨                                         |
| 7   | 컨트롤러 인증 체크 중복                       | ⚠️ **미해결**    | `authentication != null ? ... : null` + `UnauthenticatedException` 패턴 전 메서드 반복            |
| 8   | Bean Validation 미활용                        | ⚠️ **미해결**    | `MeetupDTO` 필드에 검증 애노테이션 없음, 날짜 검증 서비스에만 존재                                |
| 9   | `findByIdWithDetails` DISTINCT + 다중 FETCH   | ⚠️ **유지**      | 3-way LEFT JOIN FETCH + DISTINCT 구조 그대로                                                      |

---

## 2. 도메인 로직 이슈

### Critical

#### 2-1. `joinMeetup` — 상태 검증 누락

**위치**: `MeetupService#joinMeetup:286`  
**문제**: `incrementParticipantsIfAvailable` 쿼리는 `currentParticipants < maxParticipants` 만 체크. `CLOSED`/`COMPLETED` 상태를 확인하지 않아 스케줄러가 상태를 전이한 후에도 모임 참가가 허용됨.

```sql
-- 현재 (문제)
UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1
WHERE m.idx = :meetupIdx
  AND m.currentParticipants < m.maxParticipants

-- 개선 (상태 조건 추가)
UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1
WHERE m.idx = :meetupIdx
  AND m.currentParticipants < m.maxParticipants
  AND m.status = 'RECRUITING'
```

---

### High

#### 2-2. `updateMeetup` — `findByIdWithDetails` 불필요 사용

**위치**: `MeetupService#updateMeetup:130`  
**문제**: 수정 시 `findByIdWithDetails`를 호출해 주최자 + 참가자 전체를 FETCH. 수정에는 주최자 정보만 필요.  
**개선**: `findByIdWithOrganizer`로 교체.

---

#### 2-3. `updateMeetup` — `maxParticipants` 축소 검증 없음

**위치**: `MeetupService#updateMeetup:161`  
**문제**: 현재 참가자 수(예: 5명)보다 작은 값(예: 3명)으로 줄여도 오류 없이 저장. `currentParticipants > maxParticipants` 상태가 되어 스케줄러가 즉시 `CLOSED`로 전이하는 부작용 발생.  
**개선**: `newMax >= meetup.getCurrentParticipants()` 검증 추가.

---

#### 2-4. 위치·키워드·주최자별 조회 — 페이징 없음

**위치**: `MeetupService#getMeetupsByLocation`, `#searchMeetupsByKeyword`, `#getMeetupsByOrganizer`  
**문제**: 세 메서드 모두 전량 `List` 반환. 데이터 증가 시 OOM·응답 지연 위험.  
**개선**: `Pageable` 파라미터 추가 또는 최소한 상한(`LIMIT`) 적용.

---

#### 2-5. `findAvailableMeetups` — `@EntityGraph` + `GROUP BY` + `Pageable` 조합

**위치**: `SpringDataJpaMeetupRepository#findAvailableMeetups`  
**문제**: `GROUP BY m.idx HAVING COUNT(p) < m.maxParticipants` + `@EntityGraph(organizer)` + `Pageable` 조합. Hibernate는 컬렉션 페치와 페이징이 함께 사용될 때 SQL `LIMIT/OFFSET` 대신 **메모리 페이징**(`HHH90003004`)을 수행할 수 있음. 데이터 증가 시 전량 로드 후 애플리케이션 메모리에서 잘림.

```java
// 개선 예시: GROUP BY/HAVING 제거, currentParticipants 직접 비교
@Query("SELECT m FROM Meetup m JOIN FETCH m.organizer " +
       "WHERE m.date > :currentDate " +
       "AND m.currentParticipants < m.maxParticipants " +
       "AND m.status = 'RECRUITING' " +
       "AND (m.isDeleted = false OR m.isDeleted IS NULL) " +
       "ORDER BY m.date ASC")
```

---

### Medium

#### 2-6. `findByIdWithLock` — 소프트 삭제 필터 없음 + 미사용

**위치**: `SpringDataJpaMeetupRepository#findByIdWithLock`  
**문제**: 쿼리에 `isDeleted` 조건이 없고, `MeetupService` 내에서 실제 호출 없음 (원자적 UPDATE 방식으로 대체됨).  
**개선**: 소프트 삭제 조건 추가, 또는 사용하지 않으면 인터페이스에서 제거.

---

#### 2-7. `MeetupDTO` — 소프트 삭제 메타 필드 클라이언트 노출

**위치**: `MeetupDTO:28-29`  
**문제**: `isDeleted`, `deletedAt` 필드가 응답 JSON에 포함됨. 내부 운영 데이터 노출.  
**개선**: 응답 DTO에서 제거 또는 `@JsonIgnore` 적용.

---

#### 2-8. `isUserParticipating` — 참여 확인에 풀 Users 엔티티 로딩

**위치**: `MeetupService#isUserParticipating:353`  
**문제**: userId → `findByIdString` → `Users` 전체 엔티티 → `getIdx()`만 사용. `idx`만 필요한데 전체 엔티티 로드.  
**개선**: `usersRepository`에 `findIdxByIdString(String id): Optional<Long>` 경량 쿼리 추가, 또는 참가 확인 쿼리에 userId 직접 JOIN.

---

#### 2-9. `getMeetupParticipants` — 모임 존재 확인 없음

**위치**: `MeetupService#getMeetupParticipants:251`  
**문제**: 존재 여부·삭제 여부 확인 없이 바로 참가자 목록 조회. 삭제된 모임의 참가자도 반환됨.  
**개선**: 메서드 앞에 `meetupRepository.findByIdWithOrganizer(meetupIdx).orElseThrow(MeetupNotFoundException::new)` 추가.

---

### Low

#### 2-10. `getAvailableMeetups()` 무한 레거시 메서드 잔존

**위치**: `MeetupService#getAvailableMeetups():378`  
**문제**: `Pageable.unpaged()`를 넘기는 페이징 없는 버전이 여전히 존재. 컨트롤러는 이미 Slice 버전만 사용하지만 내부 재사용 위험.  
**개선**: `@Deprecated` 마킹 또는 제거.

---

#### 2-11. `@Timed` 이름 충돌

**위치**: `MeetupService#getAllMeetups()`, `#getAllMeetups(Pageable)`  
**문제**: 두 메서드 모두 `@Timed("getAllMeetups")`로 동일 이름 사용. 메트릭 집계 시 두 메서드의 측정값이 합산됨.  
**개선**: 비페이징 버전을 제거하거나 이름을 `getAllMeetupsPaged`처럼 구분.

---

## 3. 예외 처리 이슈

### Critical

#### 3-1. `GlobalExceptionHandler#handleException` — `null` 반환

**위치**: `GlobalExceptionHandler:147-149`  
**문제**: `Exception` 폴백 핸들러에서 `AsyncRequestTimeoutException`을 다시 걸러 `return null`을 반환. `@ExceptionHandler`에서 `null`을 반환하면 Spring MVC가 응답 처리를 포기하거나 NPE를 발생시킬 수 있음.

```java
// 현재 (문제)
@ExceptionHandler(Exception.class)
public ResponseEntity<...> handleException(Exception e) {
    if (e instanceof AsyncRequestTimeoutException) {
        return null;   // ← 위험
    }
    ...
}

// 개선 (null 대신 예외를 다시 던지거나, 빈 응답 반환)
if (e instanceof AsyncRequestTimeoutException) {
    throw (AsyncRequestTimeoutException) e;
    // 또는: return ResponseEntity.noContent().build();
}
```

> `AsyncRequestTimeoutException`은 위에 별도 `void` 핸들러가 이미 있으므로, 정상적으로는 폴백 핸들러까지 오지 않지만 Spring 버전이나 설정에 따라 라우팅이 달라질 수 있어 안전하게 처리해야 함.

---

### High

#### 3-2. `MeetupConflictException` — errorCode 단일값으로 두 상황 구분 불가

**위치**: `MeetupConflictException`  
**문제**: `alreadyJoined()`와 `fullCapacity()` 모두 `errorCode = "MEETUP_CONFLICT"` 사용. 프론트엔드가 두 상황을 errorCode로 구분할 수 없어 동일한 메시지 처리만 가능.

```java
// 개선: 상황별 errorCode 분리
public static MeetupConflictException alreadyJoined() {
    return new MeetupConflictException("이미 참가한 모임입니다.", "MEETUP_ALREADY_JOINED");
}

public static MeetupConflictException fullCapacity() {
    return new MeetupConflictException("모임 인원이 가득 찼습니다.", "MEETUP_FULL");
}
```

---

#### 3-3. `updateMeetup` — 날짜 변경 시 과거 날짜 검증 없음

**위치**: `MeetupService#updateMeetup:158-160`  
**문제**: `createMeetup`에서는 날짜가 현재 이후인지 검증하지만, `updateMeetup`에서는 날짜를 과거로 변경해도 그대로 저장됨. 스케줄러가 바로 `COMPLETED`로 전이시키는 부작용 발생.

```java
// createMeetup에는 있음
if (meetupDTO.getDate() != null && meetupDTO.getDate().isBefore(LocalDateTime.now())) {
    throw MeetupValidationException.dateMustBeFuture();
}

// updateMeetup에는 없음 ← 추가 필요
if (meetupDTO.getDate() != null) {
    if (meetupDTO.getDate().isBefore(LocalDateTime.now())) {
        throw MeetupValidationException.dateMustBeFuture();
    }
    meetup.setDate(meetupDTO.getDate());
}
```

---

#### 3-4. `joinMeetup` — 상태 거부용 예외 팩토리 메서드 없음

**위치**: `MeetupConflictException` 또는 `MeetupValidationException`  
**문제**: 2-1에서 `CLOSED`/`COMPLETED` 상태 검증을 추가하더라도, 이 상황에 대응하는 명시적 팩토리 메서드가 없음. 현재 `MeetupConflictException`의 개념(충돌)과 맞지 않는 상황에서 억지로 재사용하거나 raw 생성자를 호출하게 됨.

```java
// 추가 필요
public static MeetupConflictException meetupNotRecruiting() {
    return new MeetupConflictException("모집이 마감된 모임입니다.", "MEETUP_NOT_RECRUITING");
}
```

---

### Medium

#### 3-5. `cancelMeetupParticipation` — `catch (Exception e)` 과도하게 광범위

**위치**: `MeetupService#cancelMeetupParticipation:341-347`  
**문제**: 채팅방 나가기를 `catch (Exception e)`로 감싸 모든 예외를 삼킴. 의도는 옳으나(채팅 실패가 참가 취소를 막으면 안 됨) 범위가 너무 넓어 프로그래밍 오류(NPE, ClassCast 등)도 무시됨.

```java
// 현재
} catch (Exception e) {
    log.error("채팅방 나가기 실패: ...", e.getMessage());
}

// 개선: 예상 가능한 비즈니스 예외만 처리
} catch (ApiException e) {
    log.warn("채팅방 나가기 실패 (비즈니스): meetupIdx={}, error={}", meetupIdx, e.getMessage());
} catch (Exception e) {
    log.error("채팅방 나가기 예상치 못한 오류: meetupIdx={}", meetupIdx, e);
}
```

---

#### 3-6. `MeetupChatRoomEventListener` — 채팅방 생성 실패 복구 없음

**위치**: `MeetupChatRoomEventListener#handleMeetupCreated:74-82`  
**문제**: 채팅방 생성 실패 시 `log.error`만 남기고 끝. 재시도 메커니즘이 없어 **모임은 존재하나 채팅방이 없는 불일치 상태**가 그대로 유지됨. TODO 주석만 남아 있음.

```java
// 현재 (TODO 미이행)
} catch (Exception e) {
    log.error("모임 채팅방 생성 실패: ...");
    // TODO: 재시도 메커니즘 추가 고려
}
```

**개선 방향**:

- `MeetupScheduler`에서 채팅방 없는 모임 감지 후 재생성 시도
- 또는 별도 재시도 큐(Spring Retry, Dead Letter Queue) 활용

---

#### 3-7. `ApiException` 응답 — `error`와 `message` 동일값 중복

**위치**: `GlobalExceptionHandler#handleApiException:109-110`  
**문제**: `response.put("error", e.getMessage())`와 `response.put("message", e.getMessage())`가 동일한 값을 다른 키에 중복 저장. 불필요한 응답 payload.

```java
// 현재
response.put("error", e.getMessage());
response.put("message", e.getMessage());

// 개선: 키 통일 또는 역할 분리
// error: 기술적 설명, message: 사용자 메시지로 구분하거나 하나만 사용
response.put("message", e.getMessage());
response.put("errorCode", e.getErrorCode());
```

---

### Low

#### 3-8. `MeetupValidationException` — 생성/수정 공통 검증 미정의

**위치**: `MeetupValidationException`  
**문제**: `dateMustBeFuture()` 하나만 존재. `maxParticipants`(1 이상, 최대값 이하), 제목 길이 등 다른 검증 케이스가 늘어날 때 서비스에서 raw 생성자를 직접 쓰거나 팩토리 패턴을 일관되게 적용하지 못함.  
**개선**: 검증 케이스별 static factory 메서드 추가.

```java
public static MeetupValidationException invalidMaxParticipants() {
    return new MeetupValidationException("최대 참여 인원은 1 이상이어야 합니다.");
}

public static MeetupValidationException maxBelowCurrent() {
    return new MeetupValidationException("최대 인원은 현재 참여자 수보다 작을 수 없습니다.");
}
```

---

#### 3-9. 정상 응답과 오류 응답 DTO 구조 불일치

**위치**: `MeetupController` 전체, `GlobalExceptionHandler` 전체  
**문제**: 정상 응답은 `{"meetup": ..., "message": "..."}`, 오류 응답은 `{"error": "...", "message": "...", "status": ..., "errorCode": "..."}` 구조. 키 이름과 계층이 달라 프론트엔드 에러 핸들링 코드가 분기 처리를 해야 함.  
**개선**: 공통 `ApiResponse<T>` 래퍼 클래스 도입 (다른 도메인과 통일 전 팀 컨벤션 확인 필요).

---

## 4. 우선순위별 전체 백로그 요약

### Critical

| 항목                        | 위치                           | 문제                             |
| --------------------------- | ------------------------------ | -------------------------------- |
| `joinMeetup` 상태 미검증    | `MeetupService#joinMeetup:286` | CLOSED/COMPLETED 모임 참가 가능  |
| `handleException` null 반환 | `GlobalExceptionHandler:147`   | Spring MVC NPE 또는 빈 응답 위험 |

### High

| 항목                                      | 위치                                 | 문제                                            |
| ----------------------------------------- | ------------------------------------ | ----------------------------------------------- |
| `updateMeetup` 불필요 페치                | `MeetupService#updateMeetup:130`     | `findByIdWithDetails` → `findByIdWithOrganizer` |
| `updateMeetup` maxParticipants 검증 없음  | `MeetupService#updateMeetup:161`     | 현재 인원 초과 축소 허용                        |
| `updateMeetup` 날짜 변경 시 과거 허용     | `MeetupService#updateMeetup:158`     | 과거 날짜로 변경 → 즉시 COMPLETED               |
| 위치·키워드·주최자 조회 페이징 없음       | `getMeetupsByLocation` 등 3개 메서드 | 전량 List 반환                                  |
| `findAvailableMeetups` 메모리 페이징 위험 | `SpringDataJpaMeetupRepository`      | GROUP BY + @EntityGraph + Pageable              |
| `MeetupConflictException` errorCode 공유  | `MeetupConflictException`            | 프론트 두 상황 구분 불가                        |
| 상태 거부용 예외 팩토리 없음              | `MeetupConflictException`            | joinMeetup 상태 검증 추가 시 필요               |

### Medium

| 항목                                              | 위치                                      | 문제                           |
| ------------------------------------------------- | ----------------------------------------- | ------------------------------ |
| 참가 취소 ↔ 채팅 직접 결합                        | `MeetupService#cancelMeetupParticipation` | 이벤트 비대칭                  |
| 컨트롤러 인증 보일러플레이트                      | `MeetupController` 전 메서드              | null 체크 반복                 |
| `cancelMeetupParticipation` 광범위 catch          | `MeetupService:341`                       | 프로그래밍 오류도 삼킴         |
| 채팅방 생성 실패 복구 없음                        | `MeetupChatRoomEventListener:74`          | 모임↔채팅 불일치 상태 지속     |
| `findByIdWithLock` 소프트 삭제 필터 없음 + 미사용 | `SpringDataJpaMeetupRepository`           | 삭제된 모임 잠금 가능          |
| `MeetupDTO` 소프트 삭제 필드 노출                 | `MeetupDTO:28-29`                         | isDeleted, deletedAt 응답 포함 |
| `isUserParticipating` Users 전체 엔티티 로딩      | `MeetupService:353`                       | 참여 확인에 불필요한 SELECT    |
| `getMeetupParticipants` 모임 존재 확인 없음       | `MeetupService:251`                       | 삭제된 모임 참가자 반환        |
| `ApiException` 응답 error/message 중복            | `GlobalExceptionHandler:109-110`          | 동일값 두 키에 저장            |

### Low

| 항목                                    | 위치                                         | 문제                              |
| --------------------------------------- | -------------------------------------------- | --------------------------------- |
| Bean Validation 미활용                  | `MeetupDTO`, `MeetupService`                 | 날짜·제목 등 서비스 수동 검증     |
| `getAvailableMeetups()` 레거시 잔존     | `MeetupService:378`                          | `Pageable.unpaged()` 무한 버전    |
| `MeetupValidationException` 팩토리 미비 | `MeetupValidationException`                  | 검증 케이스 확장 시 일관성 없음   |
| `@Timed` 이름 중복                      | `MeetupService`                              | 두 getAllMeetups 메서드 동일 이름 |
| 정상/오류 응답 구조 불일치              | `MeetupController`, `GlobalExceptionHandler` | 프론트 에러 핸들링 분기 필요      |
| `findByIdWithDetails` 3-way FETCH       | `SpringDataJpaMeetupRepository`              | 참가자 많을 때 카르테시안 증가    |
| AdminMeetupController 부재              | —                                            | 관리자 강제 상태 변경 API 없음    |
| 키워드 LIKE % 양방향                    | `findByKeyword`                              | 인덱스 비효율 (스키마 변경 필요)  |

---

## 5. 잘 된 부분 (유지)

| 항목                    | 위치                                                                  | 내용                                                                       |
| ----------------------- | --------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| 모임 생성 → 채팅방      | `MeetupChatRoomEventListener`                                         | `afterCommit` 후 `@Async` + `REQUIRES_NEW` — 트랜잭션 분리 올바름          |
| 참여자 수 동시성 제어   | `incrementParticipantsIfAvailable`, `decrementParticipantsIfPositive` | DB 레벨 원자적 UPDATE                                                      |
| 근처 모임 2-step 조회   | `findNearbyMeetupIds` + `findByIdxInWithOrganizer`                    | organizer N+1 완전 제거                                                    |
| 상태 자동 전이          | `MeetupScheduler`                                                     | `closeFullRecruitingMeetups` + `completePastMeetups` 매시 정각 벌크 UPDATE |
| 단건 조회 소프트 삭제   | `findByIdWithOrganizer`, `findByIdWithDetails`                        | 두 쿼리 모두 `isDeleted` 조건 포함                                         |
| 어댑터 패턴             | `MeetupRepository` + `JpaMeetupAdapter`                               | 도메인 인터페이스와 JPA 구현체 분리                                        |
| 도메인 예외 계층        | `Meetup*Exception` → `ApiException`                                   | HTTP 상태 코드 + errorCode 포함, 글로벌 핸들러 일원화                      |
| `@BatchSize(size = 50)` | `Meetup#participants`                                                 | 목록 조회 시 참가자 N+1 배치 로딩                                          |

---

## 6. 코드 앵커 (빠른 탐색)

| 주제                                     | 클래스 / 메서드                                      |
| ---------------------------------------- | ---------------------------------------------------- |
| 참가 상태 미검증                         | `MeetupService#joinMeetup:286`                       |
| `handleException` null 반환              | `GlobalExceptionHandler:147`                         |
| updateMeetup 날짜 검증 누락              | `MeetupService#updateMeetup:158`                     |
| updateMeetup 불필요 페치                 | `MeetupService#updateMeetup:130`                     |
| `cancelMeetupParticipation` 광범위 catch | `MeetupService:341`                                  |
| 채팅방 실패 복구 TODO                    | `MeetupChatRoomEventListener:79`                     |
| errorCode 공유                           | `MeetupConflictException:13`                         |
| 소프트 삭제 필드 노출                    | `MeetupDTO:28-29`                                    |
| 메모리 페이징 위험                       | `SpringDataJpaMeetupRepository#findAvailableMeetups` |
| 스케줄러                                 | `MeetupScheduler#transitionMeetupStatuses`           |
| 글로벌 예외 처리                         | `GlobalExceptionHandler`                             |

---

## 7. 관련 문서

- 종합 요약·FE 포함: [refactoring-summary.md](./refactoring-summary.md)
- 도메인 스펙: [docs/domains/meetup.md](../../domains/meetup.md)
- Fetch 전략: [../fetch-optimization/meetup/Fetch 전략 개선 (Fetch Join vs Batch Size).md](<../fetch-optimization/meetup/Fetch%20전략%20개선%20(Fetch%20Join%20vs%20Batch%20Size).md>)

---

## 8. 구현 이력

| 날짜       | 구간     | 내용                                                                                     |
| ---------- | -------- | ---------------------------------------------------------------------------------------- |
| 2026-04-12 | Critical | `findNearbyMeetupIds` + `findByIdxInWithOrganizer` — organizer N+1 해결                  |
| 2026-04-12 | Critical | `createMeetup` `currentParticipants(1)` 단일 INSERT                                      |
| 2026-04-12 | Critical | `MeetupScheduler` 추가                                                                   |
| 2026-04-12 | High     | `findByIdWithOrganizer` / `findByIdWithDetails` 소프트 삭제 조건 추가                    |
| 2026-04-12 | High     | `GET /api/meetups` Page 페이징, `GET /api/meetups/available` Slice 페이징                |
| 2026-04-16 | —        | **2차 재분석 완료**: 예외 처리 포함 신규 이슈 추가 (Critical 2, High 7, Medium 9, Low 8) |
