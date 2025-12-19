# File 도메인 - 포트폴리오 상세 설명

## 1. 개요

File 도메인은 파일 업로드/다운로드 관리 도메인으로, 다양한 도메인에서 사용하는 파일을 통합 관리합니다. 폴리모픽 관계를 통해 게시글, 댓글, 펫케어 댓글, 실종 동물 게시글, 반려동물 프로필 등 다양한 대상에 파일을 첨부할 수 있습니다.

**주요 기능**:
- 파일 업로드/다운로드/삭제
- 폴리모픽 관계로 다양한 대상 타입 지원 (BOARD, COMMENT, CARE_COMMENT, MISSING_PET, MISSING_PET_COMMENT, PET)
- 파일 동기화 (기존 파일 삭제 후 새 파일 추가)
- 배치 파일 조회 (N+1 문제 해결)
- 파일 검증 및 보안 (크기 제한, 확장자 검증, 경로 정규화)
- 고유 파일명 생성 (날짜 + UUID)

---

## 2. 기능 설명

### 2.1 파일 업로드 및 관리

**파일 업로드 프로세스**:
1. 클라이언트에서 이미지 파일 업로드 (`POST /api/uploads/images`)
2. 파일 검증 (크기, 확장자, MIME 타입)
3. 디렉터리 구조 생성 (카테고리/소유자 타입/소유자 ID/엔티티 ID)
4. 고유 파일명 생성 (날짜 접두사 + UUID)
5. 파일 저장 및 상대 경로 반환
6. 다운로드 URL 생성

**파일 다운로드 프로세스**:
1. 클라이언트에서 상대 경로로 파일 요청 (`GET /api/uploads/file?path={상대경로}`)
2. 경로 정규화 및 보안 검증
3. 파일 리소스 반환

### 2.2 파일 동기화

**동기화 프로세스**:
- 기존 파일 삭제 후 새 파일 추가
- 파일 경로 정규화 (URL 디코딩, 상대 경로 추출)
- MIME 타입 자동 감지
- `AttachmentFile` 엔티티 생성 및 저장

### 2.3 배치 파일 조회

**배치 조회 프로세스**:
- 여러 대상의 파일을 한 번에 조회 (`findByTargetTypeAndTargetIdxIn`)
- `targetIdx`별로 그룹화하여 `Map<Long, List<FileDTO>>` 반환
- 각 파일에 다운로드 URL 자동 추가
- N+1 문제 해결

---

## 3. 서비스 로직 설명

### 3.1 핵심 비즈니스 로직

#### 로직 1: 파일 동기화
**구현 위치**: `AttachmentFileService.syncSingleAttachment()` 

```java
@Transactional
public void syncSingleAttachment(FileTargetType targetType, Long targetIdx, String filePath, String fileType) {
    if (targetType == null || targetIdx == null) {
        return;
    }

    // 1. 기존 파일 삭제
    fileRepository.deleteByTargetTypeAndTargetIdx(targetType, targetIdx);

    // 2. 파일 경로 정규화 (URL 디코딩, 상대 경로 추출)
    String normalizedPath = normalizeFilePath(filePath);
    if (StringUtils.hasText(normalizedPath)) {
        // 3. MIME 타입 해결 (제공된 타입 또는 파일 시스템에서 자동 감지)
        String resolvedFileType = resolveMimeType(normalizedPath, fileType);
        
        // 4. 새 파일 추가
        AttachmentFile attachment = AttachmentFile.builder()
                .targetType(targetType)
                .targetIdx(targetIdx)
                .filePath(normalizedPath)
                .fileType(resolvedFileType)
                .build();
        fileRepository.save(attachment);
    }
}
```

**핵심 로직**:
- **기존 파일 삭제**: `deleteByTargetTypeAndTargetIdx()`로 기존 파일 삭제
- **파일 경로 정규화**: `normalizeFilePath()`로 경로 정규화 (URL 디코딩, 상대 경로 추출)
- **MIME 타입 해결**: `resolveMimeType()`로 파일 타입 자동 감지
- **새 파일 추가**: 정규화된 경로와 파일 타입으로 `AttachmentFile` 생성

#### 로직 2: 배치 파일 조회 (N+1 문제 해결)
**구현 위치**: `AttachmentFileService.getAttachmentsBatch()`

