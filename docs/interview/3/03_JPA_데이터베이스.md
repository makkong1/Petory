# 3. JPA & 데이터베이스

## Q3-1. N+1 문제가 무엇이고, 어떻게 해결했나요?

### 답변 포인트
- **문제**: 연관 엔티티를 개별 쿼리로 조회하는 문제
- **해결 방법**:
  - Fetch Join 활용: `JOIN FETCH`로 한 번에 조회
  - 배치 조회: IN 절을 활용한 집계 쿼리
  - 예시: Board 도메인에서 301개 쿼리 → 3개 쿼리로 감소

### 상세 답변

#### 1. N+1 문제 발생 시나리오
**위치**: `domain/board/service/BoardService.java` (최적화 전)

**문제 상황**:
```
1. 게시글 목록 조회 (1개 쿼리)
   SELECT * FROM board WHERE is_deleted = false

2. 각 게시글의 작성자 조회 (N개 쿼리)
   SELECT * FROM users WHERE idx = 1
   SELECT * FROM users WHERE idx = 2
   SELECT * FROM users WHERE idx = 3
   ...
   
총 1 + N개 쿼리 발생
```

#### 2. Fetch Join으로 해결
**위치**: `domain/board/repository/SpringDataJpaBoardRepository.java`
**메서드**: `findAllByIsDeletedFalseOrderByCreatedAtDesc()`

**전체 흐름**:
```
BoardService.getAllBoards()
  ↓
BoardRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc()
  ↓
JOIN FETCH로 작성자 정보 함께 조회
  ↓
1개 쿼리로 모든 데이터 조회
```

**코드 예시**:
```java
// domain/board/repository/SpringDataJpaBoardRepository.java
@Query("SELECT b FROM Board b JOIN FETCH b.user u " +
       "WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' " +
       "ORDER BY b.createdAt DESC")
List<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc();
```

**시각적 설명**:

```
┌─────────────────────────────────────────────────────────────────┐
│                    엔티티 관계도                                  │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   ┌──────────┐                    ┌──────────┐                  │
│   │  Board   │                    │  Users   │                  │
│   │          │                    │          │                  │
│   │ idx: 1   │◄─── user_idx ───── │ idx: 1   │                  │
│   │ title    │   (Many-to-One)    │ email    │                  │
│   │ content  │                    │ nickname │                  │
│   │isDeleted │                    │isDeleted │                  │
│   │createdAt │                    │ status   │                  │
│   └──────────┘                    └──────────┘                  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    쿼리 실행 과정                                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│   Step 1: JOIN FETCH                                            │
│   ┌──────────────────────────────────────────────────────┐      │
│   │  Board 테이블과 Users 테이블을 INNER JOIN               │      │
│   │  (Board.user_idx = Users.idx)                        │      │
│   └──────────────────────────────────────────────────────┘      │
│                        ↓                                        │
│   Step 2: WHERE 조건 필터링                                       │
│   ┌──────────────────────────────────────────────────────┐      │
│   │  ✓ Board.isDeleted = false                           │     │
│   │  ✓ Users.isDeleted = false                           │     │
│   │  ✓ Users.status = 'ACTIVE'                           │     │
│   └──────────────────────────────────────────────────────┘      │
│                        ↓                                        │
│   Step 3: ORDER BY 정렬                                          │
│   ┌──────────────────────────────────────────────────────┐      │
│   │  Board.createdAt DESC (최신순)                        │      │
│   └──────────────────────────────────────────────────────┘      │
│                        ↓                                        │
│   Step 4: 결과 반환                                              │
│   ┌──────────────────────────────────────────────────────┐      │
│   │  Board 엔티티 + User 엔티티 함께 로딩 완료               │      │
│   │  (N+1 문제 없음)                                       │     │
│   └──────────────────────────────────────────────────────┘      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    실제 SQL 변환 예시                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  SELECT                                                         │
│    b.idx, b.title, b.content, b.is_deleted, b.created_at,       │
│    u.idx, u.email, u.nickname, u.is_deleted, u.status           │
│  FROM board b                                                   │
│  INNER JOIN users u ON b.user_idx = u.idx                       │
│  WHERE                                                          │
│    b.is_deleted = false                                         │
│    AND u.is_deleted = false                                     │
│    AND u.status = 'ACTIVE'                                      │
│  ORDER BY b.created_at DESC                                     │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    데이터 예시 (결과)                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────┬─────────────┬──────────┬──────────┬──────────────┐     │
│  │ idx │    title    │  user    │  status  │  created_at  │     │
│  ├─────┼─────────────┼──────────┼──────────┼──────────────┤     │
│  │  3  │ "게시글 3"  │ "홍길동" │ "ACTIVE" │ 2024-01-15     │     │
│  │  2  │ "게시글 2"  │ "김철수" │ "ACTIVE" │ 2024-01-14     │     │
│  │  1  │ "게시글 1"  │ "이영희" │ "ACTIVE" │ 2024-01-13     │     │
│  └─────┴─────────────┴──────────┴──────────┴──────────────┘     │
│                                                                 │
│  ※ JOIN FETCH로 인해 각 Board 객체에 User 정보가                   │
│     이미 로딩되어 있어 추가 쿼리 없음                                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

**결과**: 
- Before: 301개 쿼리
- After: 3개 쿼리 (게시글 조회 + 반응 배치 조회 + 첨부파일 배치 조회)
- **99% 감소**

#### 3. 배치 조회로 추가 최적화
**위치**: `domain/board/service/BoardService.java`
**메서드**: `mapBoardsWithReactionsBatch()`

**전체 흐름**:
```
게시글 목록 조회 (Fetch Join)
  ↓
