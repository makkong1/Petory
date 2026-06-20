# Board 도메인

> 기준: 현재 코드를 단일 진실로 본다. 이 문서는 일반 커뮤니티 게시판과 일반 댓글만 다룬다. `MissingPetBoard`, `MissingPetComment`는 같은 `domain/board` 패키지에 있어도 실종 제보 도메인 문서에서 별도로 다룬다.

## 1. 범위

Board 도메인은 Petory 커뮤니티 게시글, 일반 댓글, 게시글/댓글 반응, 조회수, 인기글 스냅샷, 관리자 모더레이션을 담당한다.

포함 범위:

- 커뮤니티 게시글 목록/상세/작성/수정/삭제
- 카테고리 필터링
- 제목+내용 FULLTEXT 검색
- 작성자 닉네임 검색
- 일반 댓글 목록/작성/삭제
- 게시글/댓글 좋아요·싫어요 토글
- 게시글 조회수 중복 방지
- 인기글 스냅샷 생성/조회
- 첨부파일 단일/배치 조회 연동
- 댓글 작성 알림 발송
- 관리자 게시글/댓글 블라인드·삭제·복구

비범위:

- 실종 제보 게시글/댓글
- Care 요청 댓글
- 신고 생성/처리
- 알림 전송 인프라 자체
- 파일 저장소 구현 자체

## 2. 주요 코드

| 구분 | 주요 파일 |
|---|---|
| 사용자 API | `backend/main/java/com/linkup/Petory/domain/board/controller/BoardController.java` |
| 관리자 API | `backend/main/java/com/linkup/Petory/domain/admin/controller/AdminBoardController.java` |
| 게시글 서비스 | `backend/main/java/com/linkup/Petory/domain/board/service/BoardService.java` |
| 댓글 서비스 | `backend/main/java/com/linkup/Petory/domain/board/service/CommentService.java` |
| 반응 서비스 | `backend/main/java/com/linkup/Petory/domain/board/service/ReactionService.java` |
| 인기글 서비스 | `backend/main/java/com/linkup/Petory/domain/board/service/BoardPopularityService.java` |
| 인기글 스케줄러 | `backend/main/java/com/linkup/Petory/domain/board/service/BoardPopularityScheduler.java` |
| 게시글 repository | `backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaBoardRepository.java` |
| 댓글 repository | `backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaCommentRepository.java` |
| 프론트 게시글 API | `frontend/src/api/boardApi.js` |
| 프론트 댓글 API | `frontend/src/api/commentApi.js` |
| 프론트 관리자 API | `frontend/src/api/communityAdminApi.js` |

## 3. 핵심 엔티티

### Board

주요 필드:

| 필드 | 의미 |
|---|---|
| `idx` | 게시글 PK |
| `user` | 작성자 |
| `title` | 제목 |
| `content` | 본문 |
| `category` | 카테고리 |
| `status` | `ACTIVE`, `BLINDED`, `DELETED` 등 `ContentStatus` |
| `viewCount` | 조회 수 캐시 |
| `likeCount` | 좋아요 수 캐시 |
| `dislikeCount` | 싫어요 수 캐시 |
| `commentCount` | 댓글 수 캐시 |
| `lastReactionAt` | 마지막 반응 추가/변경 시각 |
| `isDeleted`, `deletedAt` | soft delete 상태 |

삭제는 soft delete이며 `status=DELETED`, `isDeleted=true`, `deletedAt=now`로 처리한다.

### Comment

일반 커뮤니티 댓글이다.

주요 필드:

- `board`
- `user`
- `content`
- `status`
- `isDeleted`
- `deletedAt`

댓글도 soft delete를 사용한다. 게시글 삭제 시 해당 게시글의 활성 댓글을 bulk update로 함께 soft delete한다.

### BoardReaction / CommentReaction

게시글/댓글의 좋아요·싫어요 반응이다.

반응 타입:

- `LIKE`
- `DISLIKE`

같은 사용자가 같은 대상에 같은 반응을 다시 누르면 취소된다. 다른 반응을 누르면 타입이 변경된다.

### BoardViewLog

로그인 사용자의 게시글 조회 기록이다. 같은 사용자가 같은 게시글을 반복 조회해도 조회수가 계속 증가하지 않게 한다.

비로그인 또는 viewerId가 없는 조회는 항상 조회수 증가 대상으로 처리된다.

### BoardPopularitySnapshot

