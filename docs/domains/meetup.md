# Meetup 도메인

> 기준: 현재 코드를 단일 진실로 본다. 이 문서는 산책/오프라인 모임, 참가자 관리, 위치 기반 조회, 모임 채팅방 연동을 다룬다.

## 1. 범위

Meetup 도메인은 사용자가 오프라인 모임을 만들고, 다른 사용자가 참가/취소하며, 모임 채팅방과 상태 전이를 통해 모임 생명주기를 관리하는 도메인이다.

포함 범위:

- 모임 목록/상세/생성/수정/삭제
- 참여 가능한 모임 조회
- 반경 기반 근처 모임 조회
- 홈 화면 모임 추천
- 키워드 검색
- 주최자별 모임 조회
- 참가자 목록 조회
- 모임 참가/참가 취소
- 참가 여부 확인
- 모임 히스토리 좋아요
- 모임 상태 자동 전이
- 모임 생성 후 그룹 채팅방 자동 생성
- 채팅방 없는 모임 복구
- 관리자 모임 조회/삭제/참가자 조회

비범위:

- 채팅 메시지 송수신
- 사용자 프로필/펫 관리
- 실제 지도 UI 구현
- 알림 전송
- 결제/정산

## 2. 주요 코드

| 구분 | 주요 파일 |
|---|---|
| 사용자 API | `backend/main/java/com/linkup/Petory/domain/meetup/controller/MeetupController.java` |
| 관리자 API | `backend/main/java/com/linkup/Petory/domain/admin/controller/AdminMeetupController.java` |
| 관리자 facade | `backend/main/java/com/linkup/Petory/domain/admin/service/AdminCareAndMeetupFacade.java` |
| 모임 서비스 | `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupService.java` |
| 상태 전이 스케줄러 | `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupScheduler.java` |
| 채팅방 이벤트 리스너 | `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupChatRoomEventListener.java` |
| 채팅방 생성 서비스 | `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupChatRoomCreationService.java` |
| 채팅방 복구 스케줄러 | `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupChatRoomRecoveryScheduler.java` |
| 모임 repository | `backend/main/java/com/linkup/Petory/domain/meetup/repository/SpringDataJpaMeetupRepository.java` |
| 참가자 repository | `backend/main/java/com/linkup/Petory/domain/meetup/repository/SpringDataJpaMeetupParticipantsRepository.java` |
| 프론트 사용자 API | `frontend/src/api/meetupApi.js` |
| 프론트 관리자 API | `frontend/src/api/meetupAdminApi.js` |

## 3. 핵심 엔티티

### Meetup

| 필드 | 의미 |
|---|---|
| `idx` | 모임 PK |
| `title`, `description` | 제목/설명 |
| `location` | 장소 주소 |
| `latitude`, `longitude` | 위치 좌표 |
| `date` | 모임 일시 |
| `organizer` | 주최자 |
| `maxParticipants` | 최대 참여 인원, 기본 10 |
| `currentParticipants` | 현재 참여 인원 |
| `status` | `RECRUITING`, `CLOSED`, `COMPLETED`, `CANCELLED` |
| `isDeleted`, `deletedAt` | soft delete 상태 |
| `participants` | 참가자 목록 |

생성 시 서비스에서 `currentParticipants=1`, `status=RECRUITING`으로 저장하고 주최자를 참가자로 추가한다.

### MeetupParticipants

모임 참가자 엔티티다.

| 필드 | 의미 |
|---|---|
| `meetup` | 대상 모임 |
| `user` | 참가자 |
| `joinedAt` | 참가 시각 |
| `liked` | 내 모임 히스토리 좋아요 여부 |

`meetup + user` 복합 PK를 사용한다. 중복 참가의 최종 방어선 역할도 한다.

## 4. 사용자 API

`MeetupController`는 클래스 단위로 `@PreAuthorize("isAuthenticated()")`가 적용되어 있다.

### `/api/meetups`

| API | 설명 |
|---|---|
| `POST /api/meetups` | 모임 생성 |
| `PUT /api/meetups/{meetupIdx}` | 모임 수정 |
| `DELETE /api/meetups/{meetupIdx}` | 모임 soft delete |
| `GET /api/meetups?page&size` | 전체 모임 페이징 조회 |
| `GET /api/meetups/{meetupIdx}` | 모임 상세 조회 |
| `GET /api/meetups/search?keyword` | 키워드 검색 |
| `GET /api/meetups/available?page&size` | 참여 가능한 모임 Slice 조회 |
| `GET /api/meetups/organizer/{organizerIdx}` | 주최자별 모임 조회 |
| `GET /api/meetups/nearby?lat&lng&radius&maxResults` | 반경 기반 근처 모임 조회 |
| `GET /api/meetups/home?lat&lng&size` | 홈 화면 모임 추천 |
| `GET /api/meetups/{meetupIdx}/participants` | 참가자 목록 |
| `POST /api/meetups/{meetupIdx}/participants` | 모임 참가 |
| `DELETE /api/meetups/{meetupIdx}/participants` | 모임 참가 취소 |
| `GET /api/meetups/{meetupIdx}/participants/check` | 내 참가 여부와 liked 조회 |
| `PATCH /api/meetups/{meetupIdx}/history/like?liked=true` | 내 모임 히스토리 좋아요 변경 |

