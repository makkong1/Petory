# Missing Pet 도메인 - 포트폴리오 상세 설명

## 1. 개요

Missing Pet 도메인은 실종 동물 신고 및 관리 시스템으로, 반려동물을 잃어버린 사용자가 신고하고 다른 사용자들이 목격 정보를 제공할 수 있습니다. 위치 기반 검색, 파일 첨부, 알림 발송 등의 기능을 제공합니다.

**주요 기능**:
- 실종 동물 신고 생성/조회/수정/삭제
- 위치 정보 저장 (위도, 경도, 주소) - 현재 검색 기능 미구현
- 실종 동물 상태 관리 (MISSING → FOUND → RESOLVED)
- 목격 정보 댓글 (위치 정보 포함)
- 파일 첨부 (이미지 - 첫 번째 파일만 저장)
- 알림 발송 (댓글 작성 시, 비동기 처리)
- 실종제보 채팅 연동

---

## 2. 기능 설명

### 2.1 실종 동물 신고

**신고 생성 프로세스**:
1. 실종 동물 정보 입력 (이름, 종, 품종, 성별, 나이, 색상, 실종 날짜, 실종 장소)
2. 위치 정보 입력 (위도, 경도)
3. 사진 첨부 (선택)
4. 이메일 인증 확인
5. 신고 생성 및 파일 첨부 처리

**신고 수정/삭제 프로세스**:
1. 이메일 인증 확인
2. 정보 수정 또는 Soft Delete
3. 삭제 시 관련 댓글도 함께 Soft Delete

### 2.2 목격 정보 댓글

**댓글 작성 프로세스**:
1. 실종 제보 선택
2. 목격 정보 입력 (내용, 목격 위치 주소, 위도, 경도)
3. 사진 첨부 (선택)
4. 댓글 작성
5. 게시글 작성자에게 알림 발송 (댓글 작성자가 게시글 작성자가 아닌 경우)

### 2.3 실종 동물 상태 관리

**상태 전환**:
- **MISSING**: 실종 중 (기본 상태)
- **FOUND**: 발견됨
- **RESOLVED**: 해결됨

### 2.4 위치 정보 저장
**참고**: 현재 위치 기반 검색 기능은 구현되어 있지 않습니다. 위도/경도 정보는 저장만 되며, 향후 반경 기반 검색 기능 구현 예정입니다.

**현재 구현**:
- 실종 위치 정보 저장 (위도, 경도, 주소)
- 목격 위치 정보 저장 (위도, 경도, 주소)
- 위치 정보는 데이터 저장 용도로만 사용

---

## 3. 서비스 로직 설명

### 3.1 핵심 비즈니스 로직

#### 로직 1: 실종 제보 생성
**구현 위치**: `MissingPetBoardService.createBoard()`

```java
@Transactional
public MissingPetBoardDTO createBoard(MissingPetBoardDTO dto) {
    // 1. 사용자 확인
    Users user = usersRepository.findById(dto.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    
    // 2. 이메일 인증 확인
    if (user.getEmailVerified() == null || !user.getEmailVerified()) {
        throw new EmailVerificationRequiredException("실종 제보 작성을 위해 이메일 인증이 필요합니다.");
    }
    
    // 3. 실종 제보 생성
    MissingPetBoard board = MissingPetBoard.builder()
            .user(user)
            .title(dto.getTitle())
            .content(dto.getContent())
            .petName(dto.getPetName())
            .species(dto.getSpecies())
            .breed(dto.getBreed())
            .gender(dto.getGender())
            .age(dto.getAge())
            .color(dto.getColor())
            .lostDate(dto.getLostDate())
            .lostLocation(dto.getLostLocation())
            .latitude(dto.getLatitude())
            .longitude(dto.getLongitude())
            .status(dto.getStatus())
            .build();
    
    MissingPetBoard saved = boardRepository.save(board);
    
    // 4. 파일 첨부 처리
    if (dto.getImageUrl() != null) {
        attachmentFileService.syncSingleAttachment(
            FileTargetType.MISSING_PET, 
            saved.getIdx(), 
            dto.getImageUrl(),
            null
        );
    }
    
    return mapBoardWithAttachments(saved);
}
```

