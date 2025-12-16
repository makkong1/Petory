# File 도메인 - 포트폴리오 상세 설명

## 1. 기능 설명

### 1.1 도메인 개요
- **역할**: 파일 업로드/다운로드 관리 도메인으로, 다양한 도메인에서 사용하는 파일을 통합 관리합니다.
- **주요 기능**: 
  - 파일 업로드/다운로드/삭제
  - 폴리모픽 관계로 다양한 대상 타입 지원
  - 파일 동기화 (기존 파일 삭제 후 새 파일 추가)
  - 배치 파일 조회 (N+1 문제 해결)

### 1.2 기능 시연
> **스크린샷/영상 링크**: [기능 작동 영상 또는 스크린샷 추가]

#### 주요 기능 1: 파일 업로드 및 관리
- **설명**: 게시글, 펫케어 요청, 실종 동물 등 다양한 대상에 파일을 첨부할 수 있습니다.
- **사용자 시나리오**: 
  1. 파일 업로드 (이미지, 문서 등)
  2. 파일 저장 (로컬 또는 S3)
  3. 파일 다운로드 URL 생성
  4. 파일 삭제
- **스크린샷/영상**: 

---

## 2. 서비스 로직 설명

### 2.1 핵심 비즈니스 로직

#### 로직 1: 파일 동기화
**구현 위치**: `AttachmentFileService.syncSingleAttachment()` (Lines 64-83)

**핵심 로직**:
- **기존 파일 삭제**: `deleteByTargetTypeAndTargetIdx()`로 기존 파일 삭제
- **파일 경로 정규화**: `normalizeFilePath()`로 경로 정규화 (URL 디코딩, 상대 경로 추출)
- **MIME 타입 해결**: `resolveMimeType()`로 파일 타입 자동 감지
- **새 파일 추가**: 정규화된 경로와 파일 타입으로 `AttachmentFile` 생성

#### 로직 2: 배치 파일 조회 (N+1 문제 해결)
**구현 위치**: `AttachmentFileService.getAttachmentsBatch()` (Lines 46-62)

**핵심 로직**:
- **배치 조회**: `findByTargetTypeAndTargetIdxIn()`로 여러 대상의 파일을 한 번에 조회
- **그룹화**: `targetIdx`별로 그룹화하여 Map 반환
- **다운로드 URL 생성**: `withDownloadUrl()`로 각 파일에 다운로드 URL 추가
- **효과**: N+1 문제 해결

---

## 3. 아키텍처 설명

### 3.1 도메인 구조
```
domain/file/
  ├── controller/
  │   └── FileController.java
  ├── service/
  │   ├── AttachmentFileService.java
  │   └── FileStorageService.java
  ├── entity/
  │   ├── AttachmentFile.java
  │   └── FileTargetType.java
  └── repository/
      └── AttachmentFileRepository.java
```

### 3.2 엔티티 구조

#### AttachmentFile (첨부 파일)
```java
@Entity
@Table(name = "file")
public class AttachmentFile {
    private Long idx;
    @Enumerated(EnumType.STRING)
    private FileTargetType targetType;      // 대상 타입 (BOARD, COMMENT, CARE_REQUEST, MISSING_PET, USER, PET 등)
    private Long targetIdx;                 // 대상 ID
    private String filePath;                // 파일 경로 (상대 경로)
    private String fileType;                // 파일 타입 (MIME 타입)
    private LocalDateTime createdAt;       // @PrePersist로 자동 설정
}
```

### 3.3 엔티티 관계도 (ERD)
```mermaid
erDiagram
    AttachmentFile -.->|폴리모픽| Board
    AttachmentFile -.->|폴리모픽| CareRequest
    AttachmentFile -.->|폴리모픽| MissingPetBoard
    AttachmentFile -.->|폴리모픽| Users
    AttachmentFile -.->|폴리모픽| Pet
```

---

## 4. 트러블슈팅

---

## 5. 성능 최적화

### 5.1 DB 최적화

#### 인덱스 전략
```sql
-- 대상별 파일 조회
CREATE INDEX idx_file_target 
ON file(target_type, target_idx);
```

### 5.2 애플리케이션 레벨 최적화

#### 배치 파일 조회
**구현 위치**: `AttachmentFileRepository.findByTargetTypeAndTargetIdxIn()` (실제 메서드명)

**효과**: N+1 문제 해결, 여러 대상의 파일을 한 번에 조회하여 성능 향상

---

## 6. 핵심 포인트 요약

### 기술적 하이라이트
1. **폴리모픽 관계**: 다양한 대상 타입 지원
2. **파일 동기화**: 기존 파일 삭제 후 새 파일 추가
3. **배치 조회**: N+1 문제 해결
