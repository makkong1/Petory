# MissingPetDomainV2 페이지 드래프트 점검 및 작성

> 목적: 포트폴리오 repo의 `MissingPetDomainV2.jsx` 현재 내용과 Petory 코드/문서를 비교해, 실제 페이지에 넣을 문구와 수정 포인트를 확정한다.  
> 작성 기준: 현재 구현을 1순위로 보고, `docs/domains/missingpet.md`, 실종 제보 아키텍처, missing-pet 리팩토링/트러블슈팅 문서는 근거와 맥락으로만 사용한다.

---

## 0. 판단

현재 첨부한 `MissingPetDomainV2.jsx`는 방향이 좋고, 큰 구조는 그대로 진행해도 된다. MissingPet은 코드상 `domain/board` 패키지 안에 있지만, 포트폴리오에서는 일반 Board와 분리해 "실종 제보 글, 목격 댓글, 제보자-목격자 채팅 연결" 도메인으로 설명하는 게 맞다.

그대로 진행해도 되는 부분:

- 목록·상세·댓글 API를 분리해 조인 폭발을 피했다는 설명
- 목록에서 댓글을 제외하고 첨부파일과 댓글 수를 batch 조회한다는 설명
- 댓글 일괄 삭제를 N번 루프에서 bulk update 1회로 바꾼 설명
- 채팅 시작 시 게시글 전체 DTO 대신 작성자 ID projection을 사용한다는 설명
- 관리자 목록을 메모리 필터링에서 `Specification + DB 페이징`으로 바꾼 설명
- 서비스 레이어에서 JWT 주체 기반 작성자/댓글 작성자 권한을 검증한다는 설명

보완하면 좋은 부분:

- 관리자 컨트롤러 파일은 `domain/board/controller`가 아니라 `domain/admin/controller/AdminMissingPetController.java`에 있다.
- 사용자 컨트롤러 대부분에는 명시적 `@PreAuthorize`가 없지만, `SecurityConfig`의 `/api/**.authenticated()` 때문에 실제 `/api/missing-pets/**`는 인증이 필요하다. 이 차이를 한계 섹션에서 정확히 표현한다.
- `startMissingPetChat()`은 컨트롤러에서 작성자 ID projection과 JWT witnessId를 연결하고, 자기 글 채팅 차단은 `ConversationService.createMissingPetChat()`에서 처리한다.
- 홈 추천은 별도 포인트로 넣을 만하다. 좌표가 있으면 20km bounding box 후보를 DB에서 가져온 뒤, 서비스에서 Haversine 거리와 실종일 최신성 점수로 정렬한다.
- 댓글 작성 알림은 `@Async`로 분리되어 있고, 알림 실패가 댓글 작성을 롤백하지 않는다.
- 관리자 댓글 목록의 `deleted=true`는 현재 서비스가 삭제되지 않은 댓글만 반환하기 때문에 실질적으로 한계가 있다.

---

## 1. 페이지 상단

### H1

실종 제보 도메인

### 소개 문단

MissingPet 도메인은 실종 반려동물 제보 글, 목격 댓글, 제보자-목격자 채팅 연결을 담당한다. 일반 게시판과 달리 빠른 정보 스캔과 긴급 연결이 중요해서, 목록 응답에는 댓글 목록을 싣지 않고 첨부파일과 댓글 수만 batch 조회한다. 상세와 댓글은 별도 페이징으로 분리하고, 게시글 삭제 시 하위 댓글은 bulk update로 함께 soft delete한다. 채팅 시작 경로는 게시글 전체 DTO를 조립하지 않고 작성자 ID만 projection으로 조회한 뒤, JWT에서 가져온 목격자 id와 연결한다.

### 핵심 기능 태그 (`corePillars`)

첨부 JSX의 5개 태그는 그대로 사용 가능하다. 홈 추천을 같이 보여주고 싶으면 `"홈 추천 점수"`를 추가할 수 있지만, 현재 페이지 핵심은 성능/권한/채팅 연결이므로 5개 구성이 더 선명하다.

