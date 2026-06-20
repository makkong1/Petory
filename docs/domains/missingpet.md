# MissingPet 도메인

> 기준: 현재 코드를 단일 진실로 본다. MissingPet 코드는 `domain/board` 패키지에 있지만, 일반 커뮤니티 Board와 별도 도메인으로 문서화한다.

## 1. 범위

MissingPet 도메인은 실종 반려동물 제보 글, 목격 댓글, 제보자-목격자 채팅 시작, 관리자 운영을 담당한다.

포함 범위:

- 실종 제보 목록/홈 추천/상세/작성/수정/삭제
- 실종 상태 변경
- 실종 위치 좌표 저장
- 목격 댓글 목록/작성/삭제
- 목격 댓글 위치 좌표 저장
- 게시글/댓글 이미지 첨부
- 댓글 작성 알림
- 제보자-목격자 채팅방 시작
- 관리자 실종 제보/댓글 관리
- 목록/댓글/관리자 조회 성능 최적화

비범위:

- 일반 커뮤니티 게시글/댓글
- 실종 제보 신고 처리
- 채팅 메시지 송수신
- 파일 저장소 구현 자체
- 알림 전송 인프라 자체
- 지도 UI 자체

## 2. 주요 코드

| 구분 | 주요 파일 |
|---|---|
| 사용자 API | `backend/main/java/com/linkup/Petory/domain/board/controller/MissingPetBoardController.java` |
| 관리자 API | `backend/main/java/com/linkup/Petory/domain/admin/controller/AdminMissingPetController.java` |
| 게시글 서비스 | `backend/main/java/com/linkup/Petory/domain/board/service/MissingPetBoardService.java` |
| 댓글 서비스 | `backend/main/java/com/linkup/Petory/domain/board/service/MissingPetCommentService.java` |
| 게시글 엔티티 | `backend/main/java/com/linkup/Petory/domain/board/entity/MissingPetBoard.java` |
| 댓글 엔티티 | `backend/main/java/com/linkup/Petory/domain/board/entity/MissingPetComment.java` |
| converter | `backend/main/java/com/linkup/Petory/domain/board/converter/MissingPetConverter.java` |
| 게시글 repository | `backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaMissingPetBoardRepository.java` |
| 댓글 repository | `backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaMissingPetCommentRepository.java` |
| 프론트 사용자 API | `frontend/src/api/missingPetApi.js` |
| 프론트 관리자 API | `frontend/src/api/missingPetAdminApi.js` |
| 사용자 화면 | `frontend/src/components/MissingPet/MissingPetBoardPage.js`, `MissingPetBoardDetail.js`, `MissingPetBoardForm.js` |
| 관리자 화면 | `frontend/src/components/Admin/sections/MissingPetManagementSection.js` |

## 3. 핵심 엔티티

### MissingPetBoard

실종 제보 게시글이다.

| 필드 | 의미 |
|---|---|
| `idx` | 게시글 PK |
| `user` | 제보자 |
| `title`, `content` | 제목/본문 |
| `petName` | 반려동물 이름 |
| `species`, `breed` | 종/품종 |
| `gender` | `M`, `F` |
| `age`, `color` | 나이/색상 |
| `lostDate` | 실종일 |
| `lostLocation` | 실종 위치 주소 |
| `latitude`, `longitude` | 실종 위치 좌표, `BigDecimal(15,12)` |
| `status` | `MISSING`, `FOUND`, `RESOLVED` |
| `comments` | 목격 댓글 |
| `isDeleted`, `deletedAt` | soft delete 상태 |

`status`가 null이면 `@PrePersist`에서 `MISSING`으로 설정된다.

### MissingPetComment

목격 댓글이다.

| 필드 | 의미 |
|---|---|
| `board` | 대상 실종 제보 |
| `user` | 댓글 작성자 |
| `content` | 목격 내용 |
| `address` | 목격 위치 주소 |
| `latitude`, `longitude` | 목격 위치 좌표, `BigDecimal(15,12)` |
| `isDeleted`, `deletedAt` | soft delete 상태 |

