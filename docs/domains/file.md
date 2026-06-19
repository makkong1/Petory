# File 도메인

> 기준: 현재 `domain/file`, `domain/admin` 파일 관리 API, 프론트 `uploadApi/fileAdminApi` 코드.  
> 역할: 이미지 업로드, 로컬 저장소 조회, 도메인별 첨부파일 메타데이터 연결을 담당한다.

---

## 1. 도메인 책임

File 도메인은 실제 이미지 파일 저장과, 저장된 파일을 Petory의 여러 도메인 레코드에 연결하는 공통 도메인이다.

현재 구현은 S3가 아니라 **로컬 파일 시스템 저장**이다.

- 업로드 루트: `file.upload-dir`
- 기본값: `uploads`
- 공개 조회: `GET /api/uploads/file?path=...`
- 업로드: `POST /api/uploads/images`
- 첨부 메타데이터 테이블: `file`

파일 도메인은 두 레이어로 나뉜다.

1. `FileStorageService`
   - Multipart 이미지 검증
   - 업로드 디렉터리 생성
   - 고유 파일명 생성
   - 로컬 파일 저장
   - 저장 파일 Resource 로드

2. `AttachmentFileService`
   - 저장된 파일 경로를 특정 도메인 레코드에 연결
   - `FileTargetType + targetIdx` 폴리모픽 참조
   - 단일 첨부파일 교체
   - 대상별/배치 첨부파일 조회
   - 다운로드 URL 생성

---

## 2. 주요 코드 위치

| 영역 | 파일 |
|---|---|
| 업로드/조회 API | `domain/file/controller/FileUploadController.java` |
| 로컬 저장소 서비스 | `domain/file/service/FileStorageService.java` |
| 첨부 메타데이터 서비스 | `domain/file/service/AttachmentFileService.java` |
| 첨부 엔티티 | `domain/file/entity/AttachmentFile.java` |
| 대상 타입 enum | `domain/file/entity/FileTargetType.java` |
| Repository adapter | `domain/file/repository/JpaAttachmentFileAdapter.java` |
| Spring Data JPA | `domain/file/repository/SpringDataJpaAttachmentFileRepository.java` |
| 관리자 파일 API | `domain/admin/controller/AdminFileController.java` |
| 관리자 파일 facade | `domain/admin/service/AdminFileFacade.java` |
| 프론트 업로드 API | `frontend/src/api/uploadApi.js` |
| 프론트 관리자 API | `frontend/src/api/fileAdminApi.js` |

---

## 3. API

### 3.1 이미지 업로드

```http
POST /api/uploads/images
Content-Type: multipart/form-data
Authorization: Bearer <JWT>
```

파라미터:

| 이름 | 필수 | 설명 |
|---|---:|---|
| `file` | O | 업로드할 이미지 |
| `category` | X | 저장 하위 디렉터리. 예: `community`, `missing-pets`, `pets`, `chat` |
| `ownerType` | X | 예: `user`, `guest` |
| `ownerId` | X | 사용자 ID 등 |
| `entityId` | X | 연결 대상 엔티티 ID |

응답 예시:

```json
{
  "path": "community/user/1/20260619_abc123.jpg",
  "filename": "20260619_abc123.jpg",
  "url": "http://localhost:8080/api/uploads/file?path=community/user/1/20260619_abc123.jpg",
  "contentType": "image/jpeg",
  "size": 12345
}
```

`SecurityConfig` 기준으로 `GET /api/uploads/**`만 공개이고, 업로드 `POST`는 인증이 필요하다.

### 3.2 파일 조회

```http
GET /api/uploads/file?path={relativePath}
```

흐름:

1. `path`를 업로드 루트 기준 상대경로로 해석
2. `FileStorageService.resolveStoragePath()`에서 경로 정규화
3. 정규화된 경로가 `uploadLocation` 밖이면 거부
4. 파일이 존재하고 읽을 수 있으면 inline Resource 반환
5. content type은 `Files.probeContentType()`으로 감지

### 3.3 관리자 파일 API

```http
GET    /api/admin/files?targetType=&q=&page=&size=
GET    /api/admin/files/target?targetType=&targetIdx=
DELETE /api/admin/files/{id}
DELETE /api/admin/files/target?targetType=&targetIdx=
```

권한:

- `ADMIN`
- `MASTER`

