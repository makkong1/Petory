---
date: 2026-09-09
domains: [board]
type: refactoring
problem: redundant-users-join-in-fulltext-search
status: verified
metric: "검색 쿼리 users 조인 제거 — 결과 동일(3키워드 7,666/7,667/7,667), A/B/A 교대 6회 중앙값 19.9ms→18.6ms. 이득은 작고 근거는 경로 간 일관성"
before_commit: dfbeac61
related: [docs/refactoring/board/board-backend-performance-optimization.md]
---

# 게시글 검색 쿼리의 중복 `users` 조인

> 2026-09-09 작성. **상태: 미적용(판단 대기).** 아직 코드를 고치지 않았다.
> Elasticsearch 도입 검토차 검색 경로를 실측하다 부수적으로 발견했다.

---

## 발생 위치

| 역할 | 클래스 | 위치 |
|---|---|---|
| **문제 쿼리** | `SpringDataJpaBoardRepository` | `:48~61` `searchByKeywordWithPaging` (본문 + countQuery 양쪽) |
| 포트 인터페이스 | `BoardRepository` | `:69` |
| 어댑터 | `JpaBoardAdapter` | `:126~127` |
| 호출부 | `BoardService` | `:306` |
| 엔드포인트 | `BoardController` | `:107~109` `GET /api/boards/search` |
| 컬럼 정의 | `V6__board_author_visible.sql` | `author_visible` 컬럼 + `trg_board_author_visible` 트리거 |

경로: `backend/main/java/com/linkup/Petory/domain/board/`

---

## 문제

`author_visible`은 **`users` 조인을 없애려고 만든 비정규화 컬럼**인데, 검색 쿼리만 그 컬럼을 쓰면서 조인도 그대로 하고 있다.

### 현재 쿼리 (`SpringDataJpaBoardRepository:48~60`)

```sql
SELECT b.*, MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE) AS relevance
FROM board b
INNER JOIN users u ON b.user_idx = u.idx      -- ← users 에서 아무것도 안 가져옴
WHERE b.is_deleted = false
  AND u.is_deleted = false                     -- ← author_visible 이 이미 담고 있음
  AND b.author_visible = 1
  AND MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE)
ORDER BY relevance DESC, b.created_at DESC
```

`countQuery`(`:55~60`)도 동일한 조인을 갖고 있다.

### 왜 중복인가 — `V6` 정의

```sql
-- 의미: author_visible = (미탈퇴 AND status<>BANNED). 정지(SUSPENDED)는 보임(일시적).
UPDATE board b JOIN users u ON u.idx = b.user_idx
SET b.author_visible = IF(u.is_deleted = 0 AND u.status <> 'BANNED', 1, 0);
```

`author_visible = 1` ⟹ `u.is_deleted = 0`. **`AND u.is_deleted = false`는 논리적으로 아무 행도 더 거르지 못한다.**

그리고 `SELECT` 절이 `b.*`와 relevance뿐이라 **조인이 오직 그 중복 조건 하나 때문에 붙어 있다.**

### 결정적 근거 — 코드가 스스로 원칙을 적어놨다

같은 파일 `SpringDataJpaBoardRepository:74`:

```java
// 1단계: 커버링 인덱스로 깊은 skip. author_visible 로 걸러 users 조인이 필요없다.
@Query(value = "SELECT idx FROM board WHERE is_deleted = 0 AND author_visible = 1 ...")
List<Long> findVisibleBoardIds(...)
```

**목록 경로는 이미 조인을 뺐다.** 대조:

| 경로 | 메서드 | `users` 조인 |
|---|---|---|
| 목록 1단계 | `findVisibleBoardIds` `:75` | **없음** |
| 목록 COUNT | `countVisible` `:94` | **없음** |
| 카테고리 COUNT | `countVisibleByCategory` `:97` | **없음** |
| **검색 본문** | `searchByKeywordWithPaging` `:48` | **있음** 🔴 |
| **검색 COUNT** | 같은 메서드 `countQuery` `:55` | **있음** 🔴 |

→ **같은 `author_visible`을 두고 경로마다 신뢰 수준이 다르다.** 목록은 컬럼만 믿고, 검색은 조인으로 한 번 더 확인한다. §1(깊은 페이지 페이징)에서 "컬럼을 믿는다"로 이미 결정했는데 검색 경로가 안 따라간 것이다.