인기글 스냅샷이다. 주간/월간 기간, ranking, popularityScore, like/comment/view count를 저장한다.

## 4. 사용자 게시글 API

### `/api/boards`

| API | 인증 | 설명 |
|---|---|---|
| `GET /api/boards?category&page&size` | permitAll annotation | 게시글 목록 페이징 조회 |
| `GET /api/boards/{id}?viewerId` | permitAll annotation | 게시글 상세 조회, 조회수 증가 처리 |
| `GET /api/boards/popular?period=WEEKLY` | permitAll annotation | 인기글 스냅샷 조회 |
| `POST /api/boards` | 인증 필요 | 게시글 생성 |
| `PUT /api/boards/{id}` | 인증 필요 | 게시글 수정 |
| `DELETE /api/boards/{id}` | 인증 필요 | 게시글 soft delete |
| `GET /api/boards/my-posts?userId` | 인증 필요 | 특정 사용자 게시글 목록 |
| `GET /api/boards/search?keyword&searchType&page&size` | permitAll annotation | 게시글 검색 |

주의:

- 컨트롤러에는 `@PreAuthorize("permitAll()")`가 붙어 있지만, `SecurityConfig`의 `/api/**` 인증 정책 때문에 실제 접근 가능 여부는 보안 설정도 함께 봐야 한다.
- 생성은 현재 로그인 사용자의 `SecurityContext` subject를 사용한다. DTO의 userId를 신뢰하지 않는다.

## 5. 댓글 API

댓글은 별도 최상위 경로가 아니라 게시글 하위 경로에 있다.

| API | 인증 | 설명 |
|---|---|---|
| `GET /api/boards/{boardId}/comments?page&size` | permitAll annotation | 댓글 목록 페이징 조회 |
| `POST /api/boards/{boardId}/comments` | 인증 필요 | 댓글 작성 |
| `DELETE /api/boards/{boardId}/comments/{commentId}` | 인증 필요 | 댓글 soft delete |

서비스에는 `updateComment()`도 구현되어 있지만, 현재 사용자용 `BoardController`에는 댓글 수정 endpoint가 노출되어 있지 않다. 관리자용 상태 변경/복구 API는 별도 경로에 있다.

댓글 작성 흐름:

1. 게시글 존재 확인
2. 현재 로그인 사용자 조회
3. 댓글 저장
4. 게시글 `commentCount`를 DB update로 `+1`
5. 댓글 첨부파일이 있으면 File 도메인과 동기화
6. 댓글 작성자가 게시글 작성자와 다르면 `BOARD_COMMENT` 알림 생성
7. 반응 수와 첨부파일을 포함한 DTO 반환

댓글 삭제 흐름:

1. 게시글 존재 확인
2. 댓글 존재 확인
3. 댓글이 해당 게시글에 속하는지 확인
4. 작성자 또는 관리자 권한 확인
5. 댓글 작성자의 이메일 인증 여부 확인
6. soft delete
7. 게시글 `commentCount`를 DB update로 `-1`

## 6. 반응 API

| API | 인증 | 설명 |
|---|---|---|
| `POST /api/boards/{boardId}/reactions` | 인증 필요 | 게시글 좋아요/싫어요 토글 |
| `POST /api/boards/{boardId}/comments/{commentId}/reactions` | 인증 필요 | 댓글 좋아요/싫어요 토글 |

요청 body는 `ReactionRequest`이며 `userId`, `reactionType`을 포함한다.

게시글 반응 정책:

- 같은 반응 재클릭: 기존 반응 삭제, userReaction은 null.
- 다른 반응 클릭: 기존 반응 타입 변경.
- 신규 반응: insert.
- 신규 insert는 `insertIgnore`를 사용해 중복 insert 경쟁을 완화한다.
- 게시글 `likeCount`, `dislikeCount`는 DB 원자적 update로 delta 조정한다.
- 반응 추가/변경 시 `lastReactionAt`을 업데이트한다.
- 반응 취소 시 `lastReactionAt`은 유지한다.

댓글 반응 정책:

- 게시글 반응과 같은 토글 모델이다.
- 댓글에는 현재 별도 count cache 필드가 없으므로 응답 summary는 reaction repository count로 만든다.
- insert 경쟁 완화를 위해 `insertIgnore`를 사용한다.

## 7. 조회수

상세 조회 `GET /api/boards/{id}`에서 조회수를 처리한다.

