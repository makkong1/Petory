# Activity 도메인

## 1. 기능 설명

### 1.1 도메인 개요
- **역할**: 사용자 활동 로그 통합 조회 도메인. 여러 도메인(펫케어, 커뮤니티, 실종 제보)의 활동을 단일 타임라인으로 제공한다.
- **주요 기능**:
  - 사용자별 활동 통합 조회 (게시글, 댓글 등)
  - 활동 타입별 필터링 (`ALL` / `POSTS` / `COMMENTS` / `REVIEWS`)
  - 페이징 지원
  - `createdAt` 기준 최신순 정렬 (null-safe)
  - Soft Delete 필터링 (`isDeleted = false` 인 활동만 조회)

### 1.2 기능 시연

#### 주요 기능 1: 활동 통합 조회
- **설명**: 사용자가 작성한 모든 활동을 한 곳에서 최신순으로 조회
- **사용자 시나리오**:
  1. 마이페이지 "내 활동" 클릭
  2. 펫케어 요청, 커뮤니티 게시글, 실종 제보, 댓글 등 모든 활동이 최신순 표시
  3. 필터로 게시글만 / 댓글만 조회 가능
  4. 페이징으로 목록 탐색

#### 주요 기능 2: 활동 타입별 필터링
- **설명**: 활동 타입별로 필터링하여 조회
- **사용자 시나리오**:
  1. `ALL`: 모든 활동 표시
  2. `POSTS`: 펫케어 요청, 커뮤니티 게시글, 실종 제보만 표시
  3. `COMMENTS`: 펫케어 댓글, 커뮤니티 댓글, 실종 제보 댓글만 표시
  4. `REVIEWS`: `LOCATION_REVIEW` 타입만 — **`getUserActivities()`에서 수집하지 않아 항상 0** (미구현)

---

## 2. 도메인 구조

```
domain/activity/
  ├── controller/
  │   └── ActivityController.java
  ├── converter/
  │   └── ActivityConverter.java        ← 엔티티 → ActivityDTO 변환 담당
  ├── service/
  │   └── ActivityService.java
  └── dto/
      ├── ActivityDTO.java
      └── ActivityPageResponseDTO.java
```

**핵심 설계**: Activity 전용 엔티티·테이블 없음. 각 도메인 엔티티를 직접 조회해서 `ActivityConverter`가 `ActivityDTO`로 변환한다.

**의존 도메인**: `board`, `care`, `user`

---

## 3. 서비스 로직

### 3.1 `ActivityService`

**트랜잭션**: 클래스 레벨 `@Transactional(readOnly = true)`.

**참고**: Controller·Service 양쪽에 `System.out.println` 디버그 출력이 남아 있음 — 운영 전 로거 전환 필요.

#### `getUserActivities(long userId)`

6개 리포지토리에서 각각 `isDeleted=false` 데이터를 가져와 `ActivityConverter`로 변환 후 Stream으로 합쳐 `createdAt` 내림차순 정렬.

```java
return Stream.of(
        careRequestRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user).stream()
                .map(activityConverter::toActivityDto),
        boardRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user).stream()
                .map(activityConverter::toActivityDto),
        missingPetBoardRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user).stream()
                .map(activityConverter::toActivityDto),
        careRequestCommentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user)
                .stream().map(activityConverter::toActivityDto),
        commentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user).stream()
                .map(activityConverter::toActivityDto),
        missingPetCommentRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user)
                .stream().map(activityConverter::toActivityDto))
        .flatMap(s -> s)
        .sorted(Comparator.comparing(ActivityDTO::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
        .toList();
```

#### `getUserActivitiesWithPaging(long userId, String filter, int page, int size)`

1. `getUserActivities()` 호출로 전체 활동 수집
2. `filterActivities()` 로 필터 적용
3. for-loop 순회로 `postsCount` / `commentsCount` / `reviewsCount` 산출
4. `Math.min` 가드로 안전한 `subList` 페이징 (`start >= end` 이면 `Collections.emptyList()` 반환)

```java
int start = Math.min((int) pageable.getOffset(), totalFiltered);
int end = Math.min(start + pageable.getPageSize(), totalFiltered);
List<ActivityDTO> pageContent = start >= end
        ? Collections.emptyList()
        : filteredActivities.subList(start, end);
```

#### `filterActivities(List<ActivityDTO>, String filter)` — private

Java switch expression 사용.