### 부수 발견 — 조건만 중복인 곳 하나 더

`SpringDataJpaBoardRepository:115~117` `searchBoardListItemsByNickname`:

```sql
WHERE u.nickname LIKE :nickname% AND b.isDeleted = false
  AND u.isDeleted = false          -- ← 중복
  AND b.authorVisible = true
```

여기는 **닉네임으로 검색하니 조인 자체는 필요하다.** 다만 `u.isDeleted = false` 조건은 위와 같은 이유로 중복이다. 조인은 남기고 조건만 뺄 수 있다.

---

## 검증 (2026-09-09, 개발 DB `petory`, board 50,000행)

### ① 조인이 행을 떨구지 않는가

| 항목 | 결과 |
|---|---|
| FK `board_ibfk_1` (board.user_idx → users.idx) | 존재 |
| orphan board (`LEFT JOIN` 후 `u.idx IS NULL`) | **0건** |

→ `INNER JOIN`이 `LEFT JOIN`과 동일하게 동작한다. 조인 제거가 행을 늘리지 않는다.

### ② 결과 동일성

| 키워드 | 조인 있음 | 조인 없음 |
|---|---|---|
| 산책 | 7,666 | **7,666** |
| 미용 | 7,667 | **7,667** |
| 예방접종 | 7,667 | **7,667** |

---

## 측정 (A/B/A 교대 6회, 버퍼풀 워밍 후, `SHOW PROFILES`)

| | 평균 | 중앙 | 최소 |
|---|---|---|---|
| A 조인 있음 | 22.8ms | 19.9ms | 19.3ms |
| B 조인 없음 | 18.8ms | **18.6ms** | **17.6ms** |

**이득은 작다 — 중앙값 1.3ms, 약 6%.**

> ⚠️ **첫 측정은 틀렸다.** 단발로 재니 54.1ms vs 28.3ms(약 2배)가 나왔는데, A/B/A로 되돌려 재보니 A'가 21.6ms였다. **첫 A가 콜드 버퍼풀이었을 뿐이다.** 교대 반복으로 다시 잡은 값이 위 표다.

> ⚠️ **더미 데이터 편향.** 시드가 템플릿("~ 관련 질문드립니다 #N", 본문 평균 130자)이라 키워드 하나에 **7,666건(전체의 15%)**이 매칭된다. 실제 검색은 매칭이 훨씬 적고, 그러면 조인 비용도 더 작아진다. **"6% 빨라진다"를 실서비스 수치로 쓰면 안 된다.**

---

## 리팩토링 계획 (미적용)

### 1. `SpringDataJpaBoardRepository:48~61` — 검색 쿼리의 `users` 조인 제거

- **타입**: 🏗️ **Structure** (성능 아님)
- **Before**: 본문·countQuery 모두 `INNER JOIN users` + `u.is_deleted = false`
- **After**: `board` 단일 테이블. 목록 경로(`findVisibleBoardIds`·`countVisible`)와 동일한 형태
- **측정 기준**: **결과 건수 동일**(회귀 테스트로 고정)이 1순위. 응답 시간은 부수 효과이며 중앙값 19.9 → 18.6ms
- **영향 파일**: Repository 1개. 포트·어댑터·서비스·컨트롤러 **시그니처 변경 없음**

### 2. `SpringDataJpaBoardRepository:115~117` — 닉네임 검색의 중복 조건 제거

- **타입**: 📖 **Readability**
- **Before**: `u.isDeleted = false AND b.authorVisible = true`
- **After**: `b.authorVisible = true` (조인은 닉네임 검색에 필요하므로 유지)
- **측정 기준**: 결과 동일. 성능 영향 없음(조건 하나 제거)

---

## 판단이 필요한 지점

**타입을 Performance가 아니라 Structure로 잡은 이유가 여기 있다.**

조인을 빼면 **`author_visible`을 전적으로 믿게 된다.** 지금은 트리거(`trg_board_author_visible`)가 실패하거나 데이터가 드리프트하면 조인이 마지막 안전망 역할을 한다.