삭제 시 `AdminAuditService`에 감사 로그를 남긴다.

주의: 프론트 `fileAdminApi.getStatistics()`는 `GET /api/admin/files/statistics`를 호출하지만, 현재 백엔드 `AdminFileController`에는 해당 엔드포인트가 없다.

---

## 4. 데이터 모델

### 4.1 AttachmentFile

테이블명은 `file`이다.

| 필드 | 설명 |
|---|---|
| `idx` | 파일 메타데이터 PK |
| `targetType` | 첨부 대상 도메인 타입 |
| `targetIdx` | 첨부 대상 레코드 ID |
| `filePath` | 업로드 루트 기준 상대경로 |
| `fileType` | MIME 타입 |
| `createdAt`, `updatedAt` | `BaseTimeEntity`에서 관리 |

현재 `AttachmentFile`은 `BaseTimeEntity`를 상속한다. 그래서 `file` 테이블에는 `updated_at` 컬럼이 필요하며, 이를 보정하는 migration이 있다.

### 4.2 FileTargetType

```java
BOARD
COMMENT
CARE_COMMENT
MISSING_PET
MISSING_PET_COMMENT
PET
```

현재 enum에는 `CARE_REQUEST`, `USER`, `CHAT` 타입이 없다.

---

## 5. 업로드 저장 규칙

### 5.1 저장 경로

`FileUploadController`는 `category`, `ownerType`, `ownerId`, `entityId`를 순서대로 path segment로 넘긴다.

예:

```text
category=missing-pets
ownerType=user
ownerId=3
entityId=10
```

저장 상대경로:

```text
missing-pets/user/3/10/20260619_<uuid>.jpg
```

`FileStorageService`는 각 segment를 `[a-zA-Z0-9._-]` 외 문자는 `_`로 치환하고, `..`도 `_`로 바꾼다.

### 5.2 파일명

```text
yyyyMMdd_<uuid-without-hyphen>.<extension>
```

예:

```text
20260619_7c47eaf620604a0d8d22c456b5e2a1fb.jpg
```

### 5.3 검증

| 항목 | 현재 기준 |
|---|---|
| 크기 | 최대 5MB |
| MIME type | `image/jpeg`, `image/png`, `image/gif`, `image/webp`, `image/jfif` |
| 확장자 | `.jpg`, `.jpeg`, `.png`, `.gif`, `.webp`, `.jfif` |
| 실제 이미지 | WebP 외에는 `ImageIO.read()` 성공 필요 |
| WebP | `RIFF/WEBP` magic bytes 확인 |

---

## 6. 첨부파일 연결 흐름

### 6.1 단일 첨부 교체

`AttachmentFileService.syncSingleAttachment(targetType, targetIdx, filePath, fileType)`는 기존 첨부 레코드를 모두 삭제하고 새 파일 1개를 저장한다.

흐름:

1. `targetType`, `targetIdx`가 없으면 return
2. 기존 `file` row 삭제
3. `filePath` 정규화
4. `fileType`이 없으면 실제 파일에서 MIME type 추정
5. 새 `AttachmentFile` 저장

이 메서드는 **단일 파일만 지원**한다. 다중 첨부 구조가 필요한 경우 별도 메서드가 필요하다.

### 6.2 경로 정규화

`normalizeFilePath()`는 다양한 입력을 업로드 루트 기준 상대경로로 바꾼다.

지원 형태:

- `/api/uploads/file?path=...`
- URL query `path=...`
- `/uploads/...`
- `uploads/...`
- 일반 상대경로

정규화 후 백슬래시는 `/`로 바꾼다.

과거에는 `uploads/` 접두사가 DB에 저장되어 `uploads/uploads/...`로 해석되는 문제가 있었고, 이를 보정하는 migration이 있다.

### 6.3 배치 조회

목록 API에서 N+1을 줄이기 위해 `getAttachmentsBatch(targetType, targetIndices)`를 사용한다.

흐름:

1. `findByTargetTypeAndTargetIdxIn()`
2. `targetIdx`별 grouping
3. 각 `FileDTO`에 `/api/uploads/file?path=...` 다운로드 URL 추가

Board, MissingPet, Comment, Pet 변환 흐름에서 사용된다.

---

## 7. 도메인 연동