```java
public Map<Long, List<FileDTO>> getAttachmentsBatch(FileTargetType targetType, List<Long> targetIndices) {
    if (targetType == null || targetIndices == null || targetIndices.isEmpty()) {
        return Collections.emptyMap();
    }
    
    // 1. 배치 조회: 여러 대상의 파일을 한 번에 조회
    List<AttachmentFile> files = fileRepository.findByTargetTypeAndTargetIdxIn(targetType, targetIndices);
    
    // 2. targetIdx별로 그룹화한 후 FileDTO로 변환
    return files.stream()
            .collect(Collectors.groupingBy(
                AttachmentFile::getTargetIdx,
                Collectors.mapping(
                    file -> withDownloadUrl(fileConverter.toDTO(file)),
                    Collectors.toList()
                )
            ));
}
```

**핵심 로직**:
- **배치 조회**: `findByTargetTypeAndTargetIdxIn()`로 여러 대상의 파일을 한 번에 조회
- **그룹화**: `targetIdx`별로 그룹화하여 `Map<Long, List<FileDTO>>` 반환
- **다운로드 URL 생성**: `withDownloadUrl()`로 각 파일에 다운로드 URL 추가
- **효과**: N+1 문제 해결, 여러 게시글의 파일을 한 번의 쿼리로 조회

#### 로직 3: 파일 저장 및 검증
**구현 위치**: `FileStorageService.storeImage()` 

**핵심 로직**:
- **파일 검증**: 최대 5MB, 이미지 파일만 허용 (jpg, png, gif, webp)
- **경로 생성**: 카테고리, 소유자 타입, 소유자 ID, 엔티티 ID를 기반으로 디렉터리 구조 생성
- **파일명 생성**: 날짜 접두사 + UUID로 고유 파일명 생성 (예: `20240101_abc123def456.jpg`)
- **보안**: 경로 정규화 및 상대 경로 검증으로 디렉터리 탐색 공격 방지

**파일 검증 로직** (`validateFile()`):
```java
private void validateFile(MultipartFile file, String extension) {
    // 1. 파일 크기 검증 (최대 5MB)
    if (file.getSize() > MAX_FILE_SIZE_BYTES) {
        throw new IllegalArgumentException("파일은 최대 5MB까지 업로드할 수 있습니다.");
    }
    
    // 2. MIME 타입 검증
    String contentType = file.getContentType();
    if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
        throw new IllegalArgumentException("이미지 파일(jpg, png, gif, webp)만 업로드할 수 있습니다.");
    }
    
    // 3. 확장자 검증
    if (!StringUtils.hasText(extension) || !ALLOWED_EXTENSIONS.contains(extension.toLowerCase())) {
        throw new IllegalArgumentException("허용되지 않은 이미지 확장자입니다.");
    }
}
```

#### 로직 4: 파일 경로 정규화
**구현 위치**: `AttachmentFileService.normalizeFilePath()` (Lines 93-104)

**핵심 로직**:
- **상대 경로 추출**: URI에서 `/uploads/` 마커 이후 경로 추출 또는 쿼리 파라미터에서 `path` 추출
- **URL 디코딩**: 인코딩된 경로 디코딩
- **경로 정규화**: 백슬래시를 슬래시로 변환 (`\` → `/`)

---

## 4. 아키텍처 설명

### 4.1 엔티티 구조

#### AttachmentFile (첨부 파일)
```java
@Entity
@Table(name = "file")
public class AttachmentFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32)
    private FileTargetType targetType;      // 대상 타입 (BOARD, COMMENT, CARE_COMMENT, MISSING_PET, MISSING_PET_COMMENT, PET)
    
    @Column(name = "target_idx", nullable = false)
    private Long targetIdx;                 // 대상 ID
    
    @Column(name = "file_path", nullable = false, length = 255)
    private String filePath;                // 파일 경로 (상대 경로)
    
    @Column(name = "file_type", length = 50)
    private String fileType;                // 파일 타입 (MIME 타입)
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;       // @PrePersist로 자동 설정
    
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
```

**특징**:
- `BaseTimeEntity`를 상속하지 않음 (`@PrePersist`로 직접 `createdAt` 관리)
- 폴리모픽 관계로 다양한 대상 타입 지원

#### FileTargetType (파일 대상 타입)
```java
public enum FileTargetType {
    BOARD,                  // 게시글
    COMMENT,                // 댓글
    CARE_COMMENT,           // 펫케어 댓글
    MISSING_PET,            // 실종 동물 게시글
    MISSING_PET_COMMENT,    // 실종 동물 댓글
    PET                     // 반려동물 프로필
}
```

### 4.2 도메인 구조
```
domain/file/
  ├── controller/
  │   └── FileUploadController.java
  ├── service/
  │   ├── AttachmentFileService.java
  │   └── FileStorageService.java
  ├── entity/
  │   ├── AttachmentFile.java
  │   └── FileTargetType.java
  ├── repository/
  │   └── AttachmentFileRepository.java
  ├── dto/
  │   └── FileDTO.java
  └── converter/
      └── FileConverter.java