게시글 ID 리스트 추출
  ↓
IN 절로 반응 정보 배치 조회
  ↓
IN 절로 첨부파일 정보 배치 조회
```

**코드 예시**:
```java
// domain/board/service/BoardService.java
private List<BoardDTO> mapBoardsWithReactionsBatch(List<Board> boards) {
    List<Long> boardIds = boards.stream()
        .map(Board::getIdx)
        .collect(Collectors.toList());
    
    // 배치 조회: 반응 정보
    Map<Long, List<BoardReaction>> reactionsMap = 
        boardReactionRepository.findByBoardIdxIn(boardIds)
            .stream()
            .collect(Collectors.groupingBy(BoardReaction::getBoardIdx));
    
    // 배치 조회: 첨부파일 정보
    Map<Long, List<FileDTO>> filesMap = 
        attachmentFileService.getFilesByTargetIds(boardIds, FileTargetType.BOARD);
    
    // 매핑
    return boards.stream()
        .map(board -> boardConverter.toDTO(board, reactionsMap, filesMap))
        .collect(Collectors.toList());
}
```

---

## Q3-2. Fetch Join과 일반 Join의 차이를 설명해주세요.

### 답변 포인트
- **Fetch Join**: 연관 엔티티를 즉시 로딩하여 N+1 문제 해결
- **일반 Join**: 연관 엔티티를 조회하지 않음 (지연 로딩)
- Fetch Join은 SELECT 절에 연관 엔티티를 포함

### 상세 답변

#### 1. 일반 Join
**문제**: 연관 엔티티를 조회하지 않음

```java
@Query("SELECT b FROM Board b JOIN b.user u WHERE b.isDeleted = false")
List<Board> findAllBoards();
```

**결과**:
- Board 엔티티만 조회
- User 엔티티는 프록시 객체 (지연 로딩)
- `board.getUser()` 호출 시 추가 쿼리 발생 (N+1 문제)

#### 2. Fetch Join
**해결**: 연관 엔티티를 즉시 로딩

```java
@Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.isDeleted = false")
List<Board> findAllBoards();
```

**결과**:
- Board와 User 엔티티를 함께 조회
- `board.getUser()` 호출 시 추가 쿼리 없음
- N+1 문제 해결

**실제 사용 예시**:
```java
// domain/board/repository/SpringDataJpaBoardRepository.java
@Query("SELECT b FROM Board b JOIN FETCH b.user u " +
       "WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE'")