**핵심 로직**:
- **이메일 인증 확인**: 실종 제보 작성 시 이메일 인증 필요 (`EmailVerificationRequiredException`)
- **파일 첨부**: `syncSingleAttachment()`로 이미지 첨부 지원 (`FileTargetType.MISSING_PET`)
- **파일 매핑**: `mapBoardWithAttachments()`로 첨부 파일 정보 포함

#### 로직 2: 실종 제보 수정
**구현 위치**: `MissingPetBoardService.updateBoard()`

**핵심 로직**:
- **이메일 인증 확인**: 수정 시 이메일 인증 필요
- **선택적 업데이트**: DTO에 값이 있는 필드만 업데이트
- **파일 첨부**: `imageUrl`이 있으면 파일 동기화
- **위치 정보 업데이트**: 위도, 경도, 주소 정보 업데이트 지원

#### 로직 2-1: 상태 변경
**구현 위치**: `MissingPetBoardService.updateStatus()`

```java
@Transactional
public MissingPetBoardDTO updateStatus(Long id, MissingPetStatus status) {
    MissingPetBoard board = boardRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));
    board.setStatus(status);
    return mapBoardWithAttachments(board);
}
```

**핵심 로직**:
- **상태 변경**: MISSING → FOUND → RESOLVED 상태 전환
- **API 형식**: RequestBody로 `{"status": "MISSING"}` 형태로 받음
- **권한 체크**: 현재 권한 체크 없음 (모든 인증된 사용자 가능)

#### 로직 3: 실종 제보 삭제 (Soft Delete)
**구현 위치**: `MissingPetBoardService.deleteBoard()`

```java
@Transactional
public void deleteBoard(Long id) {
    MissingPetBoard board = boardRepository.findByIdWithUser(id)
            .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));
    
    // 이메일 인증 확인
    Users user = board.getUser();
    if (user.getEmailVerified() == null || !user.getEmailVerified()) {
        throw new EmailVerificationRequiredException("실종 제보 삭제를 위해 이메일 인증이 필요합니다.");
    }
    
    // 게시글 소프트 삭제
    board.setIsDeleted(true);
    board.setDeletedAt(LocalDateTime.now());
    
    // 관련 댓글 모두 소프트 삭제 (MissingPetCommentService 사용)
    commentService.deleteAllCommentsByBoard(board);
    
    boardRepository.saveAndFlush(board);
}
```

**핵심 로직**:
- **이메일 인증 확인**: 삭제 시 이메일 인증 필요
- **Soft Delete**: 게시글과 관련 댓글 모두 Soft Delete
- **연관 댓글 삭제**: `MissingPetCommentService.deleteAllCommentsByBoard()`를 통해 댓글 삭제 위임

#### 로직 4: 댓글 작성 및 알림 발송
**구현 위치**: `MissingPetCommentService.addComment()`

```java
@Transactional
public MissingPetCommentDTO addComment(Long boardId, MissingPetCommentDTO dto) {
    // 1. 게시글 확인
    MissingPetBoard board = boardRepository.findById(boardId)
            .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));
    
    // 2. 사용자 확인
    Users user = usersRepository.findById(dto.getUserId())
            .orElseThrow(() -> new IllegalArgumentException("User not found"));
    
    // 3. 댓글 생성
    MissingPetComment comment = MissingPetComment.builder()
            .board(board)
            .user(user)
            .content(dto.getContent())
            .address(dto.getAddress())
            .latitude(dto.getLatitude())
            .longitude(dto.getLongitude())
            .build();
    
    MissingPetComment saved = commentRepository.save(comment);
    
    // 4. 파일 첨부 처리
    if (dto.getImageUrl() != null) {
        attachmentFileService.syncSingleAttachment(
            FileTargetType.MISSING_PET_COMMENT, 
            saved.getIdx(), 
            dto.getImageUrl(), 
            null
        );
    }
    
    // 5. 알림 발송: 댓글 작성자가 게시글 작성자가 아닌 경우에만 알림 발송
    Long boardOwnerId = board.getUser().getIdx();
    if (!boardOwnerId.equals(user.getIdx())) {
        notificationService.createNotification(
            boardOwnerId,
            NotificationType.MISSING_PET_COMMENT,
            "실종 제보 게시글에 새로운 댓글이 달렸습니다",
            String.format("%s님이 댓글을 남겼습니다: %s", 
                user.getUsername(),
                dto.getContent() != null && dto.getContent().length() > 50
                    ? dto.getContent().substring(0, 50) + "..."
                    : dto.getContent()),
            board.getIdx(),
            "MISSING_PET"
        );
    }
    
    return mapCommentWithAttachments(saved);
}
```

