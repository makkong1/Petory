# Activity 도메인 - 포트폴리오 상세 설명

## 1. 기능 설명

### 1.1 도메인 개요
- **역할**: 사용자 활동 로그 통합 조회 도메인으로, 여러 도메인(펫케어, 커뮤니티, 실종 제보 등)의 활동을 통합하여 사용자별 활동 이력을 제공합니다.
- **주요 기능**: 
  - 사용자별 활동 통합 조회 (게시글, 댓글 등)
  - 활동 타입별 필터링 (전체, 게시글, 댓글, 리뷰)
  - 페이징 지원
  - 최신순 정렬
  - Soft Delete 필터링 (삭제된 활동 제외)

### 1.2 기능 시연
> **스크린샷/영상 링크**: [기능 작동 영상 또는 스크린샷 추가]

#### 주요 기능 1: 활동 통합 조회
- **설명**: 사용자가 작성한 모든 활동(게시글, 댓글 등)을 한 곳에서 조회할 수 있습니다.
- **사용자 시나리오**: 
  1. 마이페이지에서 "내 활동" 메뉴 클릭
  2. 펫케어 요청, 커뮤니티 게시글, 실종 제보, 댓글 등 모든 활동이 최신순으로 표시
  3. 필터링으로 게시글만, 댓글만 조회 가능
  4. 페이징으로 활동 목록 탐색
- **스크린샷/영상**: 

#### 주요 기능 2: 활동 타입별 필터링
- **설명**: 활동을 타입별로 필터링하여 조회할 수 있습니다.
- **사용자 시나리오**:
  1. "전체" 필터: 모든 활동 표시
  2. "게시글" 필터: 펫케어 요청, 커뮤니티 게시글, 실종 제보만 표시
  3. "댓글" 필터: 펫케어 댓글, 커뮤니티 댓글, 실종 제보 댓글만 표시
  4. "리뷰" 필터: 위치 서비스 리뷰만 표시 (현재 미구현)
- **스크린샷/영상**: 

---

## 2. 서비스 로직 설명

### 2.1 핵심 비즈니스 로직

#### 로직 1: 사용자 활동 통합 조회
**구현 위치**: `ActivityService.getUserActivities()` (Lines 46-251)

**핵심 로직**:
- **다양한 도메인에서 활동 수집**:
  1. **펫케어 요청** (`CARE_REQUEST`): `CareRequestRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc()`
  2. **커뮤니티 게시글** (`BOARD`): `BoardRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc()`
  3. **실종 제보 게시글** (`MISSING_PET`): `MissingPetBoardRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc()`
  4. **펫케어 댓글** (`CARE_COMMENT`): `CareRequestCommentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc()`
  5. **커뮤니티 댓글** (`COMMENT`): `CommentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc()`
  6. **실종 제보 댓글** (`MISSING_COMMENT`): `MissingPetCommentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc()`

- **ActivityDTO 변환**:
  - 각 엔티티를 `ActivityDTO`로 변환
  - 게시글 타입: `title`, `content`, `status` 포함
  - 댓글 타입: `content`, `relatedId`, `relatedTitle` 포함 (관련 게시글 정보)
  - `deleted`, `deletedAt` 필드 포함 (Soft Delete 정보)

- **최신순 정렬**: `createdAt` 기준 내림차순 정렬 (null-safe)
- **Soft Delete 필터링**: `isDeleted = false`인 활동만 조회

