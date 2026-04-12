# Meetup 백엔드 도메인 로직 점검 · 리팩토링 백로그

**작성일**: 2026-04-12  
**목적**: `MeetupService`, `SpringDataJpaMeetupRepository`, 컨트롤러 등 백엔드 로직을 기준으로 이슈를 정리하고, 별도 세션에서 도출된 분석과 **교차 검증**한 결과를 문서화한다.

**범위**: 백엔드(Java) 위주. 프론트·지도 UX는 `refactoring-summary.md` 및 도메인 문서를 참고.

---

## 1. 외부 분석 vs 코드 대조 (요약)

| #   | 이슈                                          | 대조 결과     | 비고                                                                                           |
| --- | --------------------------------------------- | ------------- | ---------------------------------------------------------------------------------------------- |
| 1   | `findNearbyMeetups()` organizer N+1           | **해당**      | 네이티브 `SELECT m.*` 후 `MeetupConverter.toDTO`에서 `organizer` 접근 → lazy 로딩 가능         |
| 2   | 상태 전이 부재 (`CLOSED` / `COMPLETED`)       | **해당**      | 스케줄러·배치 없음. `CareRequestScheduler`와 달리 모임 자동 전이 없음                          |
| 3   | 생성 시 `currentParticipants` 0 → 1 이중 저장 | **해당**      | INSERT 후 `setCurrentParticipants(1)` + `save`로 불필요한 UPDATE                               |
| 4   | 목록 페이징 없음                              | **해당**      | `findAllNotDeleted`, `findAvailableMeetups` 등 전량 `List`                                     |
| 5   | Chat 도메인 결합                              | **부분 해당** | 생성은 `MeetupCreatedEvent` + 리스너로 분리됨. **참가 취소**는 `ConversationService` 직접 호출 |
| 6   | `findByIdWithOrganizer` 소프트 삭제 필터 없음 | **해당**      | `WHERE m.idx = :idx`만 존재                                                                    |
| 7   | 컨트롤러 인증 체크 중복                       | **해당**      | `@PreAuthorize("isAuthenticated()")` + 메서드마다 `Authentication` null 체크                   |
| 8   | Bean Validation 미활용                        | **해당**      | 날짜 등 서비스 수동 검증                                                                       |
| 9   | `findByIdWithDetails` DISTINCT + 다중 FETCH   | **해당**      | 대규모 참가자 시 부하 가능                                                                     |
| —   | 관리자 상태 변경 API 없음                     | **해당**      | `AdminMeetupController`에 상태 변경 엔드포인트 없음                                            |

---

## 2. 우선순위별 백로그

### Critical (즉시 검토 권장)

| 항목              | 위치                                                                        | 문제                                                 | 개선 방향                                                                      |
| ----------------- | --------------------------------------------------------------------------- | ---------------------------------------------------- | ------------------------------------------------------------------------------ |
| 근처 모임 N+1     | `SpringDataJpaMeetupRepository#findNearbyMeetups` → `MeetupConverter#toDTO` | 주최자 미 페치 시 행마다 `Users` 조회                | 네이티브에서 `users` JOIN + 매핑, 또는 조회 후 `IN` 배치 로딩, 또는 2단계 쿼리 |
| 상태 전이         | `MeetupStatus`, `MeetupService`                                             | 정원·일시에 따른 `CLOSED`/`COMPLETED` 자동 반영 없음 | `@Scheduled` + 벌크 UPDATE (케어 도메인 스케줄러 패턴 참고)                    |
| 생성 시 이중 저장 | `MeetupService#createMeetup`                                                | `currentParticipants(0)` 후 `1`로 UPDATE             | 빌더에서 처음부터 `1` (주최자 참가 row와 일치)                                 |

### High

| 항목               | 위치                                                         | 문제                                    | 개선 방향                                                   |
| ------------------ | ------------------------------------------------------------ | --------------------------------------- | ----------------------------------------------------------- |
| 전량 목록          | `getAllMeetups`, `getAvailableMeetups`, 관리자 `listMeetups` | OOM·응답 지연 위험                      | `Pageable` + DB 페이징, 관리자는 필터를 SQL로               |
| 소프트 삭제 일관성 | `findByIdWithOrganizer`, `findByIdWithDetails`               | 삭제된 모임이 단건 API로 조회될 수 있음 | `(isDeleted = false OR isDeleted IS NULL)` 통일             |
| 근처 검색 상한     | `findNearbyMeetups`                                          | LIMIT 없음 시 대량 로드                 | `LIMIT` + 필요 시 페이징                                    |
| 키워드 검색        | `findByKeyword`                                              | `LIKE %:keyword%` → 인덱스 비효율       | 접두사 검색·FULLTEXT·검색 엔진 등 (프로젝트 검색 규칙 참고) |

### Medium