```javascript
const corePillars = [
  '조인 폭발 방지',
  '댓글 일괄삭제 최적화',
  '채팅 연결 경량화',
  '관리자 DB 필터링',
  '서비스 레이어 권한 검증',
];
```

---

## 2. `section#intro` - 도메인 개요

### 2-1. 개요 카드 문구

실종 제보 목록은 빠르게 스캔되어야 하지만, 게시글마다 댓글과 파일을 함께 붙이면 목록 조회가 댓글 수에 비례해 커진다. 현재 구조는 게시글과 작성자만 page로 조회하고, 게시글 id 목록을 만들어 `MISSING_PET` 첨부파일과 댓글 수를 batch 조회한다. DTO 변환도 `toBoardDTOWithoutComments()`를 사용해 `board.getComments()` 접근으로 인한 lazy loading을 피한다.

상세 조회는 게시글과 작성자, 첨부파일, 댓글 수를 조립하고, `commentSize > 0`일 때만 댓글 페이징을 추가로 조회한다. 댓글 목록은 작성자 fetch join과 댓글 첨부파일 batch 조회를 사용한다. 게시글 삭제 시 댓글은 `softDeleteAllByBoardIdx()` bulk update 1회로 처리한다.

채팅 시작은 긴급 액션 경로라 경량화했다. `POST /api/missing-pets/{boardIdx}/start-chat`은 `findUserIdByIdx()`로 제보자 id만 가져오고, 목격자 id는 `AuthenticatedUserIdResolver.requireCurrentUserIdx()`에서 가져온다. 이후 Chat 도메인의 `createMissingPetChat()`이 같은 제보 글에서 제보자-목격자 조합별 1:1 방을 만들거나 기존 방을 재사용한다.

### 2-2. 구조 테이블

| 항목 | 현재 코드 기준 |
|---|---|
| 사용자 API | `MissingPetBoardController`의 `/api/missing-pets` |
| 관리자 API | `AdminMissingPetController`의 `/api/admin/missing-pets` |
| 실제 인증 정책 | `SecurityConfig`의 `/api/**.authenticated()`로 인증 필요 |
| 게시글 작성자 식별 | 요청 DTO가 아니라 `SecurityContext` loginId |
| 목격 댓글 작성자 식별 | 요청 DTO가 아니라 `SecurityContext` loginId |
| 게시글 목록 | 게시글+작성자 page 조회, 댓글 제외 |
| 목록 첨부파일 | `getAttachmentsBatch(MISSING_PET, boardIds)` |
| 목록 댓글 수 | `countCommentsByBoardIds(boardIds)` |
| 상세 댓글 | `commentSize > 0`일 때만 댓글 page 조회 |
| 댓글 첨부파일 | `getAttachmentsBatch(MISSING_PET_COMMENT, commentIds)` |
| 삭제 | 게시글 soft delete + 댓글 bulk soft delete |
| 채팅 시작 | 작성자 ID projection + JWT witnessId + Chat 도메인 생성 |
| 홈 추천 | 20km bounding box 후보 + Haversine/실종일 점수 |
| 관리자 목록 | `Specification`, status/deleted/q 필터, DB 페이징 |

### 2-3. 성능 테이블

첨부 JSX의 표는 그대로 사용 가능하다. 추가로 목록 N+1 측정 수치를 보여주고 싶으면 아래 표를 사용할 수 있다.

| 지표 | Before | After |
|---|---|---|
| 게시글 목록 총 쿼리 수 | 105번 | 3번 |
| 게시글 목록 응답 시간 | 571ms | 106ms |
| 게시글 목록 메모리 증가량 | 11MB | 3MB |
| 댓글 일괄 삭제 (1000개) | 1001개 쿼리 | 1개 쿼리 |
| 채팅 연결 조회 | 게시글 전체 DTO 조립 | 작성자 ID projection 1회 |
| 관리자 목록 필터링 | 전체 메모리 로드 후 필터 | DB Specification + 페이징 |