댓글도 `BaseTimeEntity`를 상속한다.

## 4. 사용자 API

### `/api/missing-pets`

| API | 설명 |
|---|---|
| `GET /api/missing-pets/home?lat&lng&size` | 홈 화면용 실종 제보 추천 |
| `GET /api/missing-pets?status&page&size` | 실종 제보 목록 페이징 조회 |
| `GET /api/missing-pets/{id}?commentPage&commentSize` | 상세 조회, 댓글 페이징 포함 |
| `POST /api/missing-pets` | 실종 제보 작성 |
| `PUT /api/missing-pets/{id}` | 실종 제보 수정 |
| `PATCH /api/missing-pets/{id}/status` | 상태 변경 |
| `DELETE /api/missing-pets/{id}` | 실종 제보 soft delete |
| `GET /api/missing-pets/{id}/comments?page&size` | 목격 댓글 목록 페이징 조회 |
| `POST /api/missing-pets/{id}/comments` | 목격 댓글 작성 |
| `DELETE /api/missing-pets/{boardId}/comments/{commentId}` | 목격 댓글 soft delete |
| `POST /api/missing-pets/{boardIdx}/start-chat` | 제보자-목격자 채팅 시작 |

컨트롤러 클래스에는 별도 `@PreAuthorize`가 없고, 채팅 시작만 메서드 단위 `@PreAuthorize("isAuthenticated()")`가 있다. 실제 접근 가능 여부는 `SecurityConfig`의 `/api/**` 정책도 함께 확인해야 한다.

## 5. 홈 추천

`GET /api/missing-pets/home`은 홈 화면에 실종 제보를 노출하기 위한 경량 추천 API다.

파라미터:

- `lat`
- `lng`
- `size`, 기본 6

좌표가 없을 때:

- `MISSING` 상태
- 삭제되지 않은 게시글
- 활성 작성자
- `lostDate DESC, createdAt DESC`

좌표가 있을 때:

1. 20km 반경 bounding box 후보를 조회한다.
2. 후보 수는 `max(200, size)`로 잡는다.
3. 각 후보에 대해 실종일 최신성과 거리 점수를 계산한다.
4. 반경 20km 이내 후보만 남긴다.
5. `score = 0.6 * recencyScore + 0.4 * distScore`로 정렬한다.
6. 부족하면 최신 실종 제보로 보충한다.

점수:

```text
recencyScore = max(0, 1 - daysSinceLost / 14)
distScore    = max(0, 1 - distKm / 20)
```

응답 DTO의 `distance`는 미터 단위로 설정된다.

## 6. 목록과 상세

### 목록

`getBoardsWithPaging(status, page, size)`를 사용한다.

조건:

- 삭제되지 않은 게시글
- 작성자 `isDeleted=false`
- 작성자 `status=ACTIVE`
- status가 있으면 해당 상태만

정렬:

- `createdAt DESC`

성능 처리:

- 게시글과 작성자를 fetch join한다.
- 댓글은 목록에서 제외한다.
- 게시글 첨부파일은 `getAttachmentsBatch(FileTargetType.MISSING_PET, boardIds)`로 배치 조회한다.
- 댓글 수는 `getCommentCountsBatch(boardIds)`로 배치 조회한다.
- DTO는 `toBoardDTOWithoutComments()`를 사용해 댓글 lazy loading을 피한다.

### 상세

`getBoard(id, commentPage, commentSize)`를 사용한다.

흐름:

1. 게시글과 작성자 조회
2. 삭제된 게시글이면 not found 처리
3. 게시글 첨부파일 조회
4. 댓글 수 `COUNT` 조회
5. `commentSize > 0`이면 댓글 페이징 조회
6. 게시글 DTO에 댓글 목록/댓글 수/첨부파일/대표 이미지 설정