응답은 대부분 `{ meetups, count, ... }`, `{ meetup }`, `{ participant }`, `{ history }` 형태의 map 응답이다.

## 5. 모임 생성

생성 흐름:

1. authentication name으로 사용자 조회
2. 이메일 인증 확인
3. 모임 일시가 과거인지 검증
4. `maxParticipants`가 없으면 10 사용
5. `currentParticipants=1`, `status=RECRUITING`으로 모임 저장
6. 주최자를 `MeetupParticipants`에 자동 저장
7. 트랜잭션 커밋 후 `MeetupCreatedEvent` 발행
8. 비동기 리스너가 그룹 채팅방 생성

이메일 인증 purpose:

- `MEETUP`

채팅방 생성은 모임 생성 트랜잭션과 분리된다. 채팅방 생성 실패가 모임 생성을 롤백하지 않는다.

## 6. 수정과 삭제

수정:

- 주최자 또는 `ADMIN`/`MASTER`만 가능하다.
- 제목, 설명, 위치, 좌표, 일시, 최대 인원을 부분 수정한다.
- 일시는 현재 이후여야 한다.
- `maxParticipants < 1`이면 거절한다.
- `maxParticipants < currentParticipants`이면 거절한다.

삭제:

- 주최자 또는 `ADMIN`/`MASTER`만 가능하다.
- soft delete로 `isDeleted=true`, `deletedAt=now` 처리한다.

관리자 전용 삭제는 `deleteMeetupForAdmin()`을 사용해 사용자 검증 없이 soft delete한다.

## 7. 조회와 검색

### 전체 목록

`getAllMeetups(Pageable)`은 `findAllNotDeleted(pageable)`을 사용한다.

조건:

- soft delete 제외
- 일반 사용자 조회에서는 `CANCELLED` 제외

조회 방식:

- `@EntityGraph(attributePaths = "organizer")`로 주최자 N+1을 줄인다.

### 상세

`getMeetupById()`는 `findByIdWithDetails()`를 사용한다.

포함:

- 주최자
- 참가자
- 참가자 사용자
- 일반 사용자 상세 조회에서는 `CANCELLED` 제외

### 참여 가능한 모임

`getAvailableMeetups(Pageable)`은 count 쿼리 없이 `Slice`로 반환한다.

조건:

- `date > now`
- `status = RECRUITING`
- `currentParticipants < maxParticipants`
- soft delete 제외

정렬:

- `date ASC`

### 키워드 검색

`searchMeetupsByKeyword(keyword)`는 FULLTEXT 기반 검색을 사용한다.

흐름:

1. native query로 title/description FULLTEXT 검색 후 id 목록 조회
2. id 목록으로 주최자를 fetch join해 재조회
3. 결과가 `MAX_LIST_SIZE=500`을 넘으면 잘라낸다.

일반 키워드 검색은 `CANCELLED` 모임을 제외한다.

### 주최자별 조회

`getMeetupsByOrganizer(organizerIdx)`는 주최자 id로 soft delete 제외 목록을 조회한다. 결과가 500개를 넘으면 잘라낸다.

## 8. 위치 기반 조회와 홈 추천

### 근처 모임 조회

`GET /api/meetups/nearby`

파라미터:

- `lat`
- `lng`
- `radius`, 기본 5.0km
- `maxResults`, 기본 500

서비스 정책:

- maxResults는 1~1000으로 보정한다.
- `date > now`
- `status`가 `COMPLETED` 또는 `CANCELLED`가 아니거나 status null
- soft delete 제외
- 좌표가 있는 모임만 포함

쿼리:

- `geo_point` 공간 컬럼
- `ST_Within` bounding polygon
- `ST_Distance_Sphere` 반경 검증
- 거리 오름차순, 날짜 오름차순 정렬
- id만 조회한 뒤 `findByIdxInWithOrganizer(ids)`로 주최자 fetch
- id 순서를 유지해 DTO 변환

응답 DTO의 `distance`는 미터 단위다.

### 홈 추천

`GET /api/meetups/home`

좌표가 없으면 참여 가능한 모임을 `date ASC`로 조회한다.

좌표가 있으면:

1. 50km 반경 근처 모임을 `size * 3`개 후보로 조회한다.
2. `RECRUITING` 상태만 남긴다.
3. 거리, 날짜 긴급도, 남은 정원 점수를 계산한다.
4. `score = 0.4 * distScore + 0.4 * urgencyScore + 0.2 * capacityScore`로 정렬한다.
5. 결과가 없으면 참여 가능한 모임 fallback을 사용한다.

점수:

```text
distScore     = max(0, 1 - distKm / 50)
urgencyScore  = max(0, 1 - daysUntil / 30)
capacityScore = 1 - currentParticipants / maxParticipants
```

## 9. 참가와 취소

### 참가

`joinMeetup(meetupIdx, userId)` 흐름:

1. 모임을 `findByIdWithLock()`으로 비관적 락 조회
2. 사용자 조회
3. 이메일 인증 확인
4. 중복 참가 검사
5. 주최자가 아니면 `incrementParticipantsIfAvailable()`로 원자적 인원 증가
6. 증가 실패 시 status가 모집 중이 아니면 `meetupNotRecruiting`, 아니면 `fullCapacity`
7. `entityManager.refresh(meetup)`으로 영속성 컨텍스트 동기화
8. 참가자 row 저장
9. PK 충돌이 발생하면 증가분을 원자적으로 감소시키고 `alreadyJoined` 반환

동시성 방어:

- 모임 row 비관적 락
- 조건부 원자적 UPDATE
- 참가자 복합 PK
- PK 충돌 시 증가분 보정

주의:

- `joinMeetup()` 자체는 채팅방 입장을 호출하지 않는다.
- 산책모임 채팅 입장은 Chat API `POST /api/chat/conversations/meetup/{meetupIdx}/join` 경로에서 처리된다.

### 참가 취소

`cancelMeetupParticipation(meetupIdx, userId)` 흐름:

1. 모임과 주최자 조회
2. 사용자 조회
3. 주최자면 취소 불가
4. 참가자 row 조회
5. 참가자 삭제
6. `decrementParticipantsIfPositive()`로 원자적 인원 감소
7. `conversationService.leaveMeetupChat(meetupIdx, userIdx)` 호출
8. 채팅방 나가기 실패는 로그만 남기고 참가 취소는 성공 처리

## 10. 모임 히스토리와 좋아요

참가자는 `MeetupParticipants.liked` 값을 가진다.

기능:

- `checkParticipation`: 내 참가 여부와 liked 상태 조회
- `updateMyMeetupLike`: 참가 row의 liked 값 변경
- `getMeetupHistory`: 사용자의 참여/주최 이력을 조회해 `MeetupHistoryDTO`로 변환

주최자는 모임 생성 시 참가자로 저장되므로 히스토리에서 `ORGANIZER`로 표현된다.

## 11. 상태 전이

상태:

- `RECRUITING`
- `CLOSED`
- `COMPLETED`
- `CANCELLED`

`MeetupScheduler.transitionMeetupStatuses()`가 매시 정각 실행된다.

전이:

- 정원이 찬 `RECRUITING` 모임이고 `date >= now`이면 `CLOSED`
- `date < now`이고 아직 `COMPLETED`가 아니며 `CANCELLED`도 아니면 `COMPLETED`

전이는 bulk update로 처리된다.

`CANCELLED`는 주최자 제재 등으로 취소된 모임을 나타내며, 스케줄러가 이후 `COMPLETED`로 덮어쓰지 않는다.

## 12. 채팅방 연동

### 생성

모임 생성 후:

1. `TransactionSynchronization.afterCommit()`에서 `MeetupCreatedEvent` 발행
2. `MeetupChatRoomEventListener`가 `@Async`로 이벤트 수신
3. `MeetupChatRoomCreationService.createChatRoom()` 호출
4. `ConversationCreatorService.createConversation(ConversationType.MEETUP, RelatedType.MEETUP, ...)`
5. 주최자 participant role을 `ADMIN`으로 설정

채팅방 생성은 최대 3회 retry된다. retry 소진 시 `@Recover`에서 로그를 남긴다.

### 복구

`MeetupChatRoomRecoveryScheduler`가 5분마다 실행된다.

흐름:

1. `findWithoutChatRoom()`으로 채팅방 없는 모임 조회
2. 각 모임에 대해 `createChatRoom()` 재시도

### 참가/나가기

- 참가 시 Meetup 도메인은 채팅방 입장을 직접 호출하지 않는다.
- 참가 취소 시 Meetup 도메인은 채팅방 나가기를 시도한다.
- 채팅방 나가기 실패는 참가 취소를 막지 않는다.

## 13. 관리자 API

### `/api/admin/meetups`

`ADMIN`, `MASTER` 접근 가능.