List<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc();
```

#### 3. 다중 Fetch Join
**위치**: `domain/board/repository/SpringDataJpaMissingPetBoardRepository.java`

**코드 예시**:
```java
@Query("SELECT DISTINCT b FROM MissingPetBoard b " +
       "JOIN FETCH b.user u " +
       "LEFT JOIN FETCH b.comments c " +
       "LEFT JOIN FETCH c.user cu " +
       "WHERE b.isDeleted = false")
List<MissingPetBoard> findByIdWithComments(Long id);
```

**주의사항**:
- `DISTINCT` 사용 필요 (중복 데이터 방지)
- 여러 Fetch Join 시 카테시안 곱 발생 가능

---

## Q3-3. JPA의 지연 로딩(Lazy Loading)과 즉시 로딩(Eager Loading)의 차이는?

### 답변 포인트
- **Lazy Loading**: 필요할 때만 조회 (기본값)
- **Eager Loading**: 항상 함께 조회 (N+1 문제 발생 가능)
- Fetch Join으로 필요 시에만 즉시 로딩

### 상세 답변

#### 1. 지연 로딩 (Lazy Loading)
**기본값**: `@OneToMany`, `@ManyToOne`의 기본값

**위치**: `domain/board/entity/Board.java`

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "user_idx")
private Users user;
```

**동작 방식**:
```
Board 조회
  ↓
User는 프록시 객체로 생성
  ↓
board.getUser() 호출 시
  ↓
실제 쿼리 실행 (SELECT * FROM users WHERE idx = ?)
```

**장점**: 필요한 경우에만 조회하여 성능 최적화
**단점**: N+1 문제 발생 가능

#### 2. 즉시 로딩 (Eager Loading)
**사용 시**: `fetch = FetchType.EAGER`

```java
@ManyToOne(fetch = FetchType.EAGER)
@JoinColumn(name = "user_idx")
private Users user;
```

**동작 방식**:
```
Board 조회
  ↓
항상 User도 함께 조회 (JOIN 또는 별도 쿼리)
```

**단점**: 
- 불필요한 데이터까지 조회
- N+1 문제 발생 가능 (별도 쿼리로 조회하는 경우)

#### 3. Fetch Join으로 필요 시 즉시 로딩
**권장 방법**: Fetch Join 사용

**전체 흐름**:
```
필요한 경우에만 Fetch Join 사용
  ↓
한 번의 쿼리로 연관 엔티티까지 조회
  ↓
N+1 문제 해결
```

**사용 예시**:
```java
// 필요할 때만 Fetch Join 사용
@Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.idx = :id")
Optional<Board> findByIdWithUser(@Param("id") Long id);
```

---

## Q3-4. 데이터베이스 인덱스 전략을 설명해주세요.

### 답변 포인트
- 자주 조회되는 컬럼에 인덱스 생성 (id, email, nickname 등)
- UNIQUE 제약조건으로 중복 방지
- 복합 인덱스 활용
- FULLTEXT 인덱스로 검색 성능 향상

### 상세 답변

#### 1. 기본 인덱스
**위치**: `domain/user/entity/Users.java`

**예시**:
```java
@Column(unique = true)
private String email;  // UNIQUE 인덱스 자동 생성

@Column(unique = true)
private String nickname;  // UNIQUE 인덱스 자동 생성
```

#### 2. 복합 인덱스
**위치**: `domain/user/entity/SocialUser.java`