**코드 예시**:
```java
public List<ActivityDTO> getUserActivities(long userId) {
    Users user = usersRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
    
    List<ActivityDTO> activities = new ArrayList<>();
    
    // 1. 펫케어 요청 조회
    List<CareRequest> careRequests = careRequestRepository
        .findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
    activities.addAll(careRequests.stream()
        .map(cr -> ActivityDTO.builder()
            .idx(cr.getIdx())
            .type("CARE_REQUEST")
            .title(cr.getTitle())
            .content(cr.getDescription())
            .createdAt(cr.getCreatedAt())
            .status(cr.getStatus() != null ? cr.getStatus().name() : null)
            .deleted(cr.getIsDeleted())
            .deletedAt(cr.getDeletedAt())
            .build())
        .collect(Collectors.toList()));
    
    // 2. 커뮤니티 게시글 조회
    List<Board> boards = boardRepository
        .findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
    activities.addAll(boards.stream()
        .map(b -> ActivityDTO.builder()
            .idx(b.getIdx())
            .type("BOARD")
            .title(b.getTitle())
            .content(b.getContent())
            .createdAt(b.getCreatedAt())
            .status(b.getStatus() != null ? b.getStatus().name() : null)
            .deleted(b.getIsDeleted())
            .deletedAt(b.getDeletedAt())
            .build())
        .collect(Collectors.toList()));
    
    // 3. 실종 제보 게시글 조회
    // ... (동일한 패턴)
    
    // 4. 펫케어 댓글 조회 (관련 게시글 정보 포함)
    List<CareRequestComment> careComments = careRequestCommentRepository
        .findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
    activities.addAll(careComments.stream()
        .map(cc -> {
            CareRequest cr = cc.getCareRequest();
            return ActivityDTO.builder()
                .idx(cc.getIdx())
                .type("CARE_COMMENT")
                .title(null)
                .content(cc.getContent())
                .createdAt(cc.getCreatedAt())
                .status(cc.getIsDeleted() ? "DELETED" : "ACTIVE")
                .deleted(cc.getIsDeleted())
                .deletedAt(cc.getDeletedAt())
                .relatedId(cr != null ? cr.getIdx() : null)
                .relatedTitle(cr != null ? cr.getTitle() : null)
                .build();
        })
        .collect(Collectors.toList()));
    
    // 5. 커뮤니티 댓글 조회
    // 6. 실종 제보 댓글 조회
    // ... (동일한 패턴)
    
    // 7. 최신순 정렬 (null-safe)
    activities.sort((a, b) -> {
        if (a.getCreatedAt() == null && b.getCreatedAt() == null) return 0;
        if (a.getCreatedAt() == null) return 1; // null은 뒤로
        if (b.getCreatedAt() == null) return -1; // null은 뒤로
        return b.getCreatedAt().compareTo(a.getCreatedAt());
    });
    
    return activities;
}
```

#### 로직 2: 활동 조회 (페이징 및 필터링)
**구현 위치**: `ActivityService.getUserActivitiesWithPaging()` (Lines 254-297)

**핵심 로직**:
- **전체 활동 조회**: `getUserActivities()` 호출하여 모든 활동 수집
- **필터링 적용**: `filterActivities()` 메서드로 필터링
  - `ALL`: 모든 활동 반환
  - `POSTS`: 게시글 타입만 (`CARE_REQUEST`, `BOARD`, `MISSING_PET`)
  - `COMMENTS`: 댓글 타입만 (`CARE_COMMENT`, `COMMENT`, `MISSING_COMMENT`)
  - `REVIEWS`: 리뷰 타입만 (`LOCATION_REVIEW` - 현재 미구현)

- **필터별 개수 계산**: 전체, 게시글, 댓글, 리뷰 개수 제공 (필터 버튼에 표시용)
- **페이징 적용**: Spring Data `PageRequest` 사용하여 페이징 처리
- **응답 DTO 생성**: `ActivityPageResponseDTO`에 활동 목록, 페이징 정보, 필터별 개수 포함