보조 설명:

- 목록 수치는 `docs/troubleshooting/missing-pet/n-plus-one-query-issue.md`의 103개 게시글 기준 측정값이다.
- 댓글 일괄 삭제와 관리자 목록 수치는 `docs/refactoring/missing-pet/missing-pet-backend-performance-optimization.md`의 리팩토링 근거다.
- 운영 절대 수치가 아니라 병목 제거 전후 비교로 표현한다.

### 2-4. 데이터 흐름 카드

문구:

MissingPet 흐름은 도메인 페이지에 모든 시퀀스를 반복하지 않고 통합 흐름 페이지로 분리한다. 실종 제보 목록·상세·목격 댓글·채팅 시작 흐름과, Chat 인프라의 방 생성/메시지 흐름을 분리해 볼 수 있게 한다.

내부 링크:

- `/domains/flows?tab=missing-pet`
- `/domains/flows?tab=missing-pet&seq=chat`
- `/domains/flows?tab=chat&seq=missingpet`

---

## 3. `section#design` - 기술 결정

첨부 JSX의 A~E 구성은 적절하다. 아래 내용으로 문구와 코드 스니펫만 현재 코드 기준으로 다듬으면 된다.

### A. 목록·상세·댓글 API 분리

핵심 문구:

실종 제보 목록에서 댓글 전체를 함께 내려주면 게시글 수와 댓글 수가 곱해지는 조인 폭발이 발생하기 쉽다. 현재 구현은 목록, 상세, 댓글 목록을 분리한다. 목록은 댓글 목록을 제외하고 게시글+작성자만 조회한 뒤, 첨부파일과 댓글 수를 boardIds 기준 batch 조회한다. 상세는 `commentPage/commentSize`가 있을 때만 댓글 페이징을 붙인다.

코드 스니펫 후보:

```java
Page<MissingPetBoard> boardPage = status == null
        ? missingPetBoardRepository.findAllByOrderByCreatedAtDesc(pageable)
        : missingPetBoardRepository.findByStatusOrderByCreatedAtDesc(status, pageable);

List<Long> boardIds = boards.stream()
        .map(MissingPetBoard::getIdx)
        .collect(Collectors.toList());

Map<Long, List<FileDTO>> filesByBoardId =
        attachmentFileService.getAttachmentsBatch(
                FileTargetType.MISSING_PET, boardIds);

Map<Long, Integer> commentCountsByBoardId =
        missingPetCommentService.getCommentCountsBatch(boardIds);
```

DTO 변환:

```java
MissingPetBoardDTO dto = missingPetConverter.toBoardDTOWithoutComments(board);
dto.setAttachments(filesByBoardId.getOrDefault(board.getIdx(), List.of()));
dto.setCommentCount(commentCountsByBoardId.getOrDefault(board.getIdx(), 0));
```

근거:

- `backend/main/java/com/linkup/Petory/domain/board/service/MissingPetBoardService.java`
- `backend/main/java/com/linkup/Petory/domain/board/converter/MissingPetConverter.java`
- `docs/troubleshooting/missing-pet/n-plus-one-query-issue.md`

### B. 댓글 일괄 삭제 최적화

핵심 문구:

실종 제보 글을 soft delete할 때 댓글 1000개를 하나씩 조회하고 저장하면 `1 SELECT + 1000 UPDATE` 형태로 커진다. 현재 구현은 게시글 삭제 트랜잭션 안에서 댓글 repository의 bulk update를 호출해 하위 댓글을 한 번에 soft delete한다.

코드 스니펫 후보:

```java
@Transactional
public void deleteBoard(Long id) {
    MissingPetBoard board = missingPetBoardRepository.findByIdWithUser(id)
            .orElseThrow(MissingPetBoardNotFoundException::new);

    assertOwner(board.getUser());
    board.softDelete();
    missingPetCommentService.deleteAllCommentsByBoard(board);

    missingPetBoardRepository.saveAndFlush(board);
}
```

