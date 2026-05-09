# DB 개념 어필 포인트 — Board 도메인

> 코드베이스 실측 데이터 기준 (`BoardPerformanceComparisonTest`, `BoardService`, `BoardPopularityService` 등 확인)

---

## 1. N+1 문제 발견 및 해결 (배치 조회 + Fetch Join)

### 어필 포인트

| 시나리오 | Before | After | 개선율 |
|---------|--------|-------|--------|
| 게시글 목록 100개 (전체) | 301개 쿼리, 745ms, 22.50 MB | 3개 쿼리, 30ms, 2 MB | 쿼리 99%, 시간 96%, 메모리 91% |
| 반응 정보만 (Fetch Join 제외) | 201개 쿼리, 244ms, 8 MB | 2개 쿼리, 0ms, 509 KB | 쿼리 99%, 메모리 93% |
| Comment 목록 20개 (반응 배치) | 40+ 쿼리 (2N) | 3~4 쿼리 | 약 90% |

**Before (301개 쿼리 분해)**
- 게시글 조회: 1개
- 작성자 LAZY 로딩: 10개 (10명 순환 사용)
- 좋아요 카운트 개별 조회: 100개
- 싫어요 카운트 개별 조회: 100개
- 첨부파일 개별 조회: 100개

**After (3개 쿼리)**
1. `JOIN FETCH b.user` — 게시글 + 작성자 1회
2. `WHERE board_idx IN (…) GROUP BY board_idx, reaction_type` — 반응 배치 1회
3. 첨부파일 배치 조회 1회

**핵심 구현**

```java
// BoardService.java
private Map<Long, Map<ReactionType, Long>> getReactionCountsBatch(List<Long> boardIds) {
    final int BATCH_SIZE = 500; // IN 절 크기 제한
    Map<Long, Map<ReactionType, Long>> countsMap = new HashMap<>();

    for (int i = 0; i < boardIds.size(); i += BATCH_SIZE) {
        List<Long> batch = boardIds.subList(i, Math.min(i + BATCH_SIZE, boardIds.size()));
        List<Object[]> results = boardReactionRepository.countByBoardsGroupByReactionType(batch);
        // Object[] = [boardId, reactionType, count]
        parseBatchReactionCountResults(results).forEach(countsMap::putAll);
    }
    return countsMap;
}
```

```java
// SpringDataJpaBoardReactionRepository.java
@Query("SELECT br.board.idx, br.reactionType, COUNT(br) " +
       "FROM BoardReaction br " +
       "WHERE br.board.idx IN :boardIds " +
       "GROUP BY br.board.idx, br.reactionType")
List<Object[]> countByBoardsGroupByReactionType(@Param("boardIds") List<Long> boardIds);
```

### 말할 내용
> "게시글 목록 조회 시 작성자·반응·첨부파일에서 동시에 N+1이 발생했습니다. 100개 게시글 조회 시 301개 쿼리가 발생했고 745ms가 걸렸는데, Fetch Join과 배치 IN 조회를 적용해 3개 쿼리·30ms로 줄였습니다. 배치 처리는 MySQL IN 절 크기 제한을 고려해 500개 단위로 분할합니다. 게시글 수가 100개든 1000개든 쿼리는 일정하게 3개로 유지됩니다."

---

## 2. 인덱스 설계

### 어필 포인트

**board 테이블**
```sql
-- 복합 인덱스: 카테고리별 삭제 여부 + 생성일 정렬
CREATE INDEX idx_board_category_deleted_created ON board(category, is_deleted, created_at);
-- 생성일 내림차순 정렬 전용
CREATE INDEX idx_board_created_at_desc ON board(created_at);
-- 삭제 여부 + 생성일 (카테고리 미지정 목록 조회)
CREATE INDEX idx_board_deleted_created ON board(is_deleted, created_at);
-- 상태 필터링
CREATE INDEX idx_board_status ON board(status);
-- 제목·본문 FULLTEXT 검색 (ngram 파서, 한글 지원)
CREATE FULLTEXT INDEX idx_board_title_content ON board(title, content) WITH PARSER ngram;
-- 사용자별 게시글 + 삭제 여부 + 생성일
CREATE INDEX idx_board_user_deleted_created ON board(user_idx, is_deleted, created_at);
```

