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
```java
// AttachmentFileService.java
public void syncSingleAttachment(
    FileTargetType targetType, Long targetIdx, 
    String newFilePath, String oldFilePath) {
    
    // 기존 파일 삭제
    if (oldFilePath != null) {
        List<AttachmentFile> oldFiles = fileRepository
            .findByTargetTypeAndTargetIdx(targetType, targetIdx);
        for (AttachmentFile file : oldFiles) {
            fileStorageService.deleteFile(file.getFilePath());
            fileRepository.delete(file);
        }
    }
    
    // 새 파일 추가
    if (newFilePath != null) {
        AttachmentFile newFile = AttachmentFile.builder()
            .targetType(targetType)
            .targetIdx(targetIdx)
            .filePath(newFilePath)
            .build();
        fileRepository.save(newFile);
    }
}
```

**설명**:
- **처리 흐름**: 기존 파일 삭제 → 새 파일 추가
- **주요 판단 기준**: 기존 파일 경로와 새 파일 경로 비교

#### 로직 2: 배치 파일 조회 (N+1 문제 해결)
```java
// AttachmentFileService.java
public Map<Long, List<FileDTO>> getAttachmentsBatch(
    FileTargetType targetType, List<Long> targetIds) {
    
    List<AttachmentFile> files = fileRepository
        .findByTargetTypeAndTargetIds(targetType, targetIds);
    
    return files.stream()
        .collect(Collectors.groupingBy(
            AttachmentFile::getTargetIdx,
            Collectors.mapping(converter::toDTO, Collectors.toList())
        ));
}
```

**설명**:
- **처리 흐름**: 여러 대상의 파일을 한 번에 조회 → Map으로 그룹화
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
    private FileTargetType targetType;      // 대상 타입 (BOARD, COMMENT, CARE_REQUEST, MISSING_PET, USER, PET 등)
    private Long targetIdx;                 // 대상 ID
    private String filePath;                // 파일 경로
    private String fileType;                // 파일 타입
    private LocalDateTime createdAt;
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
```java
// N+1 문제 해결
@Query("SELECT f FROM AttachmentFile f " +
       "WHERE f.targetType = :targetType AND f.targetIdx IN :targetIds")
List<AttachmentFile> findByTargetTypeAndTargetIds(
    @Param("targetType") FileTargetType targetType,
    @Param("targetIds") List<Long> targetIds
);
```

---

## 6. 핵심 포인트 요약

### 기술적 하이라이트
1. **폴리모픽 관계**: 다양한 대상 타입 지원
2. **파일 동기화**: 기존 파일 삭제 후 새 파일 추가
3. **배치 조회**: N+1 문제 해결

### 학습한 점
- 폴리모픽 관계 설계
- 파일 관리 전략
- 배치 조회 최적화

### 개선 가능한 부분
- S3 연동: 클라우드 스토리지 활용
- 이미지 리사이징: 업로드 시 자동 리사이징
- CDN 연동: 빠른 다운로드 속도