Repository:

```java
@Modifying(clearAutomatically = true)
@Query("UPDATE MissingPetComment mc " +
       "SET mc.isDeleted = true, mc.deletedAt = :deletedAt " +
       "WHERE mc.board.idx = :boardIdx AND mc.isDeleted = false")
int softDeleteAllByBoardIdx(Long boardIdx, LocalDateTime deletedAt);
```

근거:

- `backend/main/java/com/linkup/Petory/domain/board/service/MissingPetCommentService.java`
- `backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaMissingPetCommentRepository.java`
- `docs/refactoring/missing-pet/missing-pet-backend-performance-optimization.md`

### C. 채팅 연결 경량화

핵심 문구:

목격자가 제보자에게 연락하는 경로는 빠르고 단순해야 한다. 이전처럼 게시글 상세 DTO를 조립해서 작성자를 얻으면 첨부파일, 댓글 수, 댓글 페이징 조건까지 엮일 수 있다. 현재 컨트롤러는 게시글 작성자 id만 projection으로 조회하고, 목격자 id는 JWT 기반 resolver에서 가져온다. Chat 도메인은 같은 제보 글의 제보자-목격자 조합별 방을 재사용하거나 새로 만든다.

코드 스니펫 후보:

```java
@PostMapping("/{boardIdx}/start-chat")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<ConversationDTO> startMissingPetChat(
        @PathVariable("boardIdx") Long boardIdx) {
    Long reporterId = missingPetBoardService.getUserIdByBoardIdx(boardIdx);
    Long witnessId = getCurrentUserId();

    ConversationDTO conversation = conversationService.createMissingPetChat(
            boardIdx, reporterId, witnessId);

    return ResponseEntity.ok(conversation);
}
```

Repository:

```java
@Query("SELECT b.user.idx FROM MissingPetBoard b " +
       "WHERE b.idx = :idx AND b.isDeleted = false")
Optional<Long> findUserIdByIdx(Long idx);
```

Chat 서비스 보정:

```java
if (reporterId.equals(witnessId)) {
    throw ChatValidationException.ownReportCannotChat();
}
```

주의 문구:

- witnessId를 요청 파라미터나 body로 받지 않는다.
- 자기 글 채팅 시작 차단은 현재 Chat 서비스에서 처리한다.
- 동일 제보 글이어도 목격자가 다르면 1:1 채팅방이 별도로 생길 수 있다.

근거:

- `backend/main/java/com/linkup/Petory/domain/board/controller/MissingPetBoardController.java`
- `backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaMissingPetBoardRepository.java`
- `backend/main/java/com/linkup/Petory/domain/chat/service/ConversationService.java`

### D. 관리자 조회 DB 필터링 전환

핵심 문구:

관리자 목록은 삭제 여부, 상태, 검색어 필터가 함께 필요하다. 전체 데이터를 메모리에 올린 뒤 stream으로 필터링하면 데이터가 늘수록 응답 크기와 메모리 사용량이 커진다. 현재 구현은 `JpaSpecificationExecutor` 기반으로 status, deleted, q 조건을 DB query로 조합하고, `PageRequest`로 정렬과 페이징을 적용한다.

코드 스니펫 후보:

```java
if (deleted != null) {
    Specification<MissingPetBoard> deletedSpec =
            (root, query, cb) -> cb.equal(root.get("isDeleted"), deleted);
    spec = spec == null ? deletedSpec : spec.and(deletedSpec);
}

if (status != null) {
    Specification<MissingPetBoard> statusSpec =
            (root, query, cb) -> cb.equal(root.get("status"), status);
    spec = spec == null ? statusSpec : spec.and(statusSpec);
}

if (q != null && !q.isBlank()) {
    String keyword = "%" + q.toLowerCase() + "%";
    Specification<MissingPetBoard> searchSpec = (root, query, cb) -> {
        Join<MissingPetBoard, Users> userJoin = root.join("user", JoinType.LEFT);
        return cb.or(
                cb.like(cb.lower(root.get("title")), keyword),
                cb.like(cb.lower(root.get("content")), keyword),
                cb.like(cb.lower(root.get("petName")), keyword),
                cb.like(cb.lower(userJoin.get("username")), keyword));
    };
    spec = spec == null ? searchSpec : spec.and(searchSpec);
}
```

근거:

- `backend/main/java/com/linkup/Petory/domain/admin/controller/AdminMissingPetController.java`
- `backend/main/java/com/linkup/Petory/domain/board/service/MissingPetBoardService.java`
- `backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaMissingPetBoardRepository.java`

### E. 서비스 레이어 권한 검증

핵심 문구:

실종 제보 작성자와 댓글 작성자는 요청 body의 userId를 신뢰하지 않는다. 작성과 댓글 등록은 `SecurityContext`의 로그인 id로 활성 사용자를 조회한다. 수정, 상태 변경, 삭제는 서비스 레이어에서 소유자 또는 `ADMIN`/`MASTER`인지 확인한다. 컨트롤러 annotation만으로 막지 않고, 실제 데이터 소유권을 서비스에서 검증하는 구조다.

코드 스니펫 후보:

```java
private void assertOwner(Users boardOwner) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!isAdmin() && (auth == null || !auth.getName().equals(boardOwner.getId()))) {
        throw MissingPetForbiddenException.boardOwnerOnly();
    }
}

@Transactional
public MissingPetBoardDTO createBoard(MissingPetBoardDTO dto) {
    String loginId = SecurityContextHolder.getContext().getAuthentication().getName();
    Users user = usersRepository.findActiveByIdString(loginId)
            .orElseThrow(UserNotFoundException::new);
    // ...
}
```

댓글 삭제:

```java
private void assertCommentOwner(Users commentOwner) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (!isAdmin() && (auth == null || !auth.getName().equals(commentOwner.getId()))) {
        throw MissingPetForbiddenException.commentOwnerOnly();
    }
}
```

주의 문구:

- 작성, 수정, 삭제에는 `MISSING_PET` 목적 이메일 인증이 필요하다.
- 관리자 삭제도 현재 서비스 구현상 게시글 작성자의 이메일 인증에 걸릴 수 있다.

---

## 4. 추가 카드 후보 - 홈 추천

현재 JSX에는 한계 섹션에서만 홈 추천을 언급한다. 페이지에 도메인 특성을 조금 더 보여주고 싶으면 `기술 결정` 하위 카드로 추가 가능하다.

핵심 문구:

홈 화면 실종 제보는 단순 최신순이 아니라 위치와 실종일을 함께 본다. 좌표가 없으면 `MISSING` 상태 글을 `lostDate DESC, createdAt DESC`로 가져온다. 좌표가 있으면 20km bounding box 후보를 먼저 조회하고, 서비스에서 Haversine 거리와 실종일 최신성 점수를 계산한다. 부족한 결과는 최신 실종 제보로 보충한다.

코드 스니펫 후보:

```java
double score = 0.6 * recencyScore + 0.4 * distScore;
```

점수:

```text
recencyScore = max(0, 1 - daysSinceLost / 14)
distScore    = max(0, 1 - distKm / 20)
```

한계:

- 거리 정렬은 DB가 아니라 애플리케이션 메모리에서 수행한다.
- 후보 상한은 `max(200, size)`다.

---

## 5. `section#limits` - 한계와 운영 메모

첨부 JSX의 한계 섹션은 유지하되, 아래 항목으로 더 정확히 다듬으면 좋다.

