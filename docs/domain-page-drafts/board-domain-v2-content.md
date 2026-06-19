# BoardDomainV2 페이지 드래프트 점검 및 작성

> 목적: 포트폴리오 repo의 `BoardDomainV2.jsx` 현재 내용과 Petory 코드/문서를 비교해, 실제 페이지에 넣을 문구와 수정 포인트를 확정한다.  
> 작성 기준: 현재 구현을 1순위로 보고, `docs/domains/board.md`, 커뮤니티 게시판 아키텍처, board 리팩토링/트러블슈팅 문서는 근거와 맥락으로만 사용한다.

---

## 0. 판단

현재 첨부한 `BoardDomainV2.jsx`는 방향이 좋고, 큰 구조는 그대로 진행해도 된다. 다만 기존 draft 파일이 없으므로 이 문서를 새 기준안으로 만든다.

- Board 페이지는 일반 커뮤니티 게시판과 일반 댓글을 함께 다루는 게 맞다.
- `MissingPetBoard`, `MissingPetComment`는 같은 `domain/board` 패키지에 있지만, 이 페이지에서는 제외하고 Missing Pet 도메인으로 넘기는 게 맞다.
- 핵심 칩 5개는 적절하다. 다만 댓글 최적화가 중요한 페이지라 `"댓글 배치 조회"`를 칩에 추가해도 좋다.
- 성능 수치 `301개 -> 3개`, `745ms -> 30ms`, `22.50MB -> 2MB`는 `docs/troubleshooting/board/performance-optimization.md`에 근거가 있어 사용 가능하다.
- 조회수 스니펫은 첨부 JSX처럼 `existsByBoardAndUser` 기반으로 쓰지 말고, 현재 코드의 `insertIgnore(boardId, userId)` 기준으로 바꾸는 게 정확하다.
- 인기글 점수는 현재 코드 기준 `likes * 3 + comments * 2 + views`다. 오래된 문서의 `조회수*0.1 + 좋아요*2 + 댓글*1.5` 표현은 쓰지 않는다.
- 생성/댓글 작성은 JWT principal 기반이지만, 반응 API는 아직 `ReactionRequest.userId()`를 사용한다. 이건 한계 섹션에 남기는 게 좋다.
- `CommentService.updateComment()`는 구현되어 있지만 현재 사용자용 `BoardController`에는 댓글 수정 endpoint가 없다. 페이지에서 "댓글 수정 API 완료"처럼 쓰면 안 된다.

---

## 1. 페이지 상단

### H1

게시판 도메인

### 소개 문단

Board 도메인은 Petory 사용자들이 일상, 정보, 질문, 자랑 글을 올리고 댓글과 반응으로 소통하는 커뮤니티 영역이다. 구현의 핵심은 단순 CRUD보다 읽기 중심 성능과 데이터 정합성이었다. 게시글/댓글 목록은 작성자, 반응 수, 첨부파일을 함께 보여줘야 해서 N+1 문제가 쉽게 발생했고, 상세 조회는 로그인 사용자 기준 조회 중복을 막아야 했다. 현재 구조는 작성자 fetch join, 반응/첨부 배치 조회, 조회 로그 `insertIgnore`, 인기글 스냅샷을 조합해 목록·상세·인기글 조회 비용을 분리한다.

### 핵심 기능 태그 (`corePillars`)

첨부 JSX의 5개 태그는 사용 가능하다. 댓글을 더 강조하려면 6개 구성이 좋다.

```javascript
const corePillars = [
  '목록 N+1 최적화',
  '댓글 배치 조회',
  '반응 토글',
  '조회수 중복 방지',
  '인기글 스냅샷',
  'FULLTEXT 검색',
];
```

---

## 2. `section#intro` - 도메인 개요

### 2-1. 개요 카드 문구