**board_popularity_snapshot 테이블**
```sql
-- 게시글별 스냅샷 조회
CREATE INDEX idx_snapshot_board_id ON board_popularity_snapshot(board_id);
-- 기간 범위 조회
CREATE INDEX idx_snapshot_range ON board_popularity_snapshot(period_type, period_start_date, period_end_date);
-- 최신 스냅샷 + 랭킹 조회
CREATE INDEX idx_snapshot_recent ON board_popularity_snapshot(period_type, period_end_date, ranking);
```

**board_reaction 테이블**
```sql
-- 사용자 FK 인덱스 (FKag3ixpa53bjp1p5s79myoscpr)
CREATE INDEX FKag3ixpa53bjp1p5s79myoscpr ON board_reaction(user_idx);
-- 게시글+사용자 중복 방지 Unique (UKaymqx4hghgrqitkbplgp553u0)
CREATE UNIQUE INDEX UKaymqx4hghgrqitkbplgp553u0 ON board_reaction(board_idx, user_idx);
```

**board_view_log 테이블**
```sql
-- 사용자 FK 인덱스 (FKemjj96yrflacv5mtek2nipy22)
CREATE INDEX FKemjj96yrflacv5mtek2nipy22 ON board_view_log(user_id);
-- 게시글+사용자 중복 방지 Unique
CREATE UNIQUE INDEX uk_board_view_log_board_user ON board_view_log(board_id, user_id);
```

**comment 테이블**
```sql
-- 게시글별 댓글 조회 FK 인덱스
CREATE INDEX board_idx ON comment(board_idx);
-- 댓글 상태 필터링
CREATE INDEX idx_comment_status ON comment(status);
-- 사용자별 댓글 조회 FK 인덱스
CREATE INDEX user_idx ON comment(user_idx);
```

**comment_reaction 테이블**
```sql
-- 사용자 FK 인덱스 (FK24cjwe1ksjmeujkgoa6f2pya)
CREATE INDEX FK24cjwe1ksjmeujkgoa6f2pya ON comment_reaction(user_idx);
-- 댓글+사용자 중복 방지 Unique (UKbes4ghhrkss5cdpx28ugh86gh)
CREATE UNIQUE INDEX UKbes4ghhrkss5cdpx28ugh86gh ON comment_reaction(comment_idx, user_idx);
```

**설계 근거**
- **복합 인덱스 순서**: 등치 조건(category, is_deleted) 먼저, 범위·정렬(created_at) 마지막
- **Unique 제약조건**: board_reaction, board_view_log, comment_reaction — 중복 방지 + 조회 성능
- **FULLTEXT ngram**: LIKE 검색 대비 고성능, 한글 n-gram 분할로 형태소 분석 없이 부분 검색 지원
- **board_reaction**: `(board_idx, reaction_type)` 복합 인덱스는 실제 미존재 — GROUP BY 배치 쿼리는 `UKaymqx4hghgrqitkbplgp553u0(board_idx, user_idx)` Unique 인덱스를 활용

### 말할 내용
> "`board_reaction`에는 `(board_idx, user_idx)` Unique 제약조건(UKaymqx4hghgrqitkbplgp553u0)이 있어 중복 저장을 DB 레벨에서 차단하고, 배치 집계 쿼리(WHERE board_idx IN … GROUP BY board_idx, reaction_type)도 이 인덱스를 통해 빠르게 처리됩니다. `board_view_log`의 `uk_board_view_log_board_user`도 같은 방식입니다. `board` 테이블에는 조회 패턴에 따라 복합 인덱스를 3개로 나눠 설계했으며, 등치 조건 컬럼을 앞에 두고 범위·정렬 컬럼을 뒤에 두는 순서를 지켰습니다."

---

## 3. ORM 최적화 (JPA/Hibernate)

### 어필 포인트

**1) JOIN FETCH — 모든 목록 쿼리에 적용**
```java
// SpringDataJpaBoardRepository.java
@Query("SELECT b FROM Board b JOIN FETCH b.user u " +
       "WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' " +
       "ORDER BY b.createdAt DESC")
Page<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable);
```

**2) Lazy Loading 함정 제거 — BoardConverter**
- 이전: `board.getComments().size()` → LAZY 로딩 N+1 트리거
- 이후: `board.getCommentCount()` (엔티티 필드 직접 읽기)