**코드 예시**:
```java
public ActivityPageResponseDTO getUserActivitiesWithPaging(
        long userId, String filter, int page, int size) {
    
    // 1. 전체 활동 가져오기
    List<ActivityDTO> allActivities = getUserActivities(userId);
    
    // 2. 필터링 적용
    List<ActivityDTO> filteredActivities = filterActivities(allActivities, filter);
    
    // 3. 필터별 개수 계산
    long allCount = allActivities.size();
    long postsCount = allActivities.stream()
        .filter(a -> isPostType(a.getType()))
        .count();
    long commentsCount = allActivities.stream()
        .filter(a -> isCommentType(a.getType()))
        .count();
    long reviewsCount = allActivities.stream()
        .filter(a -> "LOCATION_REVIEW".equals(a.getType()))
        .count();
    
    // 4. 페이징 적용
    Pageable pageable = PageRequest.of(page, size);
    int start = (int) pageable.getOffset();
    int end = Math.min((start + pageable.getPageSize()), filteredActivities.size());
    
    List<ActivityDTO> pageContent = filteredActivities.subList(start, end);
    Page<ActivityDTO> activityPage = new PageImpl<>(
        pageContent, pageable, filteredActivities.size());
    
    // 5. 응답 DTO 생성
    return ActivityPageResponseDTO.builder()
        .activities(activityPage.getContent())
        .totalCount(activityPage.getTotalElements())
        .totalPages(activityPage.getTotalPages())
        .currentPage(page)
        .pageSize(size)
        .hasNext(activityPage.hasNext())
        .hasPrevious(activityPage.hasPrevious())
        .allCount(allCount)
        .postsCount(postsCount)
        .commentsCount(commentsCount)
        .reviewsCount(reviewsCount)
        .build();
}

private List<ActivityDTO> filterActivities(List<ActivityDTO> activities, String filter) {
    if (filter == null || "ALL".equals(filter)) {
        return activities;
    }
    
    switch (filter) {
        case "POSTS":
            return activities.stream()
                .filter(a -> isPostType(a.getType()))
                .collect(Collectors.toList());
        case "COMMENTS":
            return activities.stream()
                .filter(a -> isCommentType(a.getType()))
                .collect(Collectors.toList());
        case "REVIEWS":
            return activities.stream()
                .filter(a -> "LOCATION_REVIEW".equals(a.getType()))
                .collect(Collectors.toList());
        default:
            return activities;
    }
}

private boolean isPostType(String type) {
    return "CARE_REQUEST".equals(type) 
        || "BOARD".equals(type) 
        || "MISSING_PET".equals(type);
}

private boolean isCommentType(String type) {
    return "CARE_COMMENT".equals(type) 
        || "COMMENT".equals(type) 
        || "MISSING_COMMENT".equals(type);
}
```

---

## 3. 아키텍처 설명

### 3.1 도메인 구조
**참고**: Activity 도메인은 별도의 Activity 엔티티를 사용하지 않고, 각 도메인의 엔티티를 직접 조회하여 통합합니다.

```
domain/activity/
  ├── controller/
  │   └── ActivityController.java
  ├── service/
  │   └── ActivityService.java
  └── dto/
      ├── ActivityDTO.java
      └── ActivityPageResponseDTO.java
```

### 3.2 ActivityDTO 구조
```java
public class ActivityDTO {
    private Long idx;                    // 활동 ID
    private String type;                 // 활동 타입 (CARE_REQUEST, BOARD, MISSING_PET, CARE_COMMENT, COMMENT, MISSING_COMMENT, LOCATION_REVIEW)
    private String title;                // 제목 (게시글 타입에만 존재)
    private String content;              // 내용
    private LocalDateTime createdAt;     // 생성 시간
    private String status;               // 상태 (게시글/댓글의 상태)
    private Boolean deleted;             // 삭제 여부
    private LocalDateTime deletedAt;     // 삭제 시간
    
    // 관련 정보 (댓글 타입에만 존재)
    private Long relatedId;             // 관련 게시글 ID
    private String relatedTitle;         // 관련 게시글 제목
}
```

### 3.3 ActivityPageResponseDTO 구조
```java
public class ActivityPageResponseDTO {
    private List<ActivityDTO> activities;  // 활동 목록
    private long totalCount;               // 필터링된 전체 개수
    private int totalPages;                // 전체 페이지 수
    private int currentPage;               // 현재 페이지
    private int pageSize;                  // 페이지 크기
    private boolean hasNext;               // 다음 페이지 존재 여부
    private boolean hasPrevious;           // 이전 페이지 존재 여부
    
    // 필터별 개수 (필터 버튼에 표시용)
    private long allCount;                 // 전체 개수
    private long postsCount;               // 게시글 개수
    private long commentsCount;            // 댓글 개수
    private long reviewsCount;             // 리뷰 개수
}
```

### 3.4 활동 타입 분류

#### 게시글 타입 (`isPostType()`)
- `CARE_REQUEST`: 펫케어 요청
- `BOARD`: 커뮤니티 게시글
- `MISSING_PET`: 실종 제보 게시글

#### 댓글 타입 (`isCommentType()`)
- `CARE_COMMENT`: 펫케어 요청 댓글
- `COMMENT`: 커뮤니티 게시글 댓글
- `MISSING_COMMENT`: 실종 제보 게시글 댓글

#### 리뷰 타입
- `LOCATION_REVIEW`: 위치 서비스 리뷰 (현재 미구현)

