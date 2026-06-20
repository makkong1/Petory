# Activity 도메인

> 기준 코드: `domain/activity`, `frontend/src/components/Activity/ActivityPage.js`, `frontend/src/api/activityApi.js`.

Activity 도메인은 사용자별 활동을 별도 테이블에 저장하지 않고, Care, Board, MissingPet의 게시글/댓글 데이터를 읽어서 **하나의 타임라인 DTO**로 합성하는 읽기 전용 도메인이다.

---

## 1. 책임과 범위

| 구분 | 내용 |
| --- | --- |
| 통합 조회 | 케어 요청, 일반 게시글, 실종 제보, 각 댓글을 한 목록으로 합친다. |
| 정렬 | `ActivityDTO.createdAt` 기준 최신순 정렬. null은 마지막으로 보낸다. |
| 필터 | `ALL`, `POSTS`, `COMMENTS`, `REVIEWS` 필터를 제공한다. |
| 페이징 | 전체 활동을 메모리에 모은 뒤 `PageImpl`로 응답 형태를 맞춘다. |
| 화면 연결 | 프론트 `ActivityPage`에서 날짜별 그룹화, 카드 렌더링, 클릭 이동을 처리한다. |

범위 밖:

- Activity 전용 엔티티/테이블은 없다.
- 활동 생성 이벤트를 저장하는 append-only activity log 구조가 아니다.
- Location review는 DTO 타입과 필터만 준비되어 있고 현재 백엔드 수집 경로가 없다.
- Chat, Payment, Meetup 참여, File 업로드, Notification은 현재 Activity 타임라인에 포함되지 않는다.

---

## 2. 코드 위치

```text
backend/main/java/com/linkup/Petory/domain/activity/
  controller/
    ActivityController.java
  service/
    ActivityService.java
  converter/
    ActivityConverter.java
  dto/
    ActivityDTO.java
    ActivityPageResponseDTO.java

frontend/src/
  api/activityApi.js
  components/Activity/ActivityPage.js
```

---

## 3. API

`ActivityController`의 base path는 `/api/activities`다. `SecurityConfig`의 `/api/**` 인증 규칙 때문에 로그인 사용자가 필요하다.

| Method | URL | 파라미터 | 응답 | 현재 프론트 사용 |
| --- | --- | --- | --- | --- |
| GET | `/my` | `userId` 필수 | `List<ActivityDTO>` | 미사용, 하위 호환용 |
| GET | `/my/paging` | `userId` 필수, `filter=ALL`, `page=0`, `size=20` | `ActivityPageResponseDTO` | 사용 |

보안상 주의:

- API는 `userId`를 query parameter로 받는다.
- 현재 컨트롤러/서비스는 JWT 주체와 `userId`가 같은지 검증하지 않는다.
- 프론트는 `useAuth().user.idx`를 넘기지만, 백엔드에서 강제하지는 않는다.

---

## 4. 서비스 흐름

### getUserActivities

1. `UsersRepository.findById(userId)`로 사용자 row를 찾는다.
2. 6개 repository에서 사용자의 삭제되지 않은 활동을 조회한다.
3. 각 엔티티를 `ActivityConverter`로 `ActivityDTO`로 바꾼다.
4. `createdAt` 내림차순으로 정렬한다.

수집 대상:

| 원천 도메인 | Repository | Activity type |
| --- | --- | --- |
| Care | `CareRequestRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc` | `CARE_REQUEST` |
| Board | `BoardRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc` | `BOARD` |
| MissingPet | `MissingPetBoardRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc` | `MISSING_PET` |
| Care comment | `CareRequestCommentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc` | `CARE_COMMENT` |
| Board comment | `CommentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc` | `COMMENT` |
| MissingPet comment | `MissingPetCommentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc` | `MISSING_COMMENT` |

### getUserActivitiesWithPaging

1. `getUserActivities()`로 전체 활동을 먼저 수집한다.
2. `filterActivities()`로 필터를 적용한다.
3. 필터 전 전체 목록을 순회하며 `allCount`, `postsCount`, `commentsCount`, `reviewsCount`를 계산한다.
4. `PageRequest.of(page, size)`와 `subList`로 현재 페이지를 자른다.
5. `ActivityPageResponseDTO`로 감싸서 반환한다.

---

## 5. 필터 규칙

| filter | 포함 type |
| --- | --- |
| `ALL` 또는 null | 전체 |
| `POSTS` | `CARE_REQUEST`, `BOARD`, `MISSING_PET` |
| `COMMENTS` | `CARE_COMMENT`, `COMMENT`, `MISSING_COMMENT` |
| `REVIEWS` | `LOCATION_REVIEW` |
| 기타 문자열 | 전체 |

현재 `LOCATION_REVIEW`를 수집하는 repository 호출이 없으므로 `REVIEWS` 결과와 `reviewsCount`는 일반적으로 0이다.