정책:

- `viewerId`가 없으면 조회수를 증가시킨다.
- `viewerId`가 있으면 `BoardViewLog`에 `boardId + userId` 기록을 `insertIgnore`로 넣는다.
- insert 성공 시에만 `board.viewCount`를 DB update로 증가시킨다.
- 이미 조회한 사용자면 조회수를 증가시키지 않는다.
- 응답 DTO에는 증가가 반영된 조회수를 보정해서 내려준다.

## 8. 검색

### `TITLE_CONTENT`

기본 검색 타입이다.

쿼리:

```sql
MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE)
```

조건:

- 삭제되지 않은 게시글
- 삭제되지 않은 작성자
- `ACTIVE` 상태 작성자

정렬:

- relevance 내림차순
- `created_at` 내림차순

### `NICKNAME`

작성자 닉네임 검색이다.

쿼리:

```jpql
u.nickname LIKE :nickname%
```

앞쪽 와일드카드를 쓰지 않는 접두사 검색이다. 작성자 JOIN과 DB 페이징을 사용한다.

## 9. 목록/상세 매핑 최적화

게시글 목록과 검색 결과는 `mapBoardsWithReactionsBatch()`를 사용한다.

배치 매핑:

1. 게시글 ID 목록 추출
2. 게시글별 reaction count를 IN 배치 조회
3. 게시글 첨부파일을 File 도메인에서 배치 조회
4. DTO에 like/dislike/attachments/primary file URL 적용

반응 count 조회는 500개 단위로 나눈다.

댓글 목록은 `CommentService.getCommentsWithPaging()`에서 다음을 배치 조회한다.

- 댓글 첨부파일
- 댓글별 reaction count

댓글 reaction count 역시 500개 단위로 나눈다.

## 10. 작성/수정/삭제 권한

게시글:

- 생성: 인증 사용자.
- 수정/삭제: 작성자 또는 `ADMIN`/`MASTER`.
- 수정/삭제 시 작성자의 `emailVerified`가 true여야 한다.

댓글:

- 생성: 인증 사용자.
- 수정/삭제: 작성자 또는 `ADMIN`/`MASTER`.
- 수정/삭제 시 작성자의 `emailVerified`가 true여야 한다.

이메일 인증 purpose:

- 게시글 수정/삭제: `BOARD_EDIT`
- 댓글 수정/삭제: `COMMENT_EDIT`

## 11. 첨부파일

Board/Comment는 File 도메인의 `AttachmentFileService`와 연동한다.

게시글:

- 생성/수정 요청의 `boardFilePath`가 있으면 `FileTargetType.BOARD` 단일 첨부로 동기화한다.
- 목록/상세 DTO에는 batch 조회된 attachments와 primary file URL이 포함된다.

댓글:

- 작성/수정 요청의 `commentFilePath`가 있으면 `FileTargetType.COMMENT` 단일 첨부로 동기화한다.
- 댓글 목록 DTO에는 batch 조회된 attachments와 primary file URL이 포함된다.

## 12. 인기글 스냅샷

인기글은 `BoardPopularityService`와 `BoardPopularityScheduler`가 담당한다.

대상:

- 기본 대상 카테고리는 `"자랑"`이다.
- 레거시 호환을 위해 `"자랑"` 결과가 없으면 `"PRIDE"` 카테고리를 재시도한다.

기간:

- `WEEKLY`: 오늘 포함 최근 7일
- `MONTHLY`: 오늘 포함 최근 30일

점수:

```text
popularityScore = likes * 3 + comments * 2 + views
```

집계:

- 후보 게시글 ID 목록을 만든다.
- 좋아요 수, 댓글 수, 조회수를 독립 배치 쿼리로 조회한다.
- 세 쿼리를 `CompletableFuture.supplyAsync()`로 병렬 실행한 뒤 `BoardCounts`로 합친다.
- 점수 내림차순, 생성일 내림차순으로 상위 30개를 저장한다.

조회 fallback:

1. 정확한 기간 스냅샷 조회
2. 기간이 겹치는 스냅샷 조회
3. 같은 period의 최근 스냅샷 조회
4. 즉시 생성
5. 그래도 없으면 최신 게시글 10개 fallback

스케줄:

- 매일 18:30 주간 스냅샷 생성
- 매주 월요일 18:30 월간 스냅샷 생성

## 13. 관리자 API

### `/api/admin/boards`