---

## 4. 트러블슈팅

### 4.1 N+1 문제
**문제**: 각 도메인에서 활동을 조회할 때 N+1 문제 발생 가능

**해결**: 
- 각 도메인별로 별도의 Repository 메서드 사용 (`findByUserAndIsDeletedFalseOrderByCreatedAtDesc`)
- Soft Delete 필터링을 Repository 레벨에서 처리
- 최신순 정렬을 Repository 레벨에서 처리

**효과**: 
- 각 도메인별로 최적화된 쿼리 실행
- 불필요한 데이터 조회 방지

### 4.2 메모리 사용량
**문제**: 모든 활동을 메모리에 로드한 후 필터링 및 페이징 처리

**해결**: 
- 현재는 메모리 기반 필터링 및 페이징 사용
- 향후 개선: DB 레벨에서 필터링 및 페이징 처리 (각 도메인별 쿼리 최적화)

**효과**: 
- 구현 단순성 유지
- 향후 확장 가능

---

## 5. 성능 최적화

### 5.1 DB 최적화

#### 인덱스 전략
각 도메인별로 사용자별 조회를 위한 인덱스가 필요합니다:

```sql
-- 펫케어 요청 조회
CREATE INDEX idx_care_request_user_deleted_created 
ON care_request(user_idx, is_deleted, created_at DESC);

-- 커뮤니티 게시글 조회
CREATE INDEX idx_board_user_deleted_created 
ON board(user_idx, is_deleted, created_at DESC);

-- 실종 제보 게시글 조회
CREATE INDEX idx_missing_pet_board_user_deleted_created 
ON missing_pet_board(user_idx, is_deleted, created_at DESC);

-- 펫케어 댓글 조회
CREATE INDEX idx_care_request_comment_user_deleted_created 
ON care_request_comment(user_idx, is_deleted, created_at DESC);

-- 커뮤니티 댓글 조회
CREATE INDEX idx_comment_user_deleted_created 
ON comment(user_idx, is_deleted, created_at DESC);

-- 실종 제보 댓글 조회
CREATE INDEX idx_missing_pet_comment_user_deleted_created 
ON missing_pet_comment(user_idx, is_deleted, created_at DESC);
```

**선정 이유**:
- 사용자별 활동 조회가 빈번함
- Soft Delete 필터링 최적화
- 최신순 정렬 최적화

### 5.2 애플리케이션 레벨 최적화

#### 병렬 처리 (향후 개선)
- 각 도메인별 활동 조회를 병렬로 처리하여 성능 향상 가능
- `CompletableFuture` 또는 `@Async` 활용

#### 캐싱 전략 (향후 개선)
- 사용자별 활동 목록을 Redis에 캐싱
- TTL: 5분 (활동이 자주 변경되지 않으므로)
- 활동 생성/수정/삭제 시 캐시 무효화

---

## 6. 핵심 포인트 요약

### 기술적 하이라이트
1. **통합 활동 조회**: 여러 도메인(펫케어, 커뮤니티, 실종 제보)의 활동을 통합하여 조회
2. **별도 엔티티 없음**: Activity 엔티티 없이 각 도메인의 엔티티를 직접 조회
3. **필터링**: 활동 타입별 필터링 지원 (전체, 게시글, 댓글, 리뷰)
4. **페이징**: Spring Data 페이징 지원
5. **최신순 정렬**: `createdAt` 기준 내림차순 정렬 (null-safe)
6. **Soft Delete 필터링**: 삭제된 활동은 제외 (`isDeleted = false`)
7. **관련 정보 포함**: 댓글 타입의 경우 관련 게시글 정보(`relatedId`, `relatedTitle`) 포함

### 활동 타입
- **게시글**: `CARE_REQUEST`, `BOARD`, `MISSING_PET`
- **댓글**: `CARE_COMMENT`, `COMMENT`, `MISSING_COMMENT`
- **리뷰**: `LOCATION_REVIEW` (현재 미구현)

### API 엔드포인트
- `GET /api/activities/my?userId={userId}`: 사용자 활동 목록 조회 (하위 호환성 유지)
- `GET /api/activities/my/paging?userId={userId}&filter={filter}&page={page}&size={size}`: 페이징 지원 활동 목록 조회