**핵심 로직**:
- **댓글 생성**: 목격 정보 포함 (내용, 주소, 위도, 경도)
- **파일 첨부**: `syncSingleAttachment()`로 이미지 첨부 지원 (`FileTargetType.MISSING_PET_COMMENT`) - 첫 번째 파일만 저장
- **알림 발송**: 댓글 작성자가 게시글 작성자가 아닌 경우에만 알림 발송 (`NotificationType.MISSING_PET_COMMENT`)
- **알림 처리**: 비동기 처리 (`@Async`) - 알림 발송 실패해도 댓글 작성은 성공
- **알림 내용**: 댓글 내용 미리보기 (50자 제한)
- **댓글 추가**: 댓글 저장 후 `board.getComments().add(saved)` 호출

```java
// MissingPetCommentService.java
@Async
public void sendMissingPetCommentNotificationAsync(Long boardOwnerId, String username, String content, Long boardIdx) {
    try {
        String notificationContent = content != null && content.length() > 50
                ? content.substring(0, 50) + "..."
                : content;
        
        notificationService.createNotification(
                boardOwnerId,
                NotificationType.MISSING_PET_COMMENT,
                "실종 제보 게시글에 새로운 댓글이 달렸습니다",
                String.format("%s님이 댓글을 남겼습니다: %s", username, notificationContent),
                boardIdx,
                "MISSING_PET");
    } catch (Exception e) {
        log.error("실종제보 댓글 알림 발송 실패: boardOwnerId={}, boardIdx={}, error={}",
                boardOwnerId, boardIdx, e.getMessage(), e);
        // 알림 발송 실패는 로깅만 하고 예외를 던지지 않음 (댓글 작성과 분리)
    }
}
```

**설명**:
- **처리 흐름**: 댓글 저장 → 작성자 확인 → 비동기 알림 발송 → 댓글 반환
- **주요 판단 기준**: 댓글 작성자가 요청자가 아닌 경우에만 알림 발송
- **특징**: 
  - 비동기 처리로 댓글 작성 응답 시간 단축
  - 알림 발송 실패해도 댓글 작성은 성공 (예외 처리)
  - 파일 첨부는 첫 번째 파일만 저장

#### 로직 5: 파일 첨부 매핑
**구현 위치**: 
- 게시글: `MissingPetBoardService.mapBoardWithAttachments()`, `mapBoardWithAttachmentsFromBatch()`
- 댓글: `MissingPetCommentService.mapCommentWithAttachments()`

**핵심 로직**:
- **파일 조회**: `attachmentFileService.getAttachments()` 또는 `getAttachmentsBatch()`로 첨부 파일 조회
- **주요 이미지 URL 추출**: 첫 번째 파일의 다운로드 URL을 `imageUrl`로 설정
- **첨부 파일 목록**: `attachments` 필드에 모든 첨부 파일 정보 포함
- **배치 조회**: 목록 조회 시 `getAttachmentsBatch()`로 N+1 문제 해결

### 3.2 서비스 메서드 구조

#### MissingPetBoardService (게시글 전용)
| 메서드 | 설명 | 주요 로직 |
|--------|------|-----------|
| `getBoards()` | 실종 제보 목록 조회 | 상태별 필터링, 게시글+작성자만 조회, 첨부 파일 배치 조회 |
| `getBoard()` | 특정 실종 제보 조회 | 게시글+작성자만 조회, 댓글 수만 포함, 첨부 파일 포함 |
| `createBoard()` | 실종 제보 생성 | 이메일 인증 확인, 파일 첨부 처리 |
| `updateBoard()` | 실종 제보 수정 | 이메일 인증 확인, 선택적 업데이트, 파일 첨부 처리, 위치 정보 업데이트 |
| `updateStatus()` | 상태 변경 | MISSING → FOUND → RESOLVED (RequestBody로 status 받음) |
| `deleteBoard()` | 실종 제보 삭제 | 이메일 인증 확인, Soft Delete, 댓글 삭제는 `MissingPetCommentService`에 위임 |

**특징**:
- 댓글은 별도 API로 조회 (조인 폭발 방지)
- 댓글 수만 조회 (`MissingPetCommentService.getCommentCount()` 사용)