```

### 4.3 엔티티 관계도 (ERD)
```mermaid
erDiagram
    AttachmentFile -.->|폴리모픽| Board
    AttachmentFile -.->|폴리모픽| Comment
    AttachmentFile -.->|폴리모픽| CareRequestComment
    AttachmentFile -.->|폴리모픽| MissingPetBoard
    AttachmentFile -.->|폴리모픽| MissingPetComment
    AttachmentFile -.->|폴리모픽| Pet
```

### 4.4 서비스 메서드 구조

#### AttachmentFileService
| 메서드 | 설명 | 주요 로직 |
|--------|------|-----------|
| `getAttachments()` | 단일 대상의 파일 목록 조회 | `findByTargetTypeAndTargetIdx()`로 조회, 다운로드 URL 추가 |
| `getAttachmentsBatch()` | 여러 대상의 파일 목록 배치 조회 | `findByTargetTypeAndTargetIdxIn()`로 배치 조회, targetIdx별 그룹화, N+1 문제 해결 |
| `syncSingleAttachment()` | 파일 동기화 (기존 파일 삭제 후 새 파일 추가) | 기존 파일 삭제, 경로 정규화, MIME 타입 해결, 새 파일 추가 |
| `deleteAll()` | 대상의 모든 파일 삭제 | `deleteByTargetTypeAndTargetIdx()`로 삭제 |
| `normalizeFilePath()` | 파일 경로 정규화 | 상대 경로 추출, URL 디코딩, 경로 정규화 |
| `buildDownloadUrl()` | 다운로드 URL 생성 | `/api/uploads/file?path={상대경로}` 형식으로 생성 |

#### FileStorageService
| 메서드 | 설명 | 주요 로직 |
|--------|------|-----------|
| `storeImage()` | 이미지 파일 저장 | 파일 검증, 디렉터리 생성, 고유 파일명 생성, 파일 저장 |
| `loadAsResource()` | 파일 리소스 로드 | 경로 검증, 파일 리소스 반환 |
| `resolveStoragePath()` | 저장 경로 해석 | 상대 경로를 절대 경로로 변환, 보안 검증 |

### 4.5 API 설계

#### REST API
| 엔드포인트 | Method | 설명 |
|-----------|--------|------|
| `/api/uploads/images` | POST | 이미지 파일 업로드 (file, category, ownerType, ownerId, entityId 파라미터) |
| `/api/uploads/file` | GET | 파일 다운로드 (path 파라미터) |

**파일 업로드 요청 예시**:
```http
POST /api/uploads/images
Content-Type: multipart/form-data

file: [이미지 파일]
category: board
ownerType: user
ownerId: 123
entityId: 456
```

**파일 업로드 응답 예시**:
```json
{
  "path": "uploads/board/user/123/456/20240101_abc123def456.jpg",
  "filename": "20240101_abc123def456.jpg",
  "url": "http://localhost:8080/api/uploads/file?path=uploads/board/user/123/456/20240101_abc123def456.jpg",
  "contentType": "image/jpeg",
  "size": 123456
}
```

**파일 다운로드 요청 예시**:
```http
GET /api/uploads/file?path=uploads/board/user/123/456/20240101_abc123def456.jpg
```

**파일 다운로드 응답**: 이미지 바이너리 (Content-Type: image/jpeg)

---

## 5. 트랜잭션 처리

### 5.1 트랜잭션 전략
- **파일 동기화**: `@Transactional` - 기존 파일 삭제와 새 파일 추가를 원자적으로 처리
- **파일 삭제**: `@Transactional` - 파일 삭제를 원자적으로 처리
- **조회 메서드**: `@Transactional(readOnly = true)` - 읽기 전용 최적화
- **파일 저장**: `FileStorageService.storeImage()`는 트랜잭션 없이 처리 (파일 시스템 작업)

### 5.2 동시성 제어
- **파일 동기화**: 트랜잭션으로 기존 파일 삭제와 새 파일 추가를 원자적으로 처리하여 동시성 문제 방지
- **파일명 충돌 방지**: 날짜 접두사 + UUID 조합으로 고유 파일명 생성

---

## 6. 트러블슈팅

---

## 7. 성능 최적화

### 7.1 DB 최적화

#### 인덱스 전략
```sql
-- 대상별 파일 조회
CREATE INDEX idx_file_target 
ON file(target_type, target_idx);
```

**선정 이유**:
- 자주 조회되는 컬럼 조합 (`target_type`, `target_idx`)
- WHERE 절에서 자주 사용되는 조건
- 배치 조회 쿼리 (`findByTargetTypeAndTargetIdxIn`) 최적화

### 7.2 애플리케이션 레벨 최적화

#### 배치 파일 조회
**구현 위치**: `AttachmentFileRepository.findByTargetTypeAndTargetIdxIn()`

**효과**: N+1 문제 해결, 여러 대상의 파일을 한 번에 조회하여 성능 향상

**사용 예시**:
```java
// N+1 문제 발생 코드 (비효율적)
List<Board> boards = boardRepository.findAll();
for (Board board : boards) {
    List<FileDTO> files = attachmentFileService.getAttachments(FileTargetType.BOARD, board.getIdx());
    // 각 board마다 쿼리 실행
}