**3) dislikeCount 엔티티 필드화 — 쿼리 제거**
- 이전: `buildBoardSummary()` 호출 시 LIKE/DISLIKE 카운트 쿼리 2회
- 이후: Board 엔티티에 `dislikeCount` 필드 추가, 반응 변경 시 실시간 업데이트 → 추가 쿼리 0회

**4) Specification 패턴 — 관리자 페이징**
- 이전: 전체 게시글 메모리 로드 후 Stream filter → 10만 건이면 OOM 위험
- 이후: `Specification<Board>` + `JpaSpecificationExecutor` → DB 레벨 필터링
```java
// BoardService.getAdminBoardsWithPagingOptimized()
Specification<Board> spec = (root, query, cb) -> cb.equal(root.get("isDeleted"), deleted);
// status, category, keyword 조건 and() 체이닝
Page<Board> boardPage = boardRepository.findAll(spec, pageable);
```

### 말할 내용
> "LAZY 로딩이 실제 문제가 됐던 사례를 두 가지 겪었습니다. 첫 번째는 Board 목록 조회 시 작성자 100명 개별 조회, 두 번째는 BoardConverter에서 `getComments().size()`로 댓글 컬렉션을 통째로 로드한 것입니다. 전자는 JOIN FETCH, 후자는 엔티티 카운트 필드로 해결했습니다. dislikeCount 필드를 Board 엔티티에 추가해 반응 API 응답 시 추가 쿼리를 없앤 것도 같은 맥락입니다."

---

## 4. 트랜잭션 설계 및 동시성 제어

### 어필 포인트

**1) 트랜잭션 범위 최소화**
- `@Transactional(readOnly = true)` — 클래스 레벨 기본값, 조회는 전부 readOnly
- 쓰기 메서드에만 `@Transactional` 오버라이드

**2) 반응 중복 방지 — DB Unique 제약조건 활용**
```java
@Table(name = "board_reaction", uniqueConstraints = {
    @UniqueConstraint(columnNames = { "board_idx", "user_idx" })
})
```
- 동시 클릭 시에도 DB가 최종 방어선으로 중복 차단
- 애플리케이션에서는 `findByBoardAndUser` 후 토글 로직으로 처리

**3) 조회수 중복 방지 — BoardViewLog + Unique 제약조건**
- `(board_id, user_id)` Unique로 동일 사용자 중복 저장 차단
- `shouldIncrementView()`: 기존 기록 확인 → 없으면 로그 저장 + 카운트 증가

**4) 소프트 삭제 (Soft Delete)**
- `isDeleted`, `deletedAt`, `status(DELETED)` 3개 필드 관리
- 게시글 삭제 시 연관 댓글도 cascade soft delete

**5) 이메일 인증 게이트**
- 게시글/댓글 수정·삭제 전 `emailVerified` 확인
- 미인증 시 `EmailVerificationRequiredException(purpose)` — 클라이언트가 재인증 플로우 분기 활용

### 말할 내용
> "Service 클래스에 `@Transactional(readOnly = true)`를 기본으로 걸고 쓰기 메서드만 `@Transactional`을 오버라이드했습니다. 조회수 중복 방지는 BoardViewLog의 Unique 제약조건으로 DB 레벨에서 보장하고, 반응 토글도 board_reaction의 Unique 제약으로 동시 클릭을 막습니다. 애플리케이션 레벨 체크와 DB 제약을 이중으로 운용해 데이터 정합성을 보장합니다."

---

## 5. 인기글 스냅샷 패턴 (Snapshot Pattern)

### 어필 포인트

**문제**: 인기글 조회 시마다 전체 게시글 집계 → 데이터 증가에 비례해 느려짐

**해결**: 스케줄러로 미리 계산·저장, 조회는 스냅샷에서만

```java
// BoardPopularityScheduler.java
@Scheduled(cron = "0 30 18 * * ?")      // 매일 18:30 — 주간
@Scheduled(cron = "0 30 18 ? * MON")    // 매주 월요일 18:30 — 월간
```

**인기도 점수**: (좋아요 × 3) + (댓글 × 2) + 조회수  
**대상**: "자랑" 카테고리 상위 30개