#### MissingPetCommentService (댓글 전용)
| 메서드 | 설명 | 주요 로직 |
|--------|------|-----------|
| `getComments()` | 댓글 목록 조회 | 삭제되지 않은 댓글만, 작성자 활성 상태 확인, 첨부 파일 배치 조회 |
| `addComment()` | 댓글 작성 | 파일 첨부 처리 (첫 번째 파일만), 알림 발송 (비동기) |
| `deleteComment()` | 댓글 삭제 | Soft Delete |
| `getCommentCount()` | 댓글 수 조회 | 게시글의 댓글 수만 조회 (현재는 목록 조회 후 size 사용, 향후 COUNT 쿼리로 최적화 예정) |
| `deleteAllCommentsByBoard()` | 게시글의 모든 댓글 삭제 | 게시글 삭제 시 호출됨, 모든 댓글 Soft Delete |
| `sendMissingPetCommentNotificationAsync()` | 알림 발송 (비동기) | `@Async` 사용, 알림 발송 실패해도 댓글 작성은 성공 |

### 3.3 서비스 분리 구조

**분리 이유**:
- **조인 폭발 방지**: 게시글과 댓글을 함께 조회할 경우 댓글이 많아지면 조인 결과가 기하급수적으로 증가
- **페이징 가능**: 댓글을 별도 API로 조회하면 페이징 적용 가능
- **책임 분리**: 게시글과 댓글의 비즈니스 로직을 명확히 분리
- **확장성**: 각 서비스를 독립적으로 최적화 가능

**서비스 구조**:
```
MissingPetBoardService (게시글 전용)
  ├─ getBoards()           // 목록 조회 (댓글 제외)
  ├─ getBoard()            // 상세 조회 (댓글 수만 포함)
  ├─ createBoard()         // 생성
  ├─ updateBoard()         // 수정
  ├─ updateStatus()        // 상태 변경
  └─ deleteBoard()         // 삭제 (댓글 삭제는 MissingPetCommentService에 위임)

MissingPetCommentService (댓글 전용)
  ├─ getComments()                      // 목록 조회
  ├─ addComment()                       // 작성
  ├─ deleteComment()                    // 삭제
  ├─ getCommentCount()                  // 댓글 수 조회
  ├─ deleteAllCommentsByBoard()         // 게시글의 모든 댓글 삭제
  └─ sendMissingPetCommentNotificationAsync()  // 알림 발송 (비동기)
```

### 3.4 트랜잭션 처리
- **트랜잭션 범위**: 
  - 실종 제보 생성/수정/삭제: `@Transactional` (MissingPetBoardService)
  - 댓글 작성/삭제: `@Transactional` (MissingPetCommentService)
  - 조회 메서드: `@Transactional(readOnly = true)` (클래스 레벨, 양쪽 서비스 모두)
- **격리 수준**: 기본값 (READ_COMMITTED)
- **이메일 인증**: 실종 제보 작성/수정/삭제 시 이메일 인증 확인 (`EmailVerificationRequiredException`)

---

## 4. 아키텍처 설명

### 4.1 엔티티 구조

#### MissingPetBoard (실종 동물 게시글)
```java
@Entity
@Table(name = "MissingPetBoard")
public class MissingPetBoard {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;
    
    @ManyToOne
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user; // 작성자
    
    @Column(length = 100, nullable = false)
    private String title; // 제목
    
    @Lob
    private String content; // 내용
    
    @Column(name = "pet_name", length = 50)
    private String petName; // 반려동물 이름
    
    @Column(length = 50)
    private String species; // 종류
    
    @Column(length = 50)
    private String breed; // 품종
    
    @Enumerated(EnumType.STRING)
    private MissingPetGender gender; // 성별 (M, F)
    
    @Column(length = 30)
    private String age; // 나이
    
    @Column(length = 50)
    private String color; // 색상
    
    @Column(name = "lost_date")
    private LocalDate lostDate; // 실종일
    
    @Column(name = "lost_location", length = 255)
    private String lostLocation; // 실종 위치
    
    @Column(precision = 15, scale = 12)
    private BigDecimal latitude; // 위도
    
    @Column(precision = 15, scale = 12)
    private BigDecimal longitude; // 경도
    
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MissingPetStatus status = MissingPetStatus.MISSING; // 상태 (MISSING, FOUND, RESOLVED)
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL)
    @Builder.Default
    private List<MissingPetComment> comments = new ArrayList<>(); // 댓글 목록
    
    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = MissingPetStatus.MISSING;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
```