| 도메인 | 연결 방식 |
|---|---|
| Board | 게시글 이미지 `FileTargetType.BOARD` |
| Board Comment | 댓글 이미지 `FileTargetType.COMMENT` |
| MissingPet | 실종 제보 대표 이미지 `FileTargetType.MISSING_PET` |
| MissingPet Comment | 실종 제보 댓글 이미지 `FileTargetType.MISSING_PET_COMMENT` |
| Care Comment | 케어 요청 댓글 첫 번째 첨부 `FileTargetType.CARE_COMMENT` |
| Pet | 반려동물 프로필 이미지 `FileTargetType.PET` |
| Chat | `uploadApi.uploadImage(category='chat')`로 파일은 저장하지만 `AttachmentFile` 레코드는 만들지 않고 이미지 URL을 메시지 content로 전송 |

---

## 8. 관리자 파일 관리

관리자 목록 조회:

- `targetType` 필터
- `q` keyword 필터: `filePath` 또는 `fileType` LIKE 검색
- `createdAt DESC` 정렬
- Page 응답

관리자 삭제:

- `DELETE /api/admin/files/{id}`는 `file` row 1개 삭제
- `DELETE /api/admin/files/target`은 특정 대상의 파일 row 전체 삭제
- 둘 다 감사 로그를 남긴다.

주의: 관리자 삭제는 현재 DB 메타데이터 삭제다. 로컬 디스크의 물리 파일 삭제는 수행하지 않는다.

---

## 9. 장애와 예외

| 상황 | 예외 |
|---|---|
| 빈 파일 | `FileValidationException.emptyFile()` |
| 빈 경로 | `FileValidationException.emptyPath()` |
| 업로드 루트 밖 경로 | `FileValidationException.invalidPath()` |
| 크기 초과 | `FileUploadValidationException.sizeExceeded()` |
| MIME type 불일치 | `FileUploadValidationException.invalidContentType()` |
| 확장자 불일치 | `FileUploadValidationException.invalidExtension()` |
| 디렉터리 초기화/생성 실패 | `FileStorageException` |
| 저장 실패 | `FileStorageException.saveFailed()` |
| 파일 없음 | `FileNotFoundException.forPath()` |

현재 `FileUploadController.serveFile()`은 `IllegalArgumentException`만 잡아 404를 반환한다. `FileNotFoundException`은 `ApiException` 계층으로 전파된다.

---

## 10. 현재 한계와 주의사항

- 저장소는 S3가 아니라 로컬 파일 시스템이다.
- 업로드는 이미지 전용이다. 일반 파일 업로드 API는 없다.
- `syncSingleAttachment()`는 단일 파일 교체만 지원한다.
- 첨부 메타데이터 삭제 시 물리 파일은 삭제하지 않는다.
- 업로드 직후 생성된 파일은 도메인 저장이 실패해도 물리 파일로 남을 수 있다.
- `AttachmentFile`은 폴리모픽 참조라 DB 외래키로 대상 존재를 강제하지 않는다.
- `FileTargetType` enum과 관리자 UI 옵션이 일부 맞지 않는다. UI에는 `CARE_REQUEST`, `USER`가 있지만 enum에는 없다.
- 프론트 `fileAdminApi.getStatistics()`는 백엔드 엔드포인트가 없다.
- Chat 이미지는 업로드 파일을 메시지 URL로만 사용하며 `file` 테이블과 연결되지 않는다.

---

## 11. DomainV2 페이지에 넣을 포인트

- 파일 저장과 도메인 첨부 메타데이터를 `FileStorageService`와 `AttachmentFileService`로 분리했다.
- 경로는 업로드 루트 기준 상대경로만 DB에 저장해 환경 의존성을 낮췄다.
- `FileTargetType + targetIdx` 폴리모픽 구조로 여러 도메인에서 공통 첨부파일 테이블을 사용한다.
- 목록 조회는 `findByTargetTypeAndTargetIdxIn()` 배치 조회로 N+1을 줄인다.
- 이미지 검증은 MIME/확장자뿐 아니라 실제 이미지 내용까지 확인한다.
- `uploads/` 접두사 이중 경로 문제를 migration과 normalize 로직으로 보정했다.
- 현재 한계는 물리 파일 lifecycle과 DB 메타데이터 lifecycle이 완전히 묶여 있지 않다는 점이다.