- MissingPet 코드는 `domain/board` 패키지 안에 있어 일반 Board와 물리 경계가 섞여 있다.
- 사용자 컨트롤러 대부분에는 명시적 `@PreAuthorize`가 없지만, `SecurityConfig`의 `/api/**` catch-all 때문에 실제 접근은 인증 필요다.
- 관리자 삭제도 현재 서비스 구현상 게시글 작성자의 이메일 인증을 요구할 수 있다.
- 관리자 댓글 목록은 `deleted=true` 파라미터가 있어도 서비스가 삭제되지 않은 댓글만 반환하므로 삭제 댓글 조회가 제한적이다.
- 목격 댓글 위치는 주소와 좌표로 저장되지만, 별도 지도 시각화는 프론트 UI 범위에 의존한다.
- 홈 추천은 DB 거리 정렬이 아니라 애플리케이션 Haversine 점수 계산이다. 후보 수가 커지면 공간 인덱스/DB 거리 정렬 검토 여지가 있다.
- 동일 실종 제보에 목격자가 여러 명이면 제보자-목격자 조합별 1:1 채팅방이 늘어난다.
- 댓글 작성 알림은 비동기 후처리다. 알림 실패는 댓글 작성 실패로 전파하지 않는다.

---

## 6. `section#docs` - 연결 문서와 소스

### 내부 페이지 링크

- `/domains/missing-pet/optimization`
- `/domains/missing-pet/refactoring`
- `/domains/board/v2`
- `/domains/chat`
- `/domains/flows?tab=missing-pet`
- `/domains/flows?tab=missing-pet&seq=chat`

### GitHub 소스 링크 후보

첨부 JSX의 `PETORY_MISSING_PET_SERVICE`, `PETORY_MISSING_PET_COMMENT_SERVICE` 링크는 유지한다. 추가하면 좋은 링크:

```javascript
const PETORY_MISSING_PET_CONTROLLER =
  'https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/board/controller/MissingPetBoardController.java';
const PETORY_ADMIN_MISSING_PET_CONTROLLER =
  'https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/admin/controller/AdminMissingPetController.java';
const PETORY_MISSING_PET_REPO =
  'https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaMissingPetBoardRepository.java';
const PETORY_MISSING_PET_COMMENT_REPO =
  'https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaMissingPetCommentRepository.java';
const PETORY_MISSING_PET_CONVERTER =
  'https://github.com/makkong1/Petory/blob/dev/backend/main/java/com/linkup/Petory/domain/board/converter/MissingPetConverter.java';
```

### 문서 근거

- `docs/domains/missingpet.md`
- `docs/architecture/missingpet/실종 제보 아키텍처.md`
- `docs/refactoring/missing-pet/missing-pet-backend-performance-optimization.md`
- `docs/troubleshooting/missing-pet/n-plus-one-query-issue.md`
- `docs/troubleshooting/missing-pet/performance-measurement-results.md`
- `docs/troubleshooting/missing-pet/potential-issues.md`

---

## 7. JSX 반영 체크리스트

`MissingPetDomainV2.jsx`를 고칠 때 우선순위:

1. 현재 JSX의 큰 구조와 corePillars는 유지한다.
2. 관리자 컨트롤러 소스 링크를 추가한다면 경로를 `domain/admin/controller/AdminMissingPetController.java`로 잡는다.
3. 한계 섹션의 인증 문구는 "permitAll API도 인증 필요"보다 "`/api/**` catch-all 때문에 MissingPet API는 실제 인증 필요"로 다듬는다.
4. 채팅 카드에 자기 글 채팅 차단이 `ConversationService.createMissingPetChat()`에 있다는 점을 넣는다.
5. 홈 추천을 보여줄 경우 `20km bounding box + Haversine + recency/dist score`로 설명한다.
6. 관리자 댓글 `deleted=true` 조회 한계와 관리자 삭제의 이메일 인증 한계를 유지한다.
7. 성능 수치는 운영 절대 수치가 아니라 문서화된 테스트/리팩토링 비교값으로 표기한다.