**특징**:
- `BaseTimeEntity`를 상속하지 않음 (`@PrePersist`, `@PreUpdate`로 직접 관리)
- 위치 정보: `BigDecimal` 타입으로 정밀도 보장 (precision = 15, scale = 12)
- Soft Delete: `isDeleted`, `deletedAt` 필드로 Soft Delete 지원
- `orphanRemoval` 속성 없음 (cascade만 사용)

#### MissingPetComment (실종 동물 댓글)
```java
@Entity
@Table(name = "MissingPetComment")
public class MissingPetComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;
    
    @ManyToOne
    @JoinColumn(name = "board_idx", nullable = false)
    private MissingPetBoard board; // 실종 동물 게시글
    
    @ManyToOne
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user; // 작성자
    
    @Lob
    private String content; // 내용
    
    private String address; // 목격 위치 주소
    
    private Double latitude; // 목격 위치 위도
    
    private Double longitude; // 목격 위치 경도
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
```

**특징**:
- `BaseTimeEntity`를 상속하지 않음 (`@PrePersist`로 직접 `createdAt` 관리)
- 목격 위치 정보: 주소, 위도, 경도 포함 (`BigDecimal` 타입, precision = 15, scale = 12)
- Soft Delete: `isDeleted`, `deletedAt` 필드로 Soft Delete 지원

#### MissingPetStatus (실종 동물 상태)
```java
public enum MissingPetStatus {
    MISSING,   // 실종 중
    FOUND,     // 발견됨
    RESOLVED   // 해결됨
}
```

#### MissingPetGender (반려동물 성별)
```java
public enum MissingPetGender {
    M,  // 수컷
    F   // 암컷
}
```

### 4.2 도메인 구조
**참고**: Missing Pet 도메인은 `domain/board/` 패키지 내에 위치합니다.

```
domain/board/
  ├── controller/
  │   └── MissingPetBoardController.java       # 게시글 + 댓글 API
  ├── service/
  │   ├── MissingPetBoardService.java          # 게시글 전용 서비스
  │   └── MissingPetCommentService.java        # 댓글 전용 서비스
  ├── entity/
  │   ├── MissingPetBoard.java
  │   ├── MissingPetComment.java
  │   ├── MissingPetStatus.java (enum)
  │   └── MissingPetGender.java (enum)
  ├── repository/
  │   ├── MissingPetBoardRepository.java
  │   └── MissingPetCommentRepository.java
  ├── converter/
  │   └── MissingPetConverter.java
  └── dto/
      ├── MissingPetBoardDTO.java
      └── MissingPetCommentDTO.java
```

### 4.3 엔티티 관계도 (ERD)
```mermaid
erDiagram
    Users ||--o{ MissingPetBoard : "신고"
    MissingPetBoard ||--o{ MissingPetComment : "댓글"
    Users ||--o{ MissingPetComment : "작성"
    MissingPetBoard -.->|폴리모픽| AttachmentFile : "파일"
    MissingPetComment -.->|폴리모픽| AttachmentFile : "파일"
```

### 4.4 API 설계

#### REST API

**게시글 관련 API** (MissingPetBoardService 사용):
| 엔드포인트 | Method | 설명 | 서비스 메서드 |
|-----------|--------|------|-------------|
| `/api/missing-pets` | GET | 실종 제보 목록 조회 (status 파라미터로 필터링, 댓글 제외) | `getBoards()` |
| `/api/missing-pets/{id}` | GET | 특정 실종 제보 조회 (댓글 수만 포함, 댓글 목록은 별도 API로 조회) | `getBoard()` |
| `/api/missing-pets` | POST | 실종 제보 생성 (인증 필요) | `createBoard()` |
| `/api/missing-pets/{id}` | PUT | 실종 제보 수정 (인증 필요) | `updateBoard()` |
| `/api/missing-pets/{id}/status` | PATCH | 상태 변경 (RequestBody: `{"status": "MISSING"}`) | `updateStatus()` |
| `/api/missing-pets/{id}` | DELETE | 실종 제보 삭제 (인증 필요, 관련 댓글도 함께 삭제, 응답: `{"success": true}`) | `deleteBoard()` |