게시글 목록은 `BoardService.getAllBoardsWithPaging()`에서 page를 조회한 뒤 `mapBoardsWithReactionsBatch()`로 DTO를 구성한다. 게시글과 작성자는 repository query의 `JOIN FETCH`로 함께 가져오고, 게시글 ID 목록을 모아 반응 수와 첨부파일을 배치 조회한다. 댓글 목록도 같은 방향이다. `CommentService.getCommentsWithPaging()`은 댓글 page를 가져온 뒤 댓글 ID 목록으로 반응 수와 첨부파일을 배치 조회한다.

상세 조회는 `viewerId`가 있으면 `BoardViewLog`에 `boardId + userId`를 `INSERT IGNORE`로 기록하고, insert 성공 시에만 `board.viewCount`를 증가시킨다. 인기글은 요청 때마다 전체 계산하지 않고 스케줄러가 주간/월간 스냅샷을 만들며, 조회 시 스냅샷을 우선 사용하고 없으면 겹치는 기간, 최근 스냅샷, on-demand 생성, 최신 글 fallback 순서로 내려간다.

### 2-2. 구조 테이블

| 항목 | 현재 코드 기준 |
|---|---|
| 사용자 API | `BoardController`의 `/api/boards` |
| 게시글 목록 | page 조회 후 reaction/attachment 배치 매핑 |
| 댓글 목록 | page 조회 후 reaction/attachment 배치 매핑 |
| 작성자 로딩 | 게시글 query에서 `JOIN FETCH b.user` |
| 게시글 반응 | `LIKE`/`DISLIKE` 토글, `likeCount`/`dislikeCount` DB 원자적 delta update |
| 댓글 반응 | `LIKE`/`DISLIKE` 토글, 응답 summary는 reaction repository count 기반 |
| 조회수 | `BoardViewLog.insertIgnore(boardId, userId)` 성공 시에만 증가 |
| 인기글 | `BoardPopularitySnapshot` 주간/월간 스냅샷 우선 조회 |
| 검색 | `TITLE_CONTENT`는 FULLTEXT, `NICKNAME`은 작성자 JOIN + 접두사 LIKE |
| 첨부파일 | File 도메인의 `AttachmentFileService`와 단일/배치 연동 |
| 알림 | 댓글 작성자가 게시글 작성자와 다르면 `BOARD_COMMENT` 알림 생성 |
| 제외 범위 | `MissingPetBoard`, `MissingPetComment` |

### 2-3. 성능 테이블

첨부 페이지의 표는 그대로 사용 가능하다. 단, "100개 게시글 목록, 작성자·반응·첨부 포함, 테스트 DB 측정"이라는 조건을 같이 둔다.

| 지표 | Before | After |
|---|---|---|
| 목록 조회 쿼리 수 | 301개 | 3개 |
| 실행 시간 | 745ms | 30ms |
| 메모리 | 22.50MB | 2MB |

보조 설명:

- 이 수치는 `docs/troubleshooting/board/performance-optimization.md`의 기존 측정값이다.
- 현재 페이지에서는 "운영 절대 수치"가 아니라 "N+1 제거 전후 비교"로 제시한다.
- 첨부 JSX의 측정 조건 문구는 유지해도 된다.

### 2-4. 데이터 흐름 카드

문구:

시퀀스 다이어그램은 도메인별 페이지에 반복하지 않고 통합 흐름 페이지로 분리한다. Board 탭에서는 게시글 목록, 상세 조회와 조회수, 댓글 작성, 반응 토글, 인기글 스냅샷 흐름을 한 번에 확인할 수 있게 한다.

내부 링크:

- `/domains/flows?tab=board`

---

## 3. `section#design` - 기술 결정

첨부 JSX의 A~E 구성은 적절하다. 다만 조회수와 검색 스니펫은 현재 코드 표현으로 조금 다듬는 게 좋다.

### A. 목록 N+1

핵심 문구:

게시글 목록은 작성자, 반응 수, 첨부파일을 한 화면에서 같이 보여줘야 한다. 작성자는 게시글 조회 query에서 fetch join으로 해결하고, 반응 수와 첨부파일은 게시글 ID 목록을 만든 뒤 배치 조회한다. 이 구조 덕분에 게시글 수가 늘어나도 DTO 변환 단계에서 게시글별 개별 count/file query가 반복되지 않는다.

코드 스니펫 후보:

```java
List<Long> boardIds = boards.stream()
        .map(Board::getIdx)
        .collect(Collectors.toList());

Map<Long, Map<ReactionType, Long>> reactionCountsMap =
        getReactionCountsBatch(boardIds);

Map<Long, List<FileDTO>> attachmentsMap =
        attachmentFileService.getAttachmentsBatch(FileTargetType.BOARD, boardIds);
```

근거:

- `backend/main/java/com/linkup/Petory/domain/board/service/BoardService.java`
- `backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaBoardRepository.java`
- `docs/troubleshooting/board/performance-optimization.md`

### B. 댓글 N+1

핵심 문구:

댓글 목록도 게시글 목록과 같은 문제가 있었다. 댓글마다 좋아요/싫어요 수와 첨부파일을 보여줘야 하므로, 댓글 ID 목록을 모아 reaction count와 file attachment를 배치 조회한다. 댓글 reaction count는 500개 단위로 나눠 `IN` 조건 크기를 제한한다.

코드 스니펫 후보:

```java
List<Long> commentIds = comments.stream()
        .map(Comment::getIdx)
        .collect(Collectors.toList());

Map<Long, List<FileDTO>> filesByCommentId =
        attachmentFileService.getAttachmentsBatch(FileTargetType.COMMENT, commentIds);

Map<Long, Map<ReactionType, Long>> reactionCountsMap =
        getReactionCountsBatch(commentIds);
```

근거:

- `backend/main/java/com/linkup/Petory/domain/board/service/CommentService.java`
- `backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaCommentReactionRepository.java`

### C. 실시간 값과 배치 값을 분리한다

핵심 문구:

반응은 사용자가 버튼을 누르는 즉시 결과가 보여야 하므로 `BoardReaction`을 토글하고 `Board.likeCount`, `Board.dislikeCount`를 DB update로 delta 조정한다. 반대로 인기글은 매 요청마다 계산하지 않고 스냅샷으로 분리한다. 스냅샷 생성 시에는 좋아요, 댓글, 조회수 batch count를 병렬로 가져와 `likes * 3 + comments * 2 + views` 점수로 정렬한다.

코드 스니펫 후보:

```java
if (previousReactionType == ReactionType.LIKE) likeDelta--;
else if (previousReactionType == ReactionType.DISLIKE) dislikeDelta--;
if (currentReactionType == ReactionType.LIKE) likeDelta++;
else if (currentReactionType == ReactionType.DISLIKE) dislikeDelta++;

if (likeDelta != 0) boardRepository.adjustLikeCount(boardId, likeDelta);
if (dislikeDelta != 0) boardRepository.adjustDislikeCount(boardId, dislikeDelta);
```

인기글 설명:

- 대상 카테고리: `"자랑"`, 없으면 legacy `"PRIDE"` fallback
- 주간 스냅샷: 매일 18:30 생성
- 월간 스냅샷: 매주 월요일 18:30 생성
- 점수: `likes * 3 + comments * 2 + views`
- 조회수는 `board.viewCount`가 아니라 `BoardViewLog` batch count 기반

근거:

- `backend/main/java/com/linkup/Petory/domain/board/service/ReactionService.java`
- `backend/main/java/com/linkup/Petory/domain/board/service/BoardPopularityService.java`
- `backend/main/java/com/linkup/Petory/domain/board/service/BoardPopularityScheduler.java`
- `docs/refactoring/board/board-popularity-snapshot-batch-analysis.md`

### D. 조회수 품질

핵심 문구:

조회수는 단순히 상세 API 호출마다 증가시키면 새로고침이나 재진입으로 쉽게 부풀려진다. 현재 구현은 `viewerId`가 있으면 `board_view_log`에 `boardId + userId` 조합을 `INSERT IGNORE`로 기록하고, insert 성공 시에만 view count를 증가시킨다. `viewerId`가 없거나 유효한 사용자를 찾지 못하면 비로그인 조회처럼 매번 증가한다.

첨부 JSX 스니펫 수정 권장:

```java
private boolean shouldIncrementView(Board board, Long viewerId) {
    if (viewerId == null) {
        return true;
    }
    Users viewer = usersRepository.findById(viewerId).orElse(null);
    if (viewer == null) {
        return true;
    }
    return boardViewLogRepository.insertIgnore(board.getIdx(), viewer.getIdx()) > 0;
}
```

근거:

- `backend/main/java/com/linkup/Petory/domain/board/service/BoardService.java`
- `backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaBoardViewLogRepository.java`

### E. 검색

핵심 문구:

게시글 검색은 제목/내용과 작성자 닉네임의 성격이 달라 query를 분리한다. `TITLE_CONTENT`는 MySQL FULLTEXT index를 사용해 `MATCH(title, content) AGAINST`로 검색하고 relevance와 생성일 순으로 정렬한다. `NICKNAME`은 작성자 JOIN과 `nickname LIKE :keyword%` 접두사 검색을 사용해 DB 레벨에서 페이징한다.

코드 스니펫 후보:

```java
switch (searchType != null ? searchType.toUpperCase() : "TITLE_CONTENT") {
    case "NICKNAME":
        boardPage = boardRepository.searchByNicknameWithPaging(trimmedKeyword, pageable);
        break;
    case "TITLE_CONTENT":
    default:
        boardPage = boardRepository.searchByKeywordWithPaging(trimmedKeyword, pageable);
        break;
}
```

근거:

- `backend/main/java/com/linkup/Petory/domain/board/service/BoardService.java`
- `backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaBoardRepository.java`
- `docs/refactoring/recordType/board/board-search-optimization.md`

---

## 4. `section#limits` - 한계 & 다음 개선

문구:

Board 도메인은 목록 조회와 인기글 계산 비용을 크게 줄였지만, 인증 기준과 레거시 흐름에는 아직 정리할 부분이 남아 있다.

목록:

- 인기글 대상은 `"자랑"` 카테고리 중심이고, legacy `"PRIDE"` fallback이 남아 있다.
- 목록/검색은 offset pagination 기반이다. 대용량 커뮤니티로 커지면 cursor pagination을 검토할 수 있다.
- 조회수 중복 방지는 DB `board_view_log` 기반이다. 트래픽이 커지면 Redis TTL 기반 deduplication을 검토할 수 있다.
- 상세 조회의 `viewerId`는 request param이다. 인증 컨텍스트에서 viewer를 자동 추출하는 구조는 아니다.
- 게시글 생성과 댓글 작성은 JWT principal 기반이지만, 반응 API는 아직 `ReactionRequest.userId()`를 사용한다.
- `CommentService.updateComment()`는 구현되어 있지만 현재 사용자용 `BoardController`에는 댓글 수정 endpoint가 노출되어 있지 않다.
- `GET /api/boards` 계열에는 `@PreAuthorize("permitAll()")`가 있지만, 실제 접근 가능 여부는 `SecurityConfig`의 `/api/**` 인증 규칙 영향을 함께 받는다.
- 관리자 조회는 최적화된 Specification 흐름과 과거 흐름의 흔적이 공존해 추가 정리 여지가 있다.

---

## 5. `section#docs` - 관련 페이지

### 내부 링크

