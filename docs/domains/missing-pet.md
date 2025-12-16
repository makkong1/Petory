# Missing Pet 도메인 - 포트폴리오 상세 설명

## 1. 기능 설명

### 1.1 도메인 개요
- **역할**: 실종 동물 신고 및 관리 시스템으로, 반려동물을 잃어버린 사용자가 신고하고 다른 사용자들이 정보를 제공할 수 있습니다.
- **주요 기능**: 
  - 실종 동물 신고 생성/조회/수정
  - 위치 기반 검색 (반경 내)
  - 실종 동물 상태 관리 (MISSING → FOUND → CLOSED)
  - 목격 정보 댓글

### 1.2 기능 시연
> **스크린샷/영상 링크**: [기능 작동 영상 또는 스크린샷 추가]

#### 주요 기능 1: 실종 동물 신고
- **설명**: 사용자가 실종 동물 정보를 신고하고 사진을 첨부할 수 있습니다.
- **사용자 시나리오**: 
  1. 실종 동물 신고 (이름, 종, 품종, 성별, 나이, 색상, 실종 날짜, 실종 장소)
  2. 사진 첨부
  3. 위치 정보 입력 (위도, 경도)
  4. 다른 사용자들이 목격 정보 댓글 작성
- **스크린샷/영상**: 

#### 주요 기능 2: 위치 기반 검색
- **설명**: 내 위치 기준 반경 내 실종 동물을 검색할 수 있습니다.
- **사용자 시나리오**:
  1. 내 위치 확인
  2. 반경 설정 (예: 5km)
  3. 반경 내 실종 동물 목록 표시
- **스크린샷/영상**: 

---

## 2. 서비스 로직 설명

### 2.1 핵심 비즈니스 로직

#### 로직 1: 실종 제보 생성
**구현 위치**: `MissingPetBoardService.createBoard()` (Lines 62-94)

**핵심 로직**:
- **이메일 인증 확인**: 실종 제보 작성 시 이메일 인증 필요 (`EmailVerificationRequiredException`)
- **파일 첨부**: `syncSingleAttachment()`로 이미지 첨부 지원
- **알림 발송**: 댓글 작성 시 게시글 작성자에게 알림 발송 (댓글 작성자가 게시글 작성자가 아닌 경우)

#### 로직 2: 실종 제보 수정/삭제
**구현 위치**: `MissingPetBoardService.updateBoard()`, `deleteBoard()` (Lines 97-182)

**핵심 로직**:
- **이메일 인증 확인**: 수정/삭제 시 이메일 인증 필요
- **Soft Delete**: 삭제 시 관련 댓글도 함께 Soft Delete

---

## 3. 아키텍처 설명

### 3.1 도메인 구조
**참고**: Missing Pet 도메인은 `domain/board/` 패키지 내에 위치합니다.

```
domain/board/
  ├── controller/
  │   └── MissingPetBoardController.java
  ├── service/
  │   └── MissingPetBoardService.java
  ├── entity/
  │   ├── MissingPetBoard.java
  │   └── MissingPetComment.java
  ├── converter/
  │   └── MissingPetConverter.java
  ├── dto/
  │   ├── MissingPetBoardDTO.java
  │   └── MissingPetCommentDTO.java
  └── repository/
      ├── MissingPetBoardRepository.java
      └── MissingPetCommentRepository.java
```

### 3.2 엔티티 구조

#### MissingPetBoard (실종 동물 게시글)
```java
@Entity
@Table(name = "MissingPetBoard")
public class MissingPetBoard {
    private Long idx;
    private Users user;                    // 작성자
    private String title;                  // 제목
    @Lob
    private String content;                // 내용
    private String petName;                // 반려동물 이름
    private String species;                // 종류
    private String breed;                  // 품종
    private MissingPetGender gender;       // 성별
    private String age;                    // 나이
    private String color;                  // 색상
    private LocalDate lostDate;            // 실종일
    private String lostLocation;           // 실종 위치
    private BigDecimal latitude;           // 위도
    private BigDecimal longitude;          // 경도
    @Builder.Default
    private MissingPetStatus status = MissingPetStatus.MISSING;  // 상태 (MISSING, FOUND, CLOSED)
    private LocalDateTime createdAt;       // @PrePersist로 자동 설정
    private LocalDateTime updatedAt;        // @PreUpdate로 자동 설정
    @Builder.Default
    private Boolean isDeleted = false;
    private LocalDateTime deletedAt;
    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MissingPetComment> comments; // 댓글 목록
}
```

#### MissingPetComment (실종 동물 댓글)
```java
@Entity
@Table(name = "MissingPetComment")
public class MissingPetComment {
    private Long idx;
    private MissingPetBoard board;         // 실종 동물 게시글
    private Users user;                    // 작성자
    private String content;                // 내용
    private String address;                // 목격 위치 주소
    private Double latitude;               // 목격 위치 위도
    private Double longitude;              // 목격 위치 경도
    private LocalDateTime createdAt;
    @Builder.Default
    private Boolean isDeleted = false;
    private LocalDateTime deletedAt;
}
```

### 3.3 엔티티 관계도 (ERD)
```mermaid
erDiagram
    Users ||--o{ MissingPetBoard : "신고"
    MissingPetBoard ||--o{ MissingPetComment : "댓글"
    Users ||--o{ MissingPetComment : "작성"
```

---

## 4. 트러블슈팅

---

## 5. 성능 최적화

### 5.1 DB 최적화

#### 인덱스 전략
```sql
-- 상태별 조회
CREATE INDEX idx_missing_pet_status ON MissingPetBoard(status, is_deleted, created_at DESC);

-- 위치 기반 검색 (Spatial Index)
CREATE SPATIAL INDEX idx_missing_pet_location ON MissingPetBoard(longitude, latitude);
```

---

## 6. 핵심 포인트 요약

### 기술적 하이라이트
1. **이메일 인증**: 실종 제보 작성/수정/삭제 시 이메일 인증 필요
2. **파일 첨부**: 이미지 첨부 지원 (`AttachmentFileService` 연동)
3. **알림 발송**: 댓글 작성 시 게시글 작성자에게 알림 발송
4. **Soft Delete**: 게시글 삭제 시 관련 댓글도 함께 Soft Delete
5. **실종 동물 상태 관리**: MISSING → FOUND → CLOSED