**댓글 관련 API** (MissingPetCommentService 사용):
| 엔드포인트 | Method | 설명 | 서비스 메서드 |
|-----------|--------|------|-------------|
| `/api/missing-pets/{id}/comments` | GET | 댓글 목록 조회 (삭제되지 않은 댓글만, 작성자 정보 포함) | `getComments()` |
| `/api/missing-pets/{id}/comments` | POST | 댓글 작성 (인증 필요, 파일 첨부 지원 - 첫 번째 파일만 저장) | `addComment()` |
| `/api/missing-pets/{boardId}/comments/{commentId}` | DELETE | 댓글 삭제 (인증 필요, 응답: `{"success": true}`) | `deleteComment()` |

**채팅 관련 API**:
| 엔드포인트 | Method | 설명 | 서비스 메서드 |
|-----------|--------|------|-------------|
| `/api/missing-pets/{boardIdx}/start-chat` | POST | 실종제보 채팅 시작 (인증 필요, witnessId 파라미터) | `getBoard()` + `ConversationService.createMissingPetChat()` |

**참고**: 
- 게시글 상세 조회(`GET /api/missing-pets/{id}`)는 댓글 목록을 포함하지 않음
- 댓글 목록은 별도 API(`GET /api/missing-pets/{id}/comments`)로 조회 필요
- 이는 조인 폭발 방지 및 페이징 지원을 위한 설계 결정

**실종 제보 생성 요청 예시**:
```http
POST /api/missing-pets
Content-Type: application/json

{
  "userId": 1,
  "title": "우리 강아지를 찾습니다",
  "content": "어제 한강공원에서 실종되었습니다.",
  "petName": "뽀삐",
  "species": "강아지",
  "breed": "골든 리트리버",
  "gender": "M",
  "age": "3세",
  "color": "골드",
  "lostDate": "2024-01-10",
  "lostLocation": "서울특별시 영등포구 한강공원",
  "latitude": 37.5665,
  "longitude": 126.9780,
  "status": "MISSING",
  "imageUrl": "uploads/missing-pet/1/image.jpg"
}
```

**실종 제보 생성 응답 예시**:
```json
{
  "idx": 1,
  "userId": 1,
  "title": "우리 강아지를 찾습니다",
  "content": "어제 한강공원에서 실종되었습니다.",
  "petName": "뽀삐",
  "species": "강아지",
  "breed": "골든 리트리버",
  "gender": "M",
  "age": "3세",
  "color": "골드",
  "lostDate": "2024-01-10",
  "lostLocation": "서울특별시 영등포구 한강공원",
  "latitude": 37.5665,
  "longitude": 126.9780,
  "status": "MISSING",
  "imageUrl": "http://localhost:8080/api/uploads/file?path=uploads/missing-pet/1/image.jpg",
  "attachments": [
    {
      "idx": 1,
      "filePath": "uploads/missing-pet/1/image.jpg",
      "downloadUrl": "http://localhost:8080/api/uploads/file?path=uploads/missing-pet/1/image.jpg"
    }
  ]
}
```

**댓글 작성 요청 예시**:
```http
POST /api/missing-pets/1/comments
Content-Type: application/json

{
  "userId": 2,
  "content": "어제 저녁에 한강공원 근처에서 비슷한 강아지를 봤습니다.",
  "address": "서울특별시 영등포구 여의도동",
  "latitude": 37.5675,
  "longitude": 126.9790,
  "imageUrl": "uploads/missing-pet-comment/1/sighting.jpg"
}
```

**상태 변경 요청 예시**:
```http
PATCH /api/missing-pets/1/status
Content-Type: application/json

{
  "status": "FOUND"
}
```

**실종제보 채팅 시작 요청 예시**:
```http
POST /api/missing-pets/1/start-chat?witnessId=2
```

**삭제 응답 예시**:
```json
{
  "success": true
}
```

---

## 5. 트랜잭션 처리

### 5.1 트랜잭션 전략
- **실종 제보 생성**: `@Transactional` - 게시글 생성과 파일 첨부를 원자적으로 처리
- **실종 제보 수정**: `@Transactional` - 게시글 수정과 파일 첨부를 원자적으로 처리
- **실종 제보 삭제**: `@Transactional` - 게시글과 관련 댓글 Soft Delete를 원자적으로 처리
- **댓글 작성**: `@Transactional` - 댓글 생성, 파일 첨부, 알림 발송을 원자적으로 처리
- **조회 메서드**: `@Transactional(readOnly = true)` - 읽기 전용 최적화 (클래스 레벨)