- `/domains/flows?tab=board` - Board 시퀀스
- `/domains/board/optimization` - N+1, 인덱스, Before/After
- `/domains/board/refactoring` - 중복 제거, 검색/인기글 리팩토링
- `/domains/missing-pet` - 같은 board 패키지 안의 별도 실종 제보 도메인
- `/domains/file` - 게시글/댓글 첨부파일 연결
- `/domains/notification` - 댓글 작성 알림
- `/domains/admin` - 게시글/댓글 모더레이션

### GitHub 링크 상수 후보

첨부 JSX의 두 상수는 유지 가능하다. 아키텍처 문서와 핵심 소스 링크를 추가하면 페이지 근거가 더 선명해진다.

```javascript
const PETORY_BOARD_DOMAIN_DOC =
  'https://github.com/makkong1/Petory/blob/main/docs/domains/board.md';
const PETORY_BOARD_ARCH_DOC =
  'https://github.com/makkong1/Petory/blob/main/docs/architecture/board/%EC%BB%A4%EB%AE%A4%EB%8B%88%ED%8B%B0%20%EA%B2%8C%EC%8B%9C%ED%8C%90%20%EC%95%84%ED%82%A4%ED%85%8D%EC%B2%98.md';
const PETORY_BOARD_PERF_DOC =
  'https://github.com/makkong1/Petory/blob/main/docs/troubleshooting/board/performance-optimization.md';
const PETORY_BOARD_SERVICE =
  'https://github.com/makkong1/Petory/blob/main/backend/main/java/com/linkup/Petory/domain/board/service/BoardService.java';
const PETORY_COMMENT_SERVICE =
  'https://github.com/makkong1/Petory/blob/main/backend/main/java/com/linkup/Petory/domain/board/service/CommentService.java';
const PETORY_REACTION_SERVICE =
  'https://github.com/makkong1/Petory/blob/main/backend/main/java/com/linkup/Petory/domain/board/service/ReactionService.java';
const PETORY_POPULARITY_SERVICE =
  'https://github.com/makkong1/Petory/blob/main/backend/main/java/com/linkup/Petory/domain/board/service/BoardPopularityService.java';
```

관련 문서:

- `docs/domains/board.md`
- `docs/architecture/board/커뮤니티 게시판 아키텍처.md`
- `docs/troubleshooting/board/performance-optimization.md`
- `docs/refactoring/board/board-backend-performance-optimization.md`
- `docs/refactoring/board/comment-reaction-query/troubleshooting.md`
- `docs/refactoring/board/board-popularity-snapshot-batch-analysis.md`
- `docs/refactoring/board/board-popularity-snapshot-batch-refactoring.md`
- `docs/refactoring/board/board-popularity-snapshot-n-plus-one-refactoring.md`
- `docs/refactoring/recordType/board/board-search-optimization.md`

---

## 6. 첨부 `BoardDomainV2.jsx` 반영 체크

그대로 진행해도 되는 부분:

- 섹션 순서: `pillars -> intro -> design -> limits -> docs`
- Board와 Comment를 한 페이지에서 묶는 방향
- Missing Pet을 관련 페이지로만 연결하는 방향
- N+1, 반응 토글, 조회수, 인기글, FULLTEXT 검색을 핵심 축으로 잡은 구성
- 성능 표의 수치와 측정 조건 문구
- `/domains/flows?tab=board` 링크

수정하고 진행할 부분:

- 핵심 태그에 댓글을 명확히 넣고 싶으면 `"댓글 배치 조회"`를 추가한다.
- 조회수 코드 스니펫은 `existsByBoardAndUser`가 아니라 현재 구현의 `insertIgnore` 기준으로 바꾼다.
- 인기글 설명에는 현재 점수식 `likes * 3 + comments * 2 + views`를 사용한다.
- 권한/인증 한계에 "반응 API는 아직 request body userId 사용"을 추가한다.
- 댓글 수정은 서비스 구현은 있지만 사용자용 `BoardController` endpoint는 없다고 표현한다.
- `GET /api/boards` 계열은 `permitAll` annotation만 보고 공개 API처럼 쓰지 않는다.