**4단계 fallback 조회 전략**
1. 정확한 날짜 매칭 스냅샷
2. 기간 겹치는 스냅샷 (Specification)
3. 가장 최근 스냅샷
4. 모두 없으면 즉시 생성

**인기글 스냅샷 생성 시 배치 집계 — CompletableFuture 병렬 처리**

이전: 좋아요·댓글·조회수 배치 조회 3개 순차 실행 (총 ~300ms)
```java
// Before
Map<Long, Integer> likeCountsMap = getLikeCountsBatch(boardIds);
Map<Long, Integer> commentCountsMap = getCommentCountsBatch(boardIds);
Map<Long, Integer> viewCountsMap = getViewCountsBatch(boardIds);
```

이후: CompletableFuture로 병렬 실행 + `BoardCounts` record로 통합 (~100ms)
```java
// After — BoardPopularityService.java
Map<Long, BoardCounts> countsMap = fetchBoardCountsInParallel(boardIds);
record BoardCounts(int likes, int comments, int views) {
    static final BoardCounts ZERO = new BoardCounts(0, 0, 0);
}
```

**LIKE 전용 쿼리 최적화**
- 이전: `GROUP BY reaction_type` 전체 반환 후 Java에서 `if (reactionType == LIKE)` 필터
- 이후: JPQL `AND br.reactionType = :reactionType` — DB에서 LIKE만 조회

### 말할 내용
> "인기글을 요청마다 계산하면 데이터가 늘수록 선형으로 느려집니다. 스케줄러로 매일 18:30에 미리 집계·저장하고, 조회는 스냅샷만 읽도록 분리했습니다. 스냅샷 생성 시에는 좋아요·댓글·조회수 3개 집계 쿼리를 CompletableFuture로 병렬 실행해 순차 실행 대비 시간을 3분의 1로 줄였습니다. 스냅샷이 없는 경우를 위해 4단계 fallback 전략도 구현했습니다."

---

## 6. 검색 성능 최적화 — FULLTEXT 인덱스

### 어필 포인트

**LIKE 검색의 문제**: `WHERE LIKE '%keyword%'` → 인덱스 미사용, 전체 테이블 스캔

**FULLTEXT 인덱스 (ngram 파서)**:
```sql
CREATE FULLTEXT INDEX idx_board_title_content ON board(title, content) WITH PARSER ngram;
```

```java
// SpringDataJpaBoardRepository.java
@Query(value = "SELECT b.*, MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE) AS relevance " +
               "FROM board b INNER JOIN users u ON b.user_idx = u.idx " +
               "WHERE b.is_deleted = false AND u.is_deleted = false AND u.status = 'ACTIVE' " +
               "AND MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE) " +
               "ORDER BY relevance DESC, b.created_at DESC",
       countQuery = "...", nativeQuery = true)
Page<Board> searchByKeywordWithPaging(@Param("kw") String keyword, Pageable pageable);
```

**검색 타입 분기**
- `TITLE_CONTENT` (기본): FULLTEXT 인덱스, relevance 점수 기반 정렬
- `NICKNAME`: `JOIN FETCH u WHERE u.nickname LIKE :nickname%` — 접두사 검색, 인덱스 활용

### 말할 내용
> "제목·내용 통합 검색에 MySQL FULLTEXT 인덱스(ngram 파서)를 적용했습니다. ngram은 한글을 n-gram 방식으로 분할해 형태소 분석 없이도 부분 검색이 가능합니다. LIKE 검색과 달리 인덱스를 사용하며 relevance 점수 기반으로 관련도 높은 결과를 먼저 반환합니다."

---

## 7. DB 레벨 측정 및 검증

### 어필 포인트

**Hibernate Statistics 활용**
- `BoardPerformanceComparisonTest` — 실제 쿼리 수 측정
- `entityManager.clear()` + L2 캐시 evictAll() 후 정확한 측정
- 100개 게시글 × 10명 작성자 × 반응 700개 (좋아요 500, 싫어요 200) 테스트 데이터

**실측값 (테스트 3 — 전체 시나리오)**

| 측정 항목 | 최적화 전 | 최적화 후 |
|----------|---------|---------|
| 쿼리 수 | 301개 | 3개 |
| 실행 시간 | 745ms | 30ms |
| 메모리 사용량 | 22.50 MB | 2 MB |