다만 **그 안전망이 이미 일관되지 않다** — 목록 경로엔 조인이 없어서, 트리거가 깨지면 탈퇴·밴 회원 글이 **목록에는 이미 노출된다.** 검색만 막아봐야 반쪽이다.

그래서 선택지는 둘이다:

| 안 | 내용 | 평가 |
|---|---|---|
| **(a) 검색에서도 조인 제거** | 목록과 동일하게 `author_visible`만 신뢰 | §1이 이미 정한 방향. 일관성 확보 |
| (b) 목록에 조인 복원 | 안전망 우선 | §1의 성능 개선을 되돌리게 됨 |

**(a)를 제안한다.** 다만 이건 "빨라지니까"가 아니라 **"같은 컬럼에 대한 신뢰 수준을 경로마다 다르게 두지 않는다"**가 이유다. 성능 이득(1.3ms)은 근거로 쓰기엔 약하다.

---

## 개선 코드

### 1. `SpringDataJpaBoardRepository:47~60` — 조인 제거

```diff
  @Query(value = "SELECT b.*, MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE) AS relevance "
          + "FROM board b "
-         + "INNER JOIN users u ON b.user_idx = u.idx "
          + "WHERE b.is_deleted = false "
-         + "AND u.is_deleted = false "
          + "AND b.author_visible = 1 "
          + "AND MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE) "
          + "ORDER BY relevance DESC, b.created_at DESC",
    countQuery = "SELECT COUNT(*) FROM board b "
-         + "INNER JOIN users u ON b.user_idx = u.idx "
          + "WHERE b.is_deleted = false "
-         + "AND u.is_deleted = false "
          + "AND b.author_visible = 1 "
          + "AND MATCH(b.title, b.content) AGAINST(:kw IN BOOLEAN MODE)", nativeQuery = true)
```

### 2. `SpringDataJpaBoardRepository:115~118` — 중복 조건만 제거 (조인 유지)

```diff
- WHERE u.nickname LIKE :nickname% AND b.isDeleted = false AND u.isDeleted = false AND b.authorVisible = true
+ WHERE u.nickname LIKE :nickname% AND b.isDeleted = false AND b.authorVisible = true
```

### 3. 회귀 테스트 2개 — `IndexUsageRegressionTest` (8개 → 10개)

- `boardSearchQueryHasNoRedundantUsersJoin` — `@Query` 문자열에 `JOIN users`가 없고 `author_visible = 1`은 있는지 리플렉션 검사(본문·countQuery 양쪽). EXPLAIN 단언은 통계 추정에 따라 흔들려서 이 파일의 기존 관례대로 텍스트를 본다.
- `authorVisibleSubsumesUserDeletedFilter` — **키워드 표본 비교 대신 불변식 자체**를 검증한다. ① `author_visible = 1`인데 작성자가 탈퇴인 행 0건 ② orphan board 0건. 둘 다 성립하면 조인 제거는 **어떤 키워드에서도** 결과를 바꾸지 않는다.

---

## 검증 결과 (2026-09-09)

| 테스트 | 결과 |
|---|---|
| `IndexUsageRegressionTest` | **10/10** (신규 2개 포함) |
| `AuthorVisibleTriggerTest` | 3/3 |
| `FulltextParserRegressionTest` | 3/3 |
| `BoardListVisibilityTest` | 2/2 |
| `BoardServiceListProjectionTest` | 3/3 |
| `BoardListTieBreakTest` | 3/3 |
| `QueryCountScalingRegressionTest` | 3/3 |

`./gradlew compileJava compileTestJava` 통과. 실패·에러 0.

---

## 안 건드린 것 (후속 후보)

같은 클래스에 **조건만 중복**인 곳이 두 군데 더 있다. 조인은 `JOIN FETCH`로 작성자 엔티티를 실제로 로딩하므로 **필요하고**, `u.isDeleted = false` 조건만 잉여다.

| 메서드 | 위치 |
|---|---|
| `findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc` | `:40` |
| `findByUserAndIsDeletedFalseOrderByCreatedAtDesc` | `:44` |

이번 PR 범위 밖이라 손대지 않았다(승인받은 계획은 2건). 성능 영향은 없고 순수 가독성 항목이다.

---

## 상태

- **개선 완료** (2026-09-09)
- 브랜치 `perf/board-search-drop-redundant-join` → `dev` 머지 → `main` PR