// 배치 조회 사용 (효율적)
List<Board> boards = boardRepository.findAll();
List<Long> boardIndices = boards.stream().map(Board::getIdx).collect(Collectors.toList());
Map<Long, List<FileDTO>> filesMap = attachmentFileService.getAttachmentsBatch(FileTargetType.BOARD, boardIndices);
// 한 번의 쿼리로 모든 파일 조회
```

#### 파일 검증 및 보안
**구현 위치**: `FileStorageService.validateFile()` (Lines 150-161)

**최적화 사항**:
- 파일 크기 제한: 최대 5MB
- 허용 확장자: `.jpg`, `.jpeg`, `.png`, `.gif`, `.webp`, `.jfif`
- MIME 타입 검증: `image/jpeg`, `image/png`, `image/gif`, `image/webp`
- 경로 정규화: 디렉터리 탐색 공격 방지 (`../` 제거)

**보안 검증 로직** (`resolveStoragePath()`, Lines 95-104):
```java
public Path resolveStoragePath(String relativePath) {
    if (!StringUtils.hasText(relativePath)) {
        throw new IllegalArgumentException("파일 경로가 비어 있습니다.");
    }
    Path filePath = uploadLocation.resolve(relativePath).normalize();
    // 상대 경로가 업로드 디렉터리를 벗어나지 않도록 검증
    if (!filePath.startsWith(uploadLocation)) {
        throw new IllegalArgumentException("잘못된 파일 경로 요청입니다.");
    }
    return filePath;
}
```

---

## 8. 핵심 포인트 요약

### 8.1 폴리모픽 관계
- **다양한 대상 타입 지원**: BOARD, COMMENT, CARE_COMMENT, MISSING_PET, MISSING_PET_COMMENT, PET
- **유연한 파일 관리**: 하나의 엔티티로 다양한 도메인의 파일을 통합 관리

### 8.2 파일 동기화
- **기존 파일 삭제 후 새 파일 추가**: `syncSingleAttachment()`로 파일 동기화
- **경로 정규화**: URL 디코딩, 상대 경로 추출로 일관된 경로 관리
- **MIME 타입 자동 감지**: 제공된 타입 또는 파일 시스템에서 자동 감지

### 8.3 성능 최적화
- **배치 조회**: `getAttachmentsBatch()`로 N+1 문제 해결
- **인덱스 전략**: `(target_type, target_idx)` 복합 인덱스로 조회 성능 향상
- **고유 파일명**: 날짜 + UUID 조합으로 파일명 충돌 방지

### 8.4 보안
- **파일 검증**: 크기 제한(5MB), 확장자 및 MIME 타입 검증
- **경로 정규화**: 디렉터리 탐색 공격 방지 (`../` 제거)
- **상대 경로 검증**: 업로드 디렉터리를 벗어나지 않도록 검증

### 8.5 엔티티 설계 특징
- **BaseTimeEntity 미사용**: `@PrePersist`로 직접 `createdAt` 관리
- **폴리모픽 관계**: `targetType`과 `targetIdx`로 다양한 대상 타입 지원
- **상대 경로 저장**: 파일 경로를 상대 경로로 저장하여 이식성 향상