**확장성 검증**
- 게시글 100개, 1000개 관계없이 쿼리는 항상 3개 (배치 IN 조회)
- IN 절 500개 단위 분할 → 게시글 수 증가에도 안전

### 말할 내용
> "Hibernate Statistics로 쿼리 수를 정량 측정해서 최적화 전후를 비교했습니다. 영속성 컨텍스트가 캐시를 오염시키지 않도록 entityManager.clear()와 L2 캐시 evictAll()을 호출하고 측정했습니다. 745ms → 30ms, 301개 → 3개, 메모리 22.5MB → 2MB라는 측정 결과를 근거로 가져갈 수 있습니다."

---

## 8. 연관 최적화 패턴 요약

| 패턴 | 적용 위치 | 효과 |
|------|---------|------|
| Fetch Join | 모든 Board 목록 쿼리 | 작성자 N+1 제거 |
| 배치 IN 조회 | 반응·첨부파일 | 2N 쿼리 → 1~2 쿼리 |
| 엔티티 카운트 필드 | likeCount, dislikeCount, commentCount | 집계 쿼리 제거 |
| Unique 제약조건 | board_reaction, board_view_log, comment_reaction | DB 레벨 중복 방지 |
| Specification | Admin 페이징 | 메모리 필터링 → DB 필터링 |
| Snapshot Pattern | 인기글 조회 | 실시간 집계 → 즉시 응답 |
| CompletableFuture | 스냅샷 생성 배치 집계 | 순차 → 병렬, 시간 1/3 |
| FULLTEXT (ngram) | 게시글 검색 | 전체 스캔 → 인덱스 |
| Soft Delete | Board, Comment | 데이터 보존 + 감사 |

---

## 9. 면접 대답 구성

### "DB 관련 경험이 있나요?"

**1단계 — 문제 발견 (30초)**
> "게시글 목록 조회 시 100개 게시글 기준 301개 쿼리, 745ms가 걸리는 성능 문제를 Hibernate Statistics로 발견했습니다."

**2단계 — 원인 분석 (1분)**
> "세 가지 N+1이 동시에 발생했습니다. 작성자 정보 LAZY 로딩(100쿼리), 좋아요 카운트 개별 조회(100쿼리), 싫어요 카운트 개별 조회(100쿼리)입니다. 거기에 BoardConverter가 `board.getComments().size()`로 댓글 컬렉션 전체를 로드하는 문제도 있었습니다."

**3단계 — 해결 방법 (1분)**
> "JOIN FETCH로 작성자를 한 번에, IN 절 배치 조회로 반응 카운트를 한 번에 처리했습니다. BoardConverter는 엔티티의 commentCount 필드로 교체했습니다. 반응 중복 방지는 `(board_idx, user_idx)` Unique 제약조건으로 DB 레벨에서 보장합니다."

**4단계 — 결과 (30초)**
> "301개 → 3개 쿼리, 745ms → 30ms, 메모리 22.5MB → 2MB로 개선됐습니다. 게시글 수가 늘어도 쿼리 수는 일정하게 3개로 유지됩니다."

---

## 핵심 키워드

- N+1 문제 해결 (Fetch Join + 배치 IN 조회)
- 복합 인덱스 설계 (카디널리티 순서)
- FULLTEXT 인덱스 (ngram 파서, 한글 검색)
- Unique 제약조건 활용 (DB 레벨 중복 방지)
- Snapshot Pattern (인기글 미리 계산)
- CompletableFuture 병렬 배치 집계
- Specification 패턴 (동적 쿼리, DB 레벨 필터링)
- Soft Delete (isDeleted + status + deletedAt)
- Hibernate Statistics 실측 검증
- readOnly 트랜잭션 분리

---

## 관련 문서

- 리팩토링: `docs/refactoring/board/board-backend-performance-optimization.md`
- 리팩토링: `docs/refactoring/board/board-popularity-snapshot-batch-analysis.md`
- 리팩토링: `docs/refactoring/board/board-popularity-snapshot-batch-refactoring.md`
- 트러블슈팅: `docs/troubleshooting/board/performance-optimization.md`
- 도메인 스펙: `docs/domains/board.md`