`commentSize=0`이면 상세 응답에 댓글 목록을 포함하지 않는다.

## 7. 작성, 수정, 삭제

### 작성

작성 흐름:

1. 현재 로그인 사용자 id를 `SecurityContext`에서 읽는다.
2. 활성 사용자 조회
3. 이메일 인증 확인
4. 게시글 생성
5. `imageUrl`이 있으면 `FileTargetType.MISSING_PET` 단일 첨부로 동기화

이메일 인증 purpose:

- `MISSING_PET`

작성자는 요청 DTO가 아니라 현재 로그인 사용자 기준이다.

### 수정

수정 정책:

- 작성자 또는 `ADMIN`/`MASTER`만 가능하다.
- 작성자 이메일 인증이 필요하다.
- null이 아닌 필드만 부분 수정한다.
- `imageUrl`이 있으면 첨부파일을 단일 동기화한다.

### 삭제

삭제 정책:

- 작성자 또는 `ADMIN`/`MASTER`만 가능하다.
- 작성자 이메일 인증이 필요하다.
- 게시글은 soft delete한다.
- 연결 댓글은 `softDeleteAllByBoardIdx` bulk update로 함께 soft delete한다.

주의:

- 관리자 삭제도 현재 서비스상 게시글 작성자의 이메일 인증을 요구한다.

## 8. 상태 변경

상태:

- `MISSING`
- `FOUND`
- `RESOLVED`

사용자 API는 body에 `{"status":"..."}`를 받는다.

검증:

- body에 status가 없으면 `BoardValidationException.statusRequired()`
- enum 값이 아니면 `BoardValidationException.invalidStatus("MISSING, FOUND, RESOLVED")`

권한:

- 작성자 또는 `ADMIN`/`MASTER`

## 9. 목격 댓글

### 목록

`getCommentsWithPaging(boardId, page, size)`를 사용한다.

조건:

- 삭제되지 않은 댓글
- 댓글 작성자 `isDeleted=false`
- 댓글 작성자 `status=ACTIVE`

정렬:

- `createdAt ASC`

첨부파일은 댓글 id 목록으로 batch 조회한다.

### 작성

작성 흐름:

1. 게시글 조회
2. 현재 로그인 사용자 id를 `SecurityContext`에서 읽는다.
3. 활성 사용자 조회
4. content/address/latitude/longitude 저장
5. `imageUrl`이 있으면 `FileTargetType.MISSING_PET_COMMENT` 단일 첨부로 동기화
6. 댓글 작성자가 게시글 작성자와 다르면 비동기 알림 발송

알림:

- `NotificationType.MISSING_PET_COMMENT`
- target type 문자열: `"MISSING_PET"`

알림 실패는 로그만 남기고 댓글 작성은 성공한다.

### 삭제

삭제 정책:

- 게시글 존재 확인
- 댓글 존재 확인
- 댓글이 해당 게시글에 속하는지 확인
- 댓글 작성자 또는 `ADMIN`/`MASTER`만 가능
- soft delete

## 10. 채팅 시작

`POST /api/missing-pets/{boardIdx}/start-chat`

흐름:

1. 게시글 작성자 id를 `findUserIdByIdx(boardIdx)`로 경량 조회
2. 목격자 id를 `AuthenticatedUserIdResolver.requireCurrentUserIdx()`에서 가져온다.
3. `ConversationService.createMissingPetChat(boardIdx, reporterId, witnessId)`를 호출한다.

주의:

- witnessId 쿼리 파라미터를 받지 않는다.
- 삭제된 게시글이면 `findUserIdByIdx`에서 조회되지 않는다.
- 작성자와 목격자가 같은 경우를 이 컨트롤러/서비스에서 별도 차단하지 않는다.

## 11. 관리자 API

### `/api/admin/missing-pets`

`ADMIN`, `MASTER` 접근 가능.