| 항목                         | 위치                                      | 문제                               | 개선 방향                                                       |
| ---------------------------- | ----------------------------------------- | ---------------------------------- | --------------------------------------------------------------- |
| 참가 취소 ↔ 채팅             | `MeetupService#cancelMeetupParticipation` | 생성과 달리 채팅 직접 호출         | `MeetupLeftEvent` 등으로 대칭 분리 (실패 시 정책은 유지 가능)   |
| 컨트롤러 인증 보일러플레이트 | `MeetupController`                        | 동일 null 체크 반복                | `AuthenticationPrincipal` / 헬퍼 / AOP 등 팀 컨벤션에 맞게 축소 |
| DTO 검증                     | `MeetupDTO`, `MeetupService`              | 서비스에만 검증 로직               | `@Future` 등 Bean Validation + 그룹 검증                        |
| 상세 FETCH                   | `findByIdWithDetails`                     | 3중 `LEFT JOIN FETCH` + `DISTINCT` | 참가자 많을 때 entity graph·단계 조회·DTO 프로젝션 검토         |

### 추가 (점검에서만 언급된 항목)

| 항목             | 위치                                                                | 문제                                                             | 개선 방향                                                                     |
| ---------------- | ------------------------------------------------------------------- | ---------------------------------------------------------------- | ----------------------------------------------------------------------------- |
| 관리자 삭제      | `AdminMeetupController#deleteMeetup` → `MeetupService#deleteMeetup` | `userId`로 `"ADMIN"` 문자열 전달 시 `findByIdString` 실패 가능   | SecurityContext의 관리자 계정·또는 `deleteMeetupAsAdmin(meetupIdx)` 전용 경로 |
| 참가 도메인 규칙 | `MeetupService#joinMeetup`                                          | `CLOSED`/`COMPLETED`·과거 일시에 대한 명시적 거부 여부 확인 필요 | 비즈니스 규칙에 맞게 상태·`date` 검증 추가                                    |

---

## 3. 이미 잘 된 부분 (참고)

- **모임 생성 → 채팅방**: `MeetupCreatedEvent` + `MeetupChatRoomEventListener`, 트랜잭션 분리 주석과 구현이 일치.
- **동시성**: `incrementParticipantsIfAvailable` / `decrementParticipantsIfPositive`, 비관적 락 조회 등은 운영 수준으로 정리됨.
- **목록 일부**: `JOIN FETCH m.organizer`가 붙은 JPQL 쿼리는 주최자 N+1을 이미 방지.

---

## 4. 코드 앵커 (빠른 탐색)

| 주제                      | 클래스 / 메서드                                                               |
| ------------------------- | ----------------------------------------------------------------------------- |
| 근처 모임 네이티브 쿼리   | `SpringDataJpaMeetupRepository#findNearbyMeetups`                             |
| DTO 변환 (organizer 접근) | `MeetupConverter#toDTO`                                                       |
| 생성 이중 저장            | `MeetupService#createMeetup`                                                  |
| 소프트 삭제 단건          | `SpringDataJpaMeetupRepository#findByIdWithOrganizer`, `#findByIdWithDetails` |
| 채팅 직접 호출            | `MeetupService#cancelMeetupParticipation`                                     |
| 채팅 이벤트 분리          | `MeetupChatRoomEventListener#handleMeetupCreated`                             |
| 관리자 API                | `AdminMeetupController`                                                       |

---

## 5. 관련 문서

- 종합 요약·FE 포함: [refactoring-summary.md](./refactoring-summary.md)
- 도메인 스펙: [docs/domains/meetup.md](../../domains/meetup.md)
- Fetch 전략: [../fetch-optimization/meetup/Fetch 전략 개선 (Fetch Join vs Batch Size).md](<../fetch-optimization/meetup/Fetch%20전략%20개선%20(Fetch%20Join%20vs%20Batch%20Size).md>)

---

## 6. 구현 진행 (2026-04-12)

| 구간     | 내용                                                                                                                                                             |
| -------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Critical | 근처 모임: `findNearbyMeetupIds` + `findByIdxInWithOrganizer` + `maxResults`(기본 500) — organizer N+1 완화; 생성 시 `currentParticipants(1)` 단일 INSERT로 정리 |
| Critical | `MeetupScheduler`: 매시 정각, 정원 마감 → `CLOSED`, 일시 경과 → `COMPLETED`                                                                                      |
| High     | `findByIdWithOrganizer` / `findByIdWithDetails`에 소프트 삭제 조건 추가                                                                                          |
| High     | `GET /api/meetups` `page`/`size` 페이징, `GET /api/meetups/available` Slice(`hasNext`, `page`, `size`)                                                           |

| 미구현 | 키워드 `LIKE %` → FULLTEXT 등은 DB 스키마 변경 필요로 코드 미적용 |