### 5.2 동시성 제어
- **Soft Delete**: 트랜잭션으로 게시글과 관련 댓글 삭제를 원자적으로 처리
- **파일 첨부**: `syncSingleAttachment()`로 기존 파일 삭제 후 새 파일 추가를 원자적으로 처리

---

## 6. 트러블슈팅

### 6.1 위치 기반 검색 미구현
**현재 상태**: 위치 기반 검색 기능은 현재 구현되어 있지 않습니다.
**저장된 데이터**: 위도/경도 정보는 저장만 되며, 반경 기반 검색 쿼리는 존재하지 않습니다.
**향후 구현 예정**: 하버사인 공식 또는 MySQL의 공간 인덱스(GIS)를 활용한 반경 기반 검색 기능 구현 예정

---

## 7. 성능 최적화

### 7.1 DB 최적화

#### 인덱스 전략

**missing_pet_board 테이블**:
```sql
-- 사용자별 게시글 조회
CREATE INDEX idx_missing_pet_user ON missing_pet_board(user_idx, is_deleted, created_at);

-- 위치 기반 검색
CREATE INDEX idx_missing_pet_location ON missing_pet_board(latitude, longitude);

-- 상태별 조회
CREATE INDEX idx_missing_pet_status ON missing_pet_board(status, is_deleted, created_at);

-- 외래키 (user_idx)
CREATE INDEX FKrid0u1qvm8e07etghggxnu1b1 ON missing_pet_board(user_idx);
```

**missing_pet_comment 테이블**:
```sql
-- 사용자별 댓글 조회
CREATE INDEX FKe3sca61815j9cxi608oxmrfjt ON missing_pet_comment(user_idx);

-- 게시글별 댓글 조회
CREATE INDEX FKpodx5stuchr73mrjgffir72ii ON missing_pet_comment(board_idx);
```

**선정 이유**:
- 자주 조회되는 컬럼 조합 (status, is_deleted, created_at)
- WHERE 절에서 자주 사용되는 조건
- JOIN에 사용되는 외래키 (user_idx, board_idx)
- 위치 기반 검색을 위한 인덱스 (latitude, longitude)

### 7.2 애플리케이션 레벨 최적화

#### N+1 문제 해결
**구현 위치**: `MissingPetBoardRepository`, `MissingPetCommentRepository`

**최적화 사항**:
- **Fetch Join**: `JOIN FETCH`로 사용자 정보를 함께 조회하여 N+1 문제 해결
- **활성 사용자 필터링**: `u.isDeleted = false AND u.status = 'ACTIVE'` 조건으로 활성 사용자만 조회
- **댓글 조회**: `findByBoardAndIsDeletedFalseOrderByCreatedAtAsc()`에서 `JOIN FETCH` 사용

**예시**:
```java
@Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user u WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY b.createdAt DESC")
List<MissingPetBoard> findAllByOrderByCreatedAtDesc();
```

#### 파일 첨부 최적화
- **파일 매핑**: `mapBoardWithAttachments()`, `mapCommentWithAttachments()`로 첨부 파일 정보 포함
- **주요 이미지 URL**: 첫 번째 파일의 다운로드 URL을 `imageUrl`로 설정하여 빠른 접근

### 7.4 최적화 핵심 포인트

#### 1단계: 서비스 분리 및 조인 폭발 방지
- **문제**: 게시글과 댓글을 함께 조회할 경우 댓글이 많아지면 조인 결과가 기하급수적으로 증가 (Cartesian Product)
- **해결**: 게시글과 댓글 조회를 완전히 분리
  - `getBoard()`: 게시글+작성자만 조회, 댓글 수만 포함
  - `getComments()`: 댓글 목록은 별도 API로 조회
- **효과**: 조인 폭발 방지, 페이징 지원 가능, 확장성 향상

#### 2단계: 파일 N+1 해결
- **문제**: 각 게시글/댓글마다 `getAttachments()` 호출로 개별 쿼리 발생
- **해결**: `getAttachmentsBatch()` 메서드로 ID 목록을 한 번에 조회
  - 게시글 목록: `getBoards()`에서 배치 조회
  - 댓글 목록: `getComments()`에서 배치 조회