**예시**:
```java
@Table(uniqueConstraints = {
    @UniqueConstraint(columnNames = {"provider", "providerId"})
})
public class SocialUser {
    private String provider;
    private String providerId;
}
```

**용도**: 소셜 로그인 중복 방지

#### 3. FULLTEXT 인덱스
**위치**: `domain/board/repository/SpringDataJpaBoardRepository.java`

**예시**:
```sql
-- 마이그레이션 SQL
CREATE FULLTEXT INDEX ft_name_desc ON board(name, description) WITH PARSER ngram;
```

**용도**: 게시글 제목, 내용 검색 성능 향상

#### 4. 성능 최적화 인덱스
**위치**: `docs/migration/db/indexes_board.sql`

**예시**:
```sql
-- 인기글 조회 최적화
CREATE INDEX idx_board_is_deleted_created_at 
ON board(is_deleted, created_at DESC);

-- 카테고리별 조회 최적화
CREATE INDEX idx_board_category_is_deleted_created_at 
ON board(category, is_deleted, created_at DESC);
```

---

## Q3-5. 쿼리 최적화를 위해 어떤 방법을 사용했나요?

### 답변 포인트
- 배치 조회로 IN 절 활용
- Fetch Join으로 N+1 문제 해결
- 스냅샷 패턴으로 사전 집계
- 인덱스 전략 수립

### 상세 답변

#### 1. 배치 조회 (Batch Query)
**위치**: `domain/board/service/BoardService.java`
**메서드**: `mapBoardsWithReactionsBatch()`

**전체 흐름**:
```
게시글 목록 조회
  ↓
게시글 ID 리스트 추출
  ↓
IN 절로 반응 정보 한 번에 조회
  ↓
IN 절로 첨부파일 정보 한 번에 조회
```

**코드 예시**:
```java
// 500개 단위로 배치 처리
List<Long> boardIds = boards.stream()
    .map(Board::getIdx)
    .collect(Collectors.toList());

// 배치 조회
Map<Long, List<BoardReaction>> reactionsMap = 
    boardReactionRepository.findByBoardIdxIn(boardIds)
        .stream()
        .collect(Collectors.groupingBy(BoardReaction::getBoardIdx));
```

**효과**: 100개 쿼리 → 1개 쿼리

#### 2. Fetch Join
**위치**: 모든 Repository 인터페이스

**사용 예시**:
```java
// domain/board/repository/SpringDataJpaBoardRepository.java
@Query("SELECT b FROM Board b JOIN FETCH b.user u WHERE b.isDeleted = false")
List<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc();
```

**효과**: N+1 문제 해결

#### 3. 스냅샷 패턴
**위치**: `domain/board/service/BoardPopularityService.java`

**전체 흐름**:
```
스케줄러로 주기적 실행
  ↓
인기글 계산 및 스냅샷 저장
  ↓
조회 시 스냅샷 사용
```

**효과**: 복잡한 계산을 사전에 수행하여 조회 성능 향상

#### 4. 인덱스 전략
**위치**: `docs/migration/db/indexes_board.sql`

**예시**:
- `idx_board_is_deleted_created_at`: 삭제 여부 + 생성일 정렬
- `idx_board_category_is_deleted_created_at`: 카테고리 + 삭제 여부 + 생성일
- `ft_name_desc`: FULLTEXT 인덱스 (검색)

---

## Q3-6. MySQL의 ST_Distance_Sphere 함수를 어떻게 활용했나요?

### 답변 포인트
- 위치 기반 거리 계산
- 사용자 위치 기준 10km 반경 검색
- Location 도메인에서 초기 로드 성능 개선 (95.5% 데이터 감소)

### 상세 답변

#### 1. ST_Distance_Sphere 함수
**위치**: `domain/location/repository/SpringDataJpaLocationServiceRepository.java`
**메서드**: `findByRadius()`