| API | 설명 |
|---|---|
| `GET /api/admin/missing-pets/paging?status&deleted&q&page&size` | 관리자 목록 |
| `GET /api/admin/missing-pets/{id}` | 관리자 상세 |
| `PATCH /api/admin/missing-pets/{id}/status` | 상태 변경 |
| `POST /api/admin/missing-pets/{id}/delete` | soft delete |
| `POST /api/admin/missing-pets/{id}/restore` | 복구 |
| `GET /api/admin/missing-pets/{boardId}/comments?deleted` | 댓글 목록 |
| `POST /api/admin/missing-pets/{boardId}/comments/{commentId}/delete` | 댓글 삭제 |

관리자 목록:

- `Specification`으로 DB 레벨 필터링
- status 필터
- deleted 필터
- q 검색: 제목, 내용, 반려동물 이름, 작성자 username
- user fetch로 DTO 변환 시 N+1 완화
- 첨부파일과 댓글 수는 batch 조회

관리자 댓글 목록:

- 서비스에서 삭제되지 않은 댓글만 조회한다.
- 컨트롤러에서 `deleted` 필터를 추가로 적용하지만, 서비스 결과에 삭제 댓글이 포함되지 않으므로 `deleted=true` 조회는 현재 실질적으로 비어 있을 수 있다.

관리자 상태 변경의 잘못된 status는 `IllegalArgumentException`을 던진다. 사용자 API는 `BoardValidationException`을 사용한다.

## 12. 도메인 간 연결

User:

- 제보자와 댓글 작성자.
- 작성/수정/삭제 이메일 인증.
- 일반 조회는 활성 사용자 글/댓글만 노출.

File:

- 실종 제보 이미지: `FileTargetType.MISSING_PET`
- 목격 댓글 이미지: `FileTargetType.MISSING_PET_COMMENT`

Notification:

- 댓글 작성 시 제보자에게 `MISSING_PET_COMMENT` 알림.

Chat:

- 목격자와 제보자 간 채팅방 생성.

Admin:

- 실종 제보 상태/삭제/복구/댓글 삭제 관리.

Report:

- 실종 제보 신고 처리는 별도 Report 도메인에서 다룬다.

## 13. 한계와 개선

- 코드 위치가 `domain/board`에 있어 일반 Board와 패키지 경계가 섞여 있다.
- 사용자 API 컨트롤러 대부분에는 명시적 `@PreAuthorize`가 없다. 실제 접근 정책은 `SecurityConfig`와 함께 확인해야 한다.
- 관리자 삭제도 작성자 이메일 인증을 요구한다.
- 관리자 댓글 목록은 삭제 댓글 조회에 한계가 있다.
- 사용자 상세 조회는 게시글 첨부파일을 단건 조회한다. 목록은 batch지만 상세는 단건이다.
- 댓글 작성은 이미지 한 장만 `syncSingleAttachment`로 연결한다.
- 홈 추천은 애플리케이션에서 Haversine 점수를 계산한다. 후보 수가 커지면 DB 거리 정렬 또는 공간 인덱스 검토 여지가 있다.
- 채팅 시작 시 제보자 본인이 자신의 글에 대해 start-chat을 호출하는 경우를 별도 차단하지 않는다.

## 14. 관련 문서

- [실종 제보 아키텍처](../architecture/missingpet/실종 제보 아키텍처.md)
- [MissingPet 백엔드 성능 최적화](../refactoring/missing-pet/missing-pet-backend-performance-optimization.md)
- [MissingPet N+1 쿼리 이슈](../troubleshooting/missing-pet/n-plus-one-query-issue.md)
- [MissingPet 성능 측정 결과](../troubleshooting/missing-pet/performance-measurement-results.md)
- [MissingPet orphanRemoval/soft delete 분석](../troubleshooting/missing-pet/orphanRemoval-soft-delete-analysis.md)
- [MissingPet 잠재 이슈](../troubleshooting/missing-pet/potential-issues.md)