---

## 6. DTO

### ActivityDTO

| 필드 | 설명 |
| --- | --- |
| `idx` | 원천 엔티티 PK |
| `type` | 활동 타입 문자열 |
| `title` | 게시글/요청 계열 제목. 댓글은 null |
| `content` | 본문 또는 댓글 내용 |
| `createdAt` | 정렬과 날짜 그룹화 기준 |
| `status` | 원천 상태. 댓글 일부는 `ACTIVE`/`DELETED` 문자열 |
| `deleted` | 원천 row 삭제 여부 |
| `deletedAt` | 삭제 시각 |
| `relatedId` | 댓글일 때 관련 게시글/요청 ID |
| `relatedTitle` | 댓글일 때 관련 게시글/요청 제목 |

### ActivityPageResponseDTO

| 필드 | 설명 |
| --- | --- |
| `activities` | 현재 페이지 활동 목록 |
| `totalCount` | 현재 필터 기준 전체 개수 |
| `totalPages` | 현재 필터 기준 전체 페이지 수 |
| `currentPage` | 현재 페이지 번호 |
| `pageSize` | 페이지 크기 |
| `hasNext` / `hasPrevious` | 페이지 이동 가능 여부 |
| `allCount` | 필터 적용 전 전체 개수 |
| `postsCount` | 게시글 계열 개수 |
| `commentsCount` | 댓글 계열 개수 |
| `reviewsCount` | 리뷰 계열 개수. 현재 수집 경로 없음 |

---

## 7. Converter 매핑

| 원천 엔티티 | type | title | content | status | related |
| --- | --- | --- | --- | --- | --- |
| `CareRequest` | `CARE_REQUEST` | `title` | `description` | `status.name()` | 없음 |
| `Board` | `BOARD` | `title` | `content` | `status.name()` | 없음 |
| `MissingPetBoard` | `MISSING_PET` | `title` | `content` | `status.name()` | 없음 |
| `CareRequestComment` | `CARE_COMMENT` | null | `content` | `ACTIVE` 또는 `DELETED` | `careRequest.idx`, `careRequest.title` |
| `Comment` | `COMMENT` | null | `content` | `status.name()` | `board.idx`, `board.title` |
| `MissingPetComment` | `MISSING_COMMENT` | null | `content` | `ACTIVE` 또는 `DELETED` | `board.idx`, `board.title` |

상태 표현은 완전히 통일되어 있지 않다. 게시글 계열은 enum name을 쓰고, 케어/실종 댓글은 삭제 여부로 `ACTIVE`/`DELETED`를 만든다.

---

## 8. 프론트 동작

`ActivityPage`는 `activityApi.getMyActivitiesWithPaging()`만 사용한다.

화면 처리:

- `user.idx`를 `userId`로 전달한다.
- 서버 응답을 `{ map, order }` 형태로 저장한다.
- 날짜별로 그룹화해서 타임라인 형태로 보여준다.
- 필터 버튼은 서버가 내려준 count를 표시한다.
- 페이지 이동은 `PageNavigation` 컴포넌트로 처리한다.

카드 클릭 이동:

| type | 이동 |
| --- | --- |
| `BOARD`, `COMMENT` | `community` 탭으로 이동 후 `openBoardDetail` 이벤트 발행 |
| `CARE_REQUEST`, `CARE_COMMENT` | `unified-map` 탭으로 이동 |
| `MISSING_PET`, `MISSING_COMMENT` | `missing-pets` 탭으로 이동 후 `openMissingPetDetail` 이벤트 발행 |
| `LOCATION_REVIEW` | `unified-map` 탭으로 이동 후 `openLocationReviewDetail` 이벤트 발행 |

---

## 9. 현재 한계

1. 백엔드 페이징은 DB 페이징이 아니라 전체 활동을 메모리에 로드한 뒤 잘라낸다.
2. `userId` query parameter와 인증 주체의 일치 여부를 검증하지 않는다.
3. `REVIEWS` 필터와 `LOCATION_REVIEW` 타입은 준비되어 있지만 실제 수집 경로가 없다.
4. Activity 전용 저장소가 없어 특정 시점의 활동 스냅샷이나 삭제된 활동 이력 보존은 불가능하다.
5. 원천 repository 호출이 6개로 고정되어 있어 활동 타입이 늘어날수록 서비스가 커진다.
6. 로그 메시지가 문자열 결합 방식의 `log.info`로 남아 있어 placeholder 방식으로 정리할 여지가 있다.

---

## 10. 관련 문서

- `docs/architecture/activity/사용자 활동 타임라인 아키텍처.md`
- `docs/domains/board.md`
- `docs/domains/care.md`
- `docs/domains/missingpet.md`
- `docs/domains/user.md`