**전체 흐름**:
```
사용자 위치 (위도, 경도) 입력
  ↓
반경 (미터 단위) 설정
  ↓
ST_Distance_Sphere로 거리 계산
  ↓
반경 내 위치 서비스만 조회
```

**코드 예시**:
```java
// domain/location/repository/SpringDataJpaLocationServiceRepository.java
@Query(value = "SELECT * FROM locationservice WHERE " +
               "latitude IS NOT NULL AND longitude IS NOT NULL AND " +
               "ST_Distance_Sphere(POINT(longitude, latitude), POINT(?2, ?1)) <= ?3 AND " +
               "(is_deleted IS NULL OR is_deleted = 0) " +
               "ORDER BY rating DESC", 
       nativeQuery = true)
List<LocationService> findByRadius(Double latitude, Double longitude, Double radiusInMeters);
```

**매개변수**:
- `?1`: 위도 (latitude)
- `?2`: 경도 (longitude)
- `?3`: 반경 (미터 단위, 예: 10000 = 10km)

#### 2. 성능 개선 효과
**위치**: `domain/location/service/LocationServiceService.java`
**메서드**: `searchLocationServicesByLocation()`

**Before**:
- 전체 데이터 조회: 22,699개
- 초기 로드 시간: 느림

**After**:
- 사용자 위치 기준 10km 반경: 1,026개
- **95.5% 데이터 감소**
- 초기 로드 시간: 대폭 개선

**전체 흐름**:
```
사용자 위치 정보 수신
  ↓
LocationServiceService.searchLocationServicesByLocation()
  ↓
LocationServiceRepository.findByRadius()
  ↓
ST_Distance_Sphere로 거리 계산
  ↓
반경 내 위치 서비스만 반환
```

#### 3. 거리 순 정렬
**위치**: `domain/location/repository/SpringDataJpaLocationServiceRepository.java`
**메서드**: `findByRadiusOrderByDistance()`

**코드 예시**:
```java
@Query(value = "SELECT * FROM locationservice WHERE " +
               "latitude IS NOT NULL AND longitude IS NOT NULL AND " +
               "ST_Distance_Sphere(POINT(longitude, latitude), POINT(?2, ?1)) <= ?3 AND " +
               "(is_deleted IS NULL OR is_deleted = 0) " +
               "ORDER BY ST_Distance_Sphere(POINT(longitude, latitude), POINT(?2, ?1)) ASC",
       nativeQuery = true)
List<LocationService> findByRadiusOrderByDistance(
    Double latitude, Double longitude, Double radiusInMeters);
```

**용도**: 길찾기 기능에서 가까운 순서로 정렬

---

## 📝 핵심 정리

### N+1 문제 해결
- **Fetch Join**: `JOIN FETCH` 사용
- **배치 조회**: IN 절 활용
- **결과**: 301개 쿼리 → 3개 쿼리 (99% 감소)

### Fetch Join vs 일반 Join
- **Fetch Join**: 연관 엔티티 즉시 로딩
- **일반 Join**: 연관 엔티티 지연 로딩
- **권장**: 필요할 때만 Fetch Join 사용

### 지연 로딩 vs 즉시 로딩
- **지연 로딩**: 기본값, 필요할 때만 조회
- **즉시 로딩**: 항상 함께 조회 (비권장)
- **권장**: Fetch Join으로 필요 시 즉시 로딩

### 인덱스 전략
- **기본 인덱스**: UNIQUE 제약조건
- **복합 인덱스**: 여러 컬럼 조합
- **FULLTEXT 인덱스**: 검색 성능 향상

### 쿼리 최적화
- **배치 조회**: IN 절 활용
- **Fetch Join**: N+1 문제 해결
- **스냅샷 패턴**: 사전 집계
- **인덱스**: 조회 성능 향상

### ST_Distance_Sphere
- **용도**: 위치 기반 거리 계산
- **효과**: 95.5% 데이터 감소
- **사용**: 반경 검색, 거리 순 정렬