| API | 설명 |
|---|---|
| `GET /api/admin/meetups?status&q&page&size` | 관리자 모임 페이징 조회 |
| `GET /api/admin/meetups/{id}` | 관리자 단건 조회 |
| `DELETE /api/admin/meetups/{id}` | 관리자 soft delete |
| `GET /api/admin/meetups/{id}/participants` | 참가자 목록 조회 |

관리자 목록:

- q가 없으면 status 필터 JPQL
- q가 있으면 title/description FULLTEXT + location LIKE
- soft delete 제외

관리자 삭제는 `AdminCareAndMeetupFacade`를 거쳐 감사 로그를 남긴다.

## 14. 도메인 간 연결

User:

- 주최자, 참가자.
- 생성/참가 시 이메일 인증.

Chat:

- 모임 생성 후 그룹 채팅방 자동 생성.
- 참가 취소 시 채팅방 나가기.
- 채팅방 없는 모임 복구.

Admin:

- 목록/삭제/참가자 조회와 감사 로그.

Statistics:

- 모임 생성 수, 참가 수 등 통계 집계에서 repository count 메서드를 사용한다.

## 15. 제재 정책 (2026-06-28~)

> 코드 기준: `MeetupService`, `UserSanctionMeetupEventListener`, `MeetupStatus`, `SpringDataJpaMeetupRepository`

### 실시간 차단 (요청 진입 시점)

| 시점 | 적용 대상 | 동작 |
|------|-----------|------|
| `POST /api/meetups` (모임 생성) | SUSPENDED·BANNED 사용자 | `MeetupForbiddenException.sanctioned()` (403) |
| `POST /api/meetups/{id}/participants` (모임 참가) | SUSPENDED·BANNED 사용자 | `MeetupForbiddenException.sanctioned()` (403) |

### 제재 이벤트 후속 처리 (`UserSanctionAppliedEvent`)

- **SUSPENDED·BANNED 모두** 이벤트 리스너(`UserSanctionMeetupEventListener`)가 실행된다.
- `AFTER_COMMIT` 단계에서 `REQUIRES_NEW` 트랜잭션으로 실행된다.

**주최자 처리:**
- 해당 사용자가 주최한 `RECRUITING` 상태 모임을 모두 `CANCELLED`로 변경한다.
- `MeetupStatus.CANCELLED`는 제재로 인한 취소를 나타내는 전용 상태다.

**참가자 처리:**
- 해당 사용자의 취소되지 않은 진행 예정 모임 참가 row만 대상으로 한다.
- 주최자 row는 주최자 모임 취소 정책으로 처리하고, 참가 취소 대상에서는 제외한다.
- 참가 row를 삭제하고 `decrementParticipantsIfPositive()`로 인원 수를 원자적으로 감소시킨다.
- 이후 모임 채팅방에서 `leaveMeetupChat()`을 시도한다.
- 채팅 퇴장 실패는 로그만 남기고 참가 취소는 유지한다.
- 과거 모임 히스토리 row는 삭제하지 않는다.

## 16. 한계와 개선

- 참가 API와 채팅방 입장 API가 분리되어 있어 클라이언트가 둘 다 호출해야 한다.
- 모임 생성 성공 후 채팅방 생성이 최종 실패해도 모임은 유지된다. 복구 스케줄러가 있지만 즉시 일관성은 아니다.
- `getHomeMeetups()`의 최종 점수 계산은 애플리케이션에서 수행한다.
- 키워드 검색과 주최자별 조회는 500개 상한으로 잘라낸다. 완전한 페이징 API로 전환 여지가 있다.
- 참가 취소는 채팅방 나가기 실패를 롤백하지 않는다.
- `CLOSED` 상태가 된 모임에서 참가자가 취소해 정원이 비어도 자동으로 `RECRUITING`으로 되돌리는 로직은 없다.
- 제재로 `CANCELLED`된 모임은 일반 사용자 조회에서 제외되지만 관리자 조회에서는 상태 필터로 확인할 수 있다.

## 17. 관련 문서

- [산책 & 오프라인 모임 아키텍처](../architecture/meetup/산책 & 오프라인 모임 아키텍처.md)
- [Meetup 백엔드 성능 최적화](../refactoring/meetup/meetup-backend-performance-optimization.md)
- [Meetup 참가자 Race Condition](../troubleshooting/meetup/race-condition-participants.md)
- [Meetup N+1 쿼리 이슈](../troubleshooting/meetup/n-plus-one-query-issue.md)
- [Meetup 채팅방 복구 스케줄러 N+1](../refactoring/meetup/recovery-scheduler-n-plus-one.md)
- [근처 모임 인덱스 분석](../refactoring/meetup/nearby-meetups/index-analysis.md)