`ADMIN`, `MASTER` 접근 가능.

| API | 설명 |
|---|---|
| `GET /api/admin/boards/paging` | 게시글 목록 페이징, status/deleted/category/q 필터 |
| `GET /api/admin/boards/{id}` | 관리자용 단건 조회, 조회수 증가 없음 |
| `PATCH /api/admin/boards/{id}/blind` | 게시글 블라인드 |
| `PATCH /api/admin/boards/{id}/unblind` | 게시글 블라인드 해제 |
| `POST /api/admin/boards/{id}/delete` | 게시글 soft delete |
| `POST /api/admin/boards/{id}/restore` | 게시글 복구 |
| `GET /api/admin/boards/{boardId}/comments` | 관리자용 댓글 목록 |
| `PATCH /api/admin/boards/{boardId}/comments/{commentId}/blind` | 댓글 블라인드 |
| `PATCH /api/admin/boards/{boardId}/comments/{commentId}/unblind` | 댓글 블라인드 해제 |
| `POST /api/admin/boards/{boardId}/comments/{commentId}/delete` | 댓글 soft delete |
| `POST /api/admin/boards/{boardId}/comments/{commentId}/restore` | 댓글 복구 |

관리자 게시글 목록은 `Specification`으로 DB 레벨 필터링과 페이징을 수행한다.

필터:

- `deleted`
- `category`
- `status`
- `q`

`q` 검색은 제목/내용 FULLTEXT와 작성자 username 접두사 검색을 조합한다.

## 14. 도메인 간 연결

User:

- 게시글/댓글 작성자.
- 수정/삭제 권한과 이메일 인증 확인.
- 사용자 상태가 `ACTIVE`인 작성자 글/댓글만 일반 목록에 노출.

File:

- 게시글/댓글 첨부파일.

Notification:

- 댓글 작성 시 게시글 작성자에게 `BOARD_COMMENT` 알림.

Recommendation:

- 게시글 생성 시 `CommunityPostCreatedEvent`를 발행한다.
- 추천 도메인은 이 이벤트를 intent signal 분석에 사용할 수 있다.

Admin/Report:

- 신고 처리는 별도 Report/Admin 도메인이지만, 관리자 모더레이션 API는 게시글/댓글 상태를 변경한다.

## 15. 한계와 개선

- 사용자용 `BoardController`에는 댓글 수정 endpoint가 없다. 서비스에는 `updateComment()`가 구현되어 있다.
- reaction 요청 body의 `userId`를 사용한다. 현재 로그인 사용자와 일치하는지 서비스에서 별도 검증하지 않는다.
- 조회수 중복 방지는 `viewerId` 파라미터 기반이다. 인증 컨텍스트 기반 자동 추출이 아니므로 클라이언트가 값을 넘기지 않으면 비로그인 조회처럼 증가한다.
- 컨트롤러의 `permitAll()`과 보안 설정의 `/api/**` 인증 정책이 어긋날 수 있다.
- 게시글 상세 캐시는 현재 실시간 조회수 반영을 위해 제거되어 있다. 캐시 재도입 시 조회수/반응/댓글 count 무효화 전략이 필요하다.
- 인기글 병렬 집계는 기본 `ForkJoinPool`을 사용한다. 부하가 커지면 전용 executor 검토가 필요하다.
- 관리자 댓글 목록 필터는 서비스가 전체 댓글을 가져온 뒤 컨트롤러에서 status/deleted를 필터링한다.

## 16. 관련 문서

- [커뮤니티 게시판 아키텍처](<../architecture/board/커뮤니티 게시판 아키텍처.md>)
- [Board 백엔드 성능 최적화](../refactoring/board/board-backend-performance-optimization.md)
- [Board 인기글 스냅샷 배치 분석](../refactoring/board/board-popularity-snapshot-batch-analysis.md)
- [Board 인기글 스냅샷 배치 리팩토링](../refactoring/board/board-popularity-snapshot-batch-refactoring.md)
- [Board 인기글 스냅샷 N+1 리팩토링](../refactoring/board/board-popularity-snapshot-n-plus-one-refactoring.md)
- [Comment reaction query troubleshooting](../refactoring/board/comment-reaction-query/troubleshooting.md)
- [Board 성능 트러블슈팅](../troubleshooting/board/performance-optimization.md)
- [Board 코드 중복 매핑](../troubleshooting/board/code-duplication-mapping.md)