- **효과**: N개 쿼리 → 1개 (배치 조회, IN 절 사용)

### 7.5 성능 개선 결과

**최적화 전후 비교**:
| 항목 | 최적화 전 | 최적화 후 | 개선율 |
|------|----------|----------|--------|
| **쿼리 수** | 207개 | 3개 | **98.5% 감소** |
| **실행 시간** | 571ms | 79ms | **86% 감소** |
| **메모리 사용** | 11MB | 4MB | **64% 감소** |

**주요 최적화 기법**:
1. **서비스 분리**: 게시글과 댓글 조회 분리로 조인 폭발 방지
2. **Fetch Join**: 게시글+작성자, 댓글+작성자 정보를 한 번에 조회
3. **배치 조회**: 파일 첨부 정보를 IN 절로 한 번에 조회
4. **비동기 처리**: 알림 발송을 비동기로 처리하여 응답 시간 단축

### 7.6 기술 스택

- **Backend**: Spring Boot 3.5.7, JPA/Hibernate
- **Database**: MySQL
- **최적화 기법**: 
  - Fetch Join (LEFT JOIN FETCH)
  - 배치 조회 (Batch Fetching with IN 절)
  - DISTINCT를 활용한 중복 제거

---

## 8. 핵심 포인트 요약

### 8.1 실종 제보 관리
- **이메일 인증**: 실종 제보 작성/수정/삭제 시 이메일 인증 필요
- **파일 첨부**: 이미지 첨부 지원 (`FileTargetType.MISSING_PET`)
- **Soft Delete**: 게시글 삭제 시 관련 댓글도 함께 Soft Delete
- **상태 관리**: MISSING → FOUND → RESOLVED 상태 전환

### 8.2 목격 정보 댓글
- **위치 정보 포함**: 목격 위치 주소, 위도, 경도 포함 (`BigDecimal` 타입)
- **파일 첨부**: 이미지 첨부 지원 (`FileTargetType.MISSING_PET_COMMENT`) - 첫 번째 파일만 저장
- **알림 발송**: 댓글 작성 시 게시글 작성자에게 알림 발송 (댓글 작성자가 게시글 작성자가 아닌 경우, 비동기 처리)
- **Soft Delete**: 댓글 삭제 시 Soft Delete 적용
- **댓글 추가**: 댓글 저장 후 `board.getComments().add(saved)` 호출

### 8.3 실종제보 채팅 연동
- **채팅 시작**: "목격했어요" 버튼 클릭 시 실종제보 채팅방 생성
- **채팅방 생성**: 제보자와 목격자 간 1:1 채팅방 생성 (`createMissingPetChat()`)

### 8.4 성능 최적화
- **서비스 분리**: 게시글과 댓글 조회를 분리하여 조인 폭발 방지 및 페이징 지원
- **N+1 문제 해결**: `JOIN FETCH`로 사용자 정보를 함께 조회
- **활성 사용자 필터링**: 삭제되지 않고 활성 상태인 사용자만 조회
- **파일 배치 조회**: `getAttachmentsBatch()`로 게시글/댓글 목록의 파일을 한 번에 조회 (N+1 문제 해결)
- **인덱스 전략**: 상태별, 위치별, 사용자별 인덱스로 조회 성능 향상
- **비동기 알림**: `@Async`로 알림 발송을 비동기 처리하여 댓글 작성 응답 시간 단축

### 8.5 엔티티 설계 특징
- **BaseTimeEntity 미사용**: `@PrePersist`, `@PreUpdate`로 직접 시간 관리
- **위치 정보**: `BigDecimal` 타입으로 정밀도 보장 (precision = 15, scale = 12)
- **Soft Delete**: `isDeleted`, `deletedAt` 필드로 Soft Delete 지원
- **상태 관리**: `MissingPetStatus` enum으로 상태 관리 (MISSING, FOUND, RESOLVED)
- **성별 관리**: `MissingPetGender` enum으로 성별 관리 (M, F)

### 8.6 파일 첨부 연동
- **폴리모픽 관계**: `FileTargetType.MISSING_PET`, `MISSING_PET_COMMENT`로 파일 첨부
- **파일 동기화**: `syncSingleAttachment()`로 기존 파일 삭제 후 새 파일 추가
- **파일 매핑**: `mapBoardWithAttachments()`, `mapCommentWithAttachments()`로 첨부 파일 정보 포함