| filter 값 | 통과 타입 |
|----------|---------|
| `null` / `ALL` | 전체 |
| `POSTS` | `CARE_REQUEST`, `BOARD`, `MISSING_PET` |
| `COMMENTS` | `CARE_COMMENT`, `COMMENT`, `MISSING_COMMENT` |
| `REVIEWS` | `LOCATION_REVIEW` |
| 그 외 | 전체 |

---

## 4. ActivityConverter

각 도메인 엔티티를 `ActivityDTO`로 변환하는 `@Component`. 변환 규칙:

| 엔티티 | type 값 | title | status | relatedId / relatedTitle |
|--------|---------|-------|--------|--------------------------|
| `CareRequest` | `CARE_REQUEST` | `cr.getTitle()` | `status` enum name (null 가능) | — |
| `Board` | `BOARD` | `b.getTitle()` | `status` enum name (null 가능) | — |
| `MissingPetBoard` | `MISSING_PET` | `mb.getTitle()` | `status` enum name (null 가능) | — |
| `CareRequestComment` | `CARE_COMMENT` | `null` | `"DELETED"` / `"ACTIVE"` | `CareRequest` idx, title |
| `Comment` | `COMMENT` | `null` | `status` enum name (null 가능) | `Board` idx, title |
| `MissingPetComment` | `MISSING_COMMENT` | `null` | `"DELETED"` / `"ACTIVE"` | `MissingPetBoard` idx, title |

**status 일관성 주의**: `CARE_COMMENT` / `MISSING_COMMENT`는 문자열 `"DELETED"`/`"ACTIVE"`, 나머지는 enum `.name()`.

---

## 5. DTO 구조

### ActivityDTO

```java
public class ActivityDTO {
    Long idx;
    String type;          // CARE_REQUEST | BOARD | MISSING_PET | CARE_COMMENT | COMMENT | MISSING_COMMENT | LOCATION_REVIEW
    String title;         // 게시글 타입에만 존재, 댓글은 null
    String content;
    LocalDateTime createdAt;
    String status;
    Boolean deleted;
    LocalDateTime deletedAt;
    Long relatedId;       // 댓글 타입: 관련 게시글 idx
    String relatedTitle;  // 댓글 타입: 관련 게시글 제목
}
```

### ActivityPageResponseDTO

```java
public class ActivityPageResponseDTO {
    List<ActivityDTO> activities;
    long totalCount;      // 필터링된 전체 개수
    int totalPages;
    int currentPage;
    int pageSize;
    boolean hasNext;
    boolean hasPrevious;
    long allCount;        // 전체(필터 전) 개수
    long postsCount;
    long commentsCount;
    long reviewsCount;
}
```

---

## 6. API

| Method | URL | 파라미터 | 응답 | 인증 |
|--------|-----|----------|------|------|
| GET | `/api/activities/my` | `userId` (Long, 필수) | `List<ActivityDTO>` | 필요 (`/api/**`) |
| GET | `/api/activities/my/paging` | `userId` (Long, 필수), `filter` (기본 `ALL`), `page` (기본 `0`), `size` (기본 `20`) | `ActivityPageResponseDTO` | 필요 |

**보안**: `userId`는 쿼리 파라미터로만 받으며 **JWT 주체와 일치 여부를 검증하지 않음**. 클라이언트가 본인 `idx`를 전달해야 한다.

| 예외 | HTTP | 발생 조건 |
|------|------|----------|
| `UserNotFoundException` | 404 | `usersRepository.findById(userId)` 실패 |
| (인증 오류) | 401 | 비로그인 상태 (`SecurityConfig` `/api/**` 규칙) |

---

## 7. 알려진 한계 및 개선 여지

### 7.1 메모리 기반 페이징
전체 활동을 메모리에 로드한 뒤 필터링·페이징 처리. 활동 수가 많아지면 메모리 부하 발생 가능.
- 향후 개선: DB 레벨 필터링 + 페이징으로 전환

### 7.2 N+1 잠재 위험
6개 리포지토리 쿼리 자체는 최적화되어 있으나, 댓글 변환 시 관련 게시글(`getCareRequest()`, `getBoard()` 등) 접근 — 연관 엔티티 Lazy 로딩 설정에 따라 N+1 발생 가능. Fetch join 또는 `@EntityGraph` 검토 권장.

### 7.3 `LOCATION_REVIEW` 미수집
`reviewsCount`·`REVIEWS` 필터 코드는 존재하지만, `getUserActivities()`에서 수집하지 않아 항상 0. 수집 로직 구현 필요.

### 7.4 디버그 출력
`ActivityController` 양 엔드포인트에 `System.out.println` 잔존. 운영 전 SLF4J 로거로 교체 필요.
