# board 깊은 페이지 페이징 (지연 조인 + author_visible 비정규화) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** board 목록의 깊은 페이지 OFFSET 비용과 그에 딸린 자동 COUNT 비용을, 번호 페이징 UI를 그대로 둔 채 지연 조인 + `author_visible` 비정규화로 해결한다.

**Architecture:** board에 `author_visible`(= 미탈퇴 AND status≠BANNED) 컬럼을 두고, `users AFTER UPDATE` 트리거로 동기화한다. 목록 쿼리는 2단계(native 커버링 idx skip → JPQL projection by IN)로, COUNT는 board 단일 테이블로 바꾼다. 변경은 리포지토리 + Flyway V6에 갇히고 어댑터·서비스·컨트롤러·프론트는 무변경이다.

**Tech Stack:** Spring Boot 3.5.7 / Java 17 / JPA(Hibernate) / MySQL 8.4 / Flyway / JUnit5 + AssertJ / k6

## Global Constraints

- 스키마 변경은 `db/migration/V*.sql` 새 파일로만. 적용된 V1~V5 수정 금지. 다음 번호는 **V6**.
- `ddl-auto=validate` — 엔티티에 매핑한 컬럼은 실제 스키마와 일치해야 기동 성공.
- 테스트 DB는 `petory_test` (개발 `petory` 아님). DB 상태를 바꿔가며 검증할 땐 `cleanTest` 필수.
- 쿼리 측정 시 스케줄러 끄기: `--petory.scheduling.enabled=false`.
- 목록 DTO 변환에 `Page.map(converter::toDTO)` 금지 — N+1. 배치는 `toDTOList`.
- 좌표/불리언 비정규화는 트리거로 채우고 엔티티 매핑은 `updatable=false`로 갱신 소유권을 트리거에 둔다.
- 커밋 메시지 말미: `Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>`
- 로컬 앱은 8081 포트로 띄워 검증(도커 8080·MySQL 3307은 배포용, 건드리지 않음). 로컬 DB는 `petory`.
- 시드 로그인 비밀번호: 전원 `Seed1234!`.

---

### Task 1: Flyway V6 — author_visible 컬럼·백필·인덱스·트리거 + 엔티티 매핑

**Files:**
- Create: `backend/main/resources/db/migration/V6__board_author_visible.sql`
- Modify: `backend/main/java/com/linkup/Petory/domain/board/entity/Board.java` (필드 추가, `is_deleted` 매핑 근처)

**Interfaces:**
- Produces: `board.author_visible` 컬럼(TINYINT NOT NULL DEFAULT 1), 인덱스 `idx_board_visible_created`·`idx_board_cat_visible_created`, 트리거 `trg_board_author_visible`; 엔티티 필드 `Board.authorVisible : Boolean`.

- [ ] **Step 1: V6 마이그레이션 작성**

Create `backend/main/resources/db/migration/V6__board_author_visible.sql`:

```sql
-- board 목록 깊은 페이지 + 자동 COUNT 비용의 근본 원인은 "조인 건너편 작성자 필터"다.
-- (u.is_deleted=0 AND u.status='ACTIVE' 가 board 인덱스만으로 offset 을 못 세게 만든다)
-- 작성자 보임 여부를 board 컬럼으로 내려 커버링 idx skip + 단일 테이블 COUNT 를 가능케 한다.
-- 의미: author_visible = (미탈퇴 AND status<>BANNED). 정지(SUSPENDED)는 보임(일시적).

ALTER TABLE board ADD COLUMN author_visible TINYINT(1) NOT NULL DEFAULT 1;
-- 새 글은 항상 보임: 밴/탈퇴 회원은 글 생성 경로에서 차단되므로 DEFAULT 1 이 정확하다.

-- 기존 행 백필
UPDATE board b JOIN users u ON u.idx = b.user_idx
SET b.author_visible = IF(u.is_deleted = 0 AND u.status <> 'BANNED', 1, 0);

-- 전체 목록 + COUNT 커버링 (등가 2컬럼 + created_at 정렬)
ALTER TABLE board ADD INDEX idx_board_visible_created (is_deleted, author_visible, created_at DESC);
-- 카테고리 목록 커버링
ALTER TABLE board ADD INDEX idx_board_cat_visible_created (category, is_deleted, author_visible, created_at DESC);

-- 동기화: 회원 상태를 바꾸는 모든 경로(관리자 밴/언밴, 제재, 탈퇴, 재활성화)는 결국 users 를
-- UPDATE 한다. 트리거 하나로 전부 잡는다. is_deleted 또는 BANNED 경계가 바뀔 때만 발동한다
-- (로그인의 last_login_at 갱신 등 흔한 UPDATE 에는 안 걸리고, SUSPENDED<->ACTIVE 도 안 건드림).
CREATE TRIGGER trg_board_author_visible AFTER UPDATE ON users
FOR EACH ROW
  IF (OLD.is_deleted <> NEW.is_deleted)
     OR ((OLD.status = 'BANNED') <> (NEW.status = 'BANNED')) THEN
    UPDATE board SET author_visible = IF(NEW.is_deleted = 0 AND NEW.status <> 'BANNED', 1, 0)
    WHERE user_idx = NEW.idx;
  END IF;
```

- [ ] **Step 2: 엔티티에 필드 추가**

Modify `Board.java` — `is_deleted` 매핑(84~86라인 근처) 아래에 추가. 기존 `@Builder.Default` 스타일을 따른다:

```java
    // 작성자 보임 여부(미탈퇴 AND status<>BANNED). 트리거가 소유하므로 updatable=false —
    // 글 수정 시 JPA 가 트리거의 값을 되돌리는 lost update 를 막는다.
    @Builder.Default
    @Column(name = "author_visible", updatable = false, nullable = false)
    private Boolean authorVisible = true;
```

- [ ] **Step 3: 앱 기동으로 마이그레이션·validate 검증**

로컬 `petory` DB 대상. 기존 8081 프로세스가 있으면 종료 후:

```bash
pkill -f "server.port=8081" 2>/dev/null; sleep 2
cd /Users/maknkkong/project/Petory
./gradlew bootRun --args='--server.port=8081 --petory.scheduling.enabled=false' > /tmp/boot_v6.log 2>&1 &
until grep -qE "Started PetoryApplication|APPLICATION FAILED" /tmp/boot_v6.log; do sleep 2; done
grep -E "Migrating schema .* to version .6|now at version v6|APPLICATION FAILED|Schema-validation" /tmp/boot_v6.log
```

Expected: `Migrating schema ... to version "6 ..."` + `now at version v6`, `APPLICATION FAILED` 없음(= `ddl-auto=validate` 통과 = author_visible 매핑 정합).

- [ ] **Step 4: 백필 정확성 검증 (현재 데이터는 전원 ACTIVE라 전부 1)**

```bash
DBP=$(grep "spring.datasource.password" backend/main/resources/application.properties | cut -d= -f2)
mysql -uroot -p"$DBP" -B petory -e "
SELECT
 (SELECT COUNT(*) FROM board WHERE author_visible=0) AS hidden,
 (SELECT COUNT(*) FROM board b JOIN users u ON u.idx=b.user_idx
   WHERE u.is_deleted=1 OR u.status='BANNED') AS should_hide;"
```

Expected: 두 값이 **같다**(현재는 둘 다 0). 백필식과 '숨어야 할 글' 정의가 일치함을 확인.

- [ ] **Step 5: 커밋**

```bash
git add backend/main/resources/db/migration/V6__board_author_visible.sql \
        backend/main/java/com/linkup/Petory/domain/board/entity/Board.java
git commit -m "feat(board): author_visible 컬럼·인덱스·동기화 트리거 (Flyway V6)

board 목록 깊은 페이지/자동 COUNT 비용의 근본 원인인 '조인 건너편 작성자 필터'를
board 컬럼으로 내린다. author_visible=(미탈퇴 AND status<>BANNED), 정지는 보임.
users AFTER UPDATE 트리거가 모든 상태변경 경로를 한 곳에서 동기화한다.
엔티티는 updatable=false 로 매핑해 갱신 소유권을 트리거에 둔다.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 2: 시드 — 정지·영구정지·탈퇴 회원 분배 + author_visible 백필

**Files:**
- Modify: `scripts/seed/seed-dev-data.sql` (users INSERT 130~147라인, 그리고 §6 카운터 유도 섹션 끝)

**Interfaces:**
- Consumes: Task 1의 `board.author_visible` 컬럼.
- Produces: 로컬 `petory`에 BANNED·탈퇴 회원과 그들의 board 글(= `author_visible=0` 수백 건), SUSPENDED 회원(= 보임).

- [ ] **Step 1: users INSERT에 상태 분배 추가**

Modify `seed-dev-data.sql`. 컬럼 목록에 `is_deleted` 추가, `status` 하드코딩 `'ACTIVE'`를 CASE로 교체.

컬럼 목록(130~131라인)을:
```sql
INSERT INTO users (id, username, nickname, email, phone, password, role, location,
                   status, warning_count, pet_coin_balance, email_verified, created_at, last_login_at,
                   suspended_until, is_deleted)
```

값 부분에서 `'ACTIVE', 0, 0, 1,`(144라인)과 그 아래 두 줄을 다음으로 교체:
```sql
  -- 상태 분배: BANNED 2%(n%50=23) · SUSPENDED 4%(n%25=11) · 탈퇴 2%(n%50=7) · 나머지 ACTIVE
  CASE WHEN n % 50 = 23 THEN 'BANNED'
       WHEN n % 25 = 11 THEN 'SUSPENDED'
       ELSE 'ACTIVE' END,
  0, 0, 1,
  NOW() - INTERVAL (n % 730) DAY - INTERVAL (n % 1440) MINUTE,
  NOW() - INTERVAL (n % 60) DAY,
  CASE WHEN n % 25 = 11 THEN NOW() + INTERVAL 7 DAY ELSE NULL END,  -- SUSPENDED 만 만료일
  CASE WHEN n % 50 = 7 THEN 1 ELSE 0 END                            -- 탈퇴 2%
```

- [ ] **Step 2: §6 끝에 author_visible 백필 추가**

`seed-dev-data.sql`의 §6(반정규화 카운터 유도) 섹션 맨 끝, `DROP TABLE IF EXISTS seed_numbers;` **직전**에 추가. 시드는 board 를 직접 INSERT 하고 트리거는 users UPDATE 에만 걸리므로, 시드가 스스로 정합을 맞춘다:

```sql
-- author_visible 정합: 시드는 board 를 INSERT 하고 users 를 INSERT(트리거 미발동)하므로
-- 여기서 백필한다. V6 이 이미 컬럼을 만들었으므로 참조 가능하다.
UPDATE board b JOIN users u ON u.idx = b.user_idx
SET b.author_visible = IF(u.is_deleted = 0 AND u.status <> 'BANNED', 1, 0);
```

- [ ] **Step 3: 시드 적용**

```bash
cd /Users/maknkkong/project/Petory
DBP=$(grep "spring.datasource.password" backend/main/resources/application.properties | cut -d= -f2)
mysql -uroot -p"$DBP" petory < scripts/seed/seed-dev-data.sql 2>&1 | grep -iv warning | tail -3
```

Expected: 에러 없이 완료(exit 0).

- [ ] **Step 4: 숨어야 할 글이 실제로 생겼는지 + 정합 검증**

```bash
mysql -uroot -p"$DBP" -B petory -e "
SELECT
 (SELECT COUNT(*) FROM users WHERE status='BANNED') AS banned,
 (SELECT COUNT(*) FROM users WHERE status='SUSPENDED') AS suspended,
 (SELECT COUNT(*) FROM users WHERE is_deleted=1) AS withdrawn,
 (SELECT COUNT(*) FROM board WHERE author_visible=0) AS hidden_posts,
 (SELECT COUNT(*) FROM board b JOIN users u ON u.idx=b.user_idx
   WHERE u.is_deleted=1 OR u.status='BANNED') AS should_hide;"
```

Expected: banned·suspended·withdrawn 각각 수백 명, `hidden_posts > 0`, `hidden_posts == should_hide`(백필 정합).

- [ ] **Step 5: 커밋**

```bash
git add scripts/seed/seed-dev-data.sql
git commit -m "chore(seed): 정지·영구정지·탈퇴 회원 분배 + author_visible 백필

전원 ACTIVE 라 author_visible 필터가 0건을 걸러 측정이 공허했다(missing_pet 빈 테이블과
같은 함정). BANNED 2%·SUSPENDED 4%·탈퇴 2% 를 결정적으로 심어 '숨어야 할 글'을 만든다.
시드는 board 를 INSERT(트리거 미발동)하므로 §6 에서 author_visible 을 백필해 정합을 맞춘다.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 3: 트리거 동기화 회귀 테스트

**Files:**
- Create: `backend/test/java/com/linkup/Petory/domain/board/AuthorVisibleTriggerTest.java`

**Interfaces:**
- Consumes: Task 1의 트리거·컬럼. `UsersRepository`(또는 `SpringDataJpaUsersRepository`)로 상태를 바꾸고 native 로 `author_visible` 을 읽는다.

- [ ] **Step 1: 테스트 작성 (수정 전엔 컴파일만 되고 트리거 없으면 실패)**

Create `AuthorVisibleTriggerTest.java`. `@SpringBootTest` + `petory_test`. 기존 통합 테스트의 `@Autowired EntityManager` 패턴을 따른다:

```java
package com.linkup.Petory.domain.board;

import static org.assertj.core.api.Assertions.assertThat;

import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AuthorVisibleTriggerTest {

    @Autowired EntityManager em;

    private Long anyBoardUserIdx() {
        return ((Number) em.createNativeQuery(
                "SELECT user_idx FROM board WHERE is_deleted=0 LIMIT 1").getSingleResult()).longValue();
    }

    private int visibleFlag(Long userIdx) {
        return ((Number) em.createNativeQuery(
                "SELECT author_visible FROM board WHERE user_idx = :u LIMIT 1")
                .setParameter("u", userIdx).getSingleResult()).intValue();
    }

    private void setStatus(Long userIdx, UserStatus status, boolean deleted) {
        Users u = em.find(Users.class, userIdx);
        u.setStatus(status);
        u.setIsDeleted(deleted);
        em.flush(); // users UPDATE → 트리거 발동
    }

    @Test
    void ban_hides_posts_and_unban_restores() {
        Long u = anyBoardUserIdx();
        setStatus(u, UserStatus.ACTIVE, false);
        assertThat(visibleFlag(u)).isEqualTo(1);

        setStatus(u, UserStatus.BANNED, false);
        assertThat(visibleFlag(u)).as("BANNED → 숨김").isEqualTo(0);

        setStatus(u, UserStatus.ACTIVE, false);
        assertThat(visibleFlag(u)).as("언밴 → 복원").isEqualTo(1);
    }

    @Test
    void withdraw_hides_and_reactivate_restores() {
        Long u = anyBoardUserIdx();
        setStatus(u, UserStatus.ACTIVE, true);
        assertThat(visibleFlag(u)).as("탈퇴 → 숨김").isEqualTo(0);

        setStatus(u, UserStatus.ACTIVE, false);
        assertThat(visibleFlag(u)).as("복구 → 복원").isEqualTo(1);
    }

    @Test
    void suspend_does_not_touch_posts() {
        Long u = anyBoardUserIdx();
        setStatus(u, UserStatus.ACTIVE, false);
        assertThat(visibleFlag(u)).isEqualTo(1);

        setStatus(u, UserStatus.SUSPENDED, false);
        assertThat(visibleFlag(u)).as("정지 → 그대로 보임").isEqualTo(1);
    }
}
```

주의: `Users`의 세터명이 다르면(`setIsDeleted` vs 빌더 전용) 실제 엔티티에 맞춰 조정. 세터가 없으면 native `UPDATE users SET status=?, is_deleted=? WHERE idx=?` 로 대체하고 `em.flush()` 전에 `em.clear()`.

- [ ] **Step 2: 수정 전 상태에서 빨간불 확인 (원칙 6 ①단계)**

트리거가 이미 Task 1에서 들어갔으므로, 대신 **트리거가 없을 때 실패하는지**를 확인한다: `petory_test`에서 트리거를 임시 제거하고 실행.

```bash
cd /Users/maknkkong/project/Petory
mysql -uroot -p"$(grep spring.datasource.password backend/main/resources/application.properties | cut -d= -f2)" petory_test -e "DROP TRIGGER IF EXISTS trg_board_author_visible;" 2>/dev/null
./gradlew cleanTest test --tests "*AuthorVisibleTriggerTest" 2>&1 | tail -15
```

Expected: `ban_hides_posts...`·`withdraw...` **FAILED**(트리거 없으니 author_visible 안 바뀜). `suspend...`는 통과(원래 안 바뀌므로).

- [ ] **Step 3: 트리거 복구 후 초록불 확인**

앱을 재기동하면 Flyway가 `petory_test`에 V6(트리거 포함)를 다시 적용하지 않는다(이미 적용됨). 트리거만 수동 복구:

```bash
mysql -uroot -p"$(grep spring.datasource.password backend/main/resources/application.properties | cut -d= -f2)" petory_test <<'SQL'
CREATE TRIGGER trg_board_author_visible AFTER UPDATE ON users
FOR EACH ROW
  IF (OLD.is_deleted <> NEW.is_deleted)
     OR ((OLD.status = 'BANNED') <> (NEW.status = 'BANNED')) THEN
    UPDATE board SET author_visible = IF(NEW.is_deleted = 0 AND NEW.status <> 'BANNED', 1, 0)
    WHERE user_idx = NEW.idx;
  END IF;
SQL
./gradlew cleanTest test --tests "*AuthorVisibleTriggerTest" 2>&1 | tail -8
```

Expected: 3개 테스트 전부 PASS.

- [ ] **Step 4: 커밋**

```bash
git add backend/test/java/com/linkup/Petory/domain/board/AuthorVisibleTriggerTest.java
git commit -m "test(board): author_visible 동기화 트리거 회귀 테스트

밴→숨김·언밴→복원, 탈퇴→숨김·복구→복원, 정지→그대로 를 검증한다.
트리거 제거 시 밴/탈퇴 케이스가 빨간불이 되는 것을 확인(원칙 6 2단계).

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 4: 목록 쿼리 — 2단계 지연 조인 + 단일 테이블 COUNT

**Files:**
- Modify: `backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaBoardRepository.java` (findBoardListItems 82~95라인 일대)
- Create: `backend/test/java/com/linkup/Petory/domain/board/BoardDeepPageIndexTest.java`

**Interfaces:**
- Consumes: Task 1의 인덱스·컬럼. 기존 `BOARD_LIST_ITEM_SELECT`(75~80라인) 재사용.
- Produces: `findBoardListItems(Pageable)`·`findBoardListItemsByCategory(String, Pageable)`가 지연 조인으로 동작(시그니처 불변, `Page<BoardListItemDTO>` 반환). 어댑터·서비스·컨트롤러 무변경.

- [ ] **Step 1: 인덱스 사용 회귀 테스트 작성 (빨간불)**

Create `BoardDeepPageIndexTest.java`. `IndexUsageRegressionTest` 패턴(EXPLAIN FORMAT=TREE 에 인덱스명이 등장하는지) 그대로:

```java
package com.linkup.Petory.domain.board;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class BoardDeepPageIndexTest {

    @Autowired EntityManager em;

    private String explain(String sql) {
        List<?> rows = em.createNativeQuery("EXPLAIN FORMAT=TREE " + sql).getResultList();
        return rows.toString();
    }

    @Test
    void deep_page_skip_uses_covering_index() {
        String plan = explain(
            "SELECT idx FROM board WHERE is_deleted=0 AND author_visible=1 " +
            "ORDER BY created_at DESC LIMIT 20 OFFSET 40000");
        assertThat(plan)
            .as("깊은 페이지 skip 이 idx_board_visible_created 를 타야 함")
            .contains("idx_board_visible_created");
        assertThat(plan).as("users 조인 없이 board 만").doesNotContain("users");
    }

    @Test
    void visible_count_is_single_table() {
        String plan = explain(
            "SELECT COUNT(*) FROM board WHERE is_deleted=0 AND author_visible=1");
        assertThat(plan).as("COUNT 가 users 를 조인하지 않아야 함").doesNotContain("users");
    }
}
```

- [ ] **Step 2: 빨간불 확인**

```bash
cd /Users/maknkkong/project/Petory
./gradlew cleanTest test --tests "*BoardDeepPageIndexTest" 2>&1 | tail -12
```

Expected: FAIL — 아직 지연조인 쿼리/인덱스를 코드가 안 쓰므로... 인덱스는 Task1에서 이미 존재하니 이 두 테스트는 **native SQL 을 직접 EXPLAIN** 한다. 따라서 인덱스만 있으면 통과할 수 있다. 이 경우 Step 2는 "인덱스 존재 확인"이 되고 곧장 PASS일 수 있음 — 그러면 Step 3~4의 리포지토리 변경은 **동작 동등성**으로 검증(아래 Step 5). (EXPLAIN 테스트는 인덱스 회귀 방지가 목적이므로 PASS 여도 유지한다.)

- [ ] **Step 3: 리포지토리를 2단계 지연 조인으로 변경**

Modify `SpringDataJpaBoardRepository.java`. 기존 `findBoardListItems`·`findBoardListItemsByCategory`의 `@Query` 선언을 지우고, 아래 native ID 쿼리·projection·count·default 메서드로 교체. `BOARD_LIST_ITEM_SELECT` 상수와 import(`PageImpl`)를 사용:

```java
    // 1단계: 커버링 인덱스로 깊은 skip. author_visible 로 걸러 users 조인이 필요없다.
    @Query(value = "SELECT idx FROM board WHERE is_deleted = 0 AND author_visible = 1 "
            + "ORDER BY created_at DESC LIMIT :size OFFSET :offset", nativeQuery = true)
    List<Long> findVisibleBoardIds(@Param("offset") long offset, @Param("size") int size);

    @Query(value = "SELECT idx FROM board WHERE category = :category AND is_deleted = 0 AND author_visible = 1 "
            + "ORDER BY created_at DESC LIMIT :size OFFSET :offset", nativeQuery = true)
    List<Long> findVisibleBoardIdsByCategory(@Param("category") String category,
            @Param("offset") long offset, @Param("size") int size);

    // 2단계: 살아남은 idx 만 projection 조립(작성자 조인 20건). ORDER 로 1단계 순서 재현.
    @Query(BOARD_LIST_ITEM_SELECT + "WHERE b.idx IN :ids ORDER BY b.createdAt DESC")
    List<BoardListItemDTO> findBoardListItemsByIdIn(@Param("ids") List<Long> ids);

    @Query("SELECT COUNT(b) FROM Board b WHERE b.isDeleted = false AND b.authorVisible = true")
    long countVisible();

    @Query("SELECT COUNT(b) FROM Board b WHERE b.category = :category AND b.isDeleted = false AND b.authorVisible = true")
    long countVisibleByCategory(@Param("category") String category);

    @RepositoryMethod("게시글: 전체 목록 페이징 (지연 조인)")
    default Page<BoardListItemDTO> findBoardListItems(Pageable pageable) {
        List<Long> ids = findVisibleBoardIds(pageable.getOffset(), pageable.getPageSize());
        List<BoardListItemDTO> content = ids.isEmpty() ? List.of() : findBoardListItemsByIdIn(ids);
        return new PageImpl<>(content, pageable, countVisible());
    }

    @RepositoryMethod("게시글: 카테고리별 목록 페이징 (지연 조인)")
    default Page<BoardListItemDTO> findBoardListItemsByCategory(String category, Pageable pageable) {
        List<Long> ids = findVisibleBoardIdsByCategory(category, pageable.getOffset(), pageable.getPageSize());
        List<BoardListItemDTO> content = ids.isEmpty() ? List.of() : findBoardListItemsByIdIn(ids);
        return new PageImpl<>(content, pageable, countVisibleByCategory(category));
    }
```

파일 상단 import 에 `org.springframework.data.domain.PageImpl` 추가(없으면).

- [ ] **Step 4: 닉네임 검색 필터를 author_visible 로 통일**

같은 파일 `searchBoardListItemsByNickname`(92~95라인)의 `@Query` 에서 `u.status = 'ACTIVE'` 를 `b.authorVisible = true` 로 바꾼다(닉네임 검색은 users 조인이 필수라 지연조인 안 함):

```java
    @RepositoryMethod("게시글: 작성자 닉네임 검색 페이징 (projection)")
    @Query(BOARD_LIST_ITEM_SELECT
            + "WHERE u.nickname LIKE :nickname% AND b.isDeleted = false AND u.isDeleted = false AND b.authorVisible = true ORDER BY b.createdAt DESC")
    Page<BoardListItemDTO> searchBoardListItemsByNickname(@Param("nickname") String nickname, Pageable pageable);
```

- [ ] **Step 5: 컴파일 + 인덱스 테스트 + 동작 동등성 확인**

```bash
cd /Users/maknkkong/project/Petory
./gradlew compileJava -q && echo "compile OK"
./gradlew cleanTest test --tests "*BoardDeepPageIndexTest" 2>&1 | tail -6
# 앱 재기동 후 실제 목록 API 가 정상 응답하는지
pkill -f "server.port=8081" 2>/dev/null; sleep 2
./gradlew bootRun --args='--server.port=8081 --petory.scheduling.enabled=false' > /tmp/boot_q.log 2>&1 &
until grep -qE "Started PetoryApplication|APPLICATION FAILED" /tmp/boot_q.log; do sleep 2; done
T=$(curl -s -X POST http://localhost:8081/api/auth/login -H 'Content-Type: application/json' -d '{"id":"seed_user_1","password":"Seed1234!"}' | python3 -c "import sys,json;print(json.load(sys.stdin).get('accessToken',''))")
echo "1페이지:"; curl -s -H "Authorization: Bearer $T" "http://localhost:8081/api/boards?page=0&size=20" -o /tmp/p0.json -w "HTTP %{http_code}\n"
python3 -c "import json;d=json.load(open('/tmp/p0.json'));print('건수:', len(d.get('boards') or d.get('content') or []), '| total:', d.get('totalCount') or d.get('totalElements'))"
echo "깊은 페이지(2000):"; curl -s -H "Authorization: Bearer $T" "http://localhost:8081/api/boards?page=2000&size=20" -o /tmp/pd.json -w "HTTP %{http_code} · %{time_total}s\n"
```

Expected: compile OK, 인덱스 테스트 PASS, 1페이지 20건 + total 표시, 깊은 페이지 200 응답. (응답 DTO 키는 `BoardPageResponseDTO` 구조에 맞춰 확인 — `boards`/`totalCount` 등.)

- [ ] **Step 6: 커밋**

```bash
git add backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaBoardRepository.java \
        backend/test/java/com/linkup/Petory/domain/board/BoardDeepPageIndexTest.java
git commit -m "perf(board): 목록을 2단계 지연 조인으로 + COUNT 단일 테이블

깊은 페이지 skip 을 author_visible 커버링 인덱스에서만 처리(1단계 native idx),
살아남은 20건만 작성자 조인(2단계 projection). COUNT 도 users 조인을 떼고 board
단일 테이블로. 어댑터·서비스·컨트롤러·프론트 무변경(번호 페이징 계약 유지).
닉네임 검색은 필터만 author_visible 로 통일.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 5: 측정 — EXPLAIN ANALYZE A/B · COUNT · 너덜너덜 증명 · k6

**Files:**
- Create: `docs/analysis/board-deep-page-2026-07.md` (측정 원본 수치 기록)
- Create: `scripts/k6/board-pagination.js` (k6 시나리오)

**Interfaces:**
- Consumes: Task 4의 목록 API(8081), 인덱스.

- [ ] **Step 1: 깊은 페이지 스캔 A/B (EXPLAIN ANALYZE)**

```bash
cd /Users/maknkkong/project/Petory
DBP=$(grep "spring.datasource.password" backend/main/resources/application.properties | cut -d= -f2)
echo "=== 수정 후 (author_visible 커버링) ==="
mysql -uroot -p"$DBP" petory -e "EXPLAIN ANALYZE SELECT idx FROM board WHERE is_deleted=0 AND author_visible=1 ORDER BY created_at DESC LIMIT 20 OFFSET 49980\G" 2>/dev/null | grep -iE "actual|index|scan" | head
echo "=== A/B: 인덱스 무시 → 스캔 복귀 ==="
mysql -uroot -p"$DBP" petory -e "EXPLAIN ANALYZE SELECT idx FROM board IGNORE INDEX(idx_board_visible_created) WHERE is_deleted=0 AND author_visible=1 ORDER BY created_at DESC LIMIT 20 OFFSET 49980\G" 2>/dev/null | grep -iE "actual|scan" | head
```

기록: 수정 후 시간/스캔행, 인덱스 무시 시 복귀. `docs/analysis/board-deep-page-2026-07.md`에 표로.

- [ ] **Step 2: COUNT A/B**

```bash
echo "=== 수정 후 COUNT (단일 테이블) ==="
mysql -uroot -p"$DBP" petory -e "EXPLAIN ANALYZE SELECT COUNT(*) FROM board WHERE is_deleted=0 AND author_visible=1\G" 2>/dev/null | grep -iE "actual|scan|index" | head
echo "=== 수정 전 COUNT (users 조인) 재현 ==="
mysql -uroot -p"$DBP" petory -e "EXPLAIN ANALYZE SELECT COUNT(*) FROM board b JOIN users u ON u.idx=b.user_idx WHERE b.is_deleted=0 AND u.is_deleted=0 AND u.status='ACTIVE'\G" 2>/dev/null | grep -iE "actual|scan|rows" | head
```

기록: 검사행/시간 전후.

- [ ] **Step 3: 너덜너덜 증명 (비정규화가 왜 필요한가)**

비정규화 없이 board-only skip 을 하면(author_visible 무시하고 is_deleted 만), 숨어야 할 글이 섞여 페이지가 <20건이 되는지 — 실제 응답으로 비교:

```bash
mysql -uroot -p"$DBP" -B petory -e "
-- 순진한 skip(is_deleted 만) 20개 중 '숨어야 할' 작성자 글이 몇 개 섞이나
SELECT COUNT(*) AS should_be_hidden_in_page FROM (
  SELECT b.idx FROM board b
  WHERE b.is_deleted=0 ORDER BY b.created_at DESC LIMIT 20 OFFSET 40000
) k JOIN board b2 ON b2.idx=k.idx JOIN users u ON u.idx=b2.user_idx
WHERE u.is_deleted=1 OR u.status='BANNED';"
```

기록: >0 이면 "순진한 지연조인은 그 수만큼 페이지가 비게 된다 → 비정규화 필요"의 증거.

- [ ] **Step 4: k6 시나리오 작성 + 실행**

Create `scripts/k6/board-pagination.js`:

```javascript
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE || 'http://localhost:8081';
const TOKEN = __ENV.TOKEN;

export const options = { vus: 20, duration: '30s' };

export default function () {
  const params = { headers: { Authorization: `Bearer ${TOKEN}` } };
  // 얕은 / 깊은 / 맨뒤 를 섞어 친다
  const pages = [0, 1000, 2000, 2499];
  const p = pages[Math.floor(Math.random() * pages.length)];
  const res = http.get(`${BASE}/api/boards?page=${p}&size=20`, params);
  check(res, { '200': (r) => r.status === 200 });
}
```

실행(k6 설치돼 있으면):
```bash
T=$(curl -s -X POST http://localhost:8081/api/auth/login -H 'Content-Type: application/json' -d '{"id":"seed_user_1","password":"Seed1234!"}' | python3 -c "import sys,json;print(json.load(sys.stdin).get('accessToken',''))")
k6 run -e TOKEN="$T" scripts/k6/board-pagination.js 2>&1 | grep -iE "http_req_duration|p\(95\)|http_reqs" | head
```

기록: p95. (k6 미설치면 문서에 "미측정" 명시하고 EXPLAIN/curl 수치로 대체.)

- [ ] **Step 5: 측정 문서 작성 + 커밋**

`docs/analysis/board-deep-page-2026-07.md` 에 §1~§5 측정 결과를 `query-audit/fixes-*.md` 형식으로 정리(전/후 표, A/B, 남은 것). 그리고:

```bash
git add docs/analysis/board-deep-page-2026-07.md scripts/k6/board-pagination.js
git commit -m "docs(analysis): board 깊은 페이지 지연조인 전후 실측 (EXPLAIN·COUNT·k6)

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

### Task 6: 포트폴리오 페이지 (별도 레포 makkong1-github.io)

**Files:**
- Create: `/Users/maknkkong/project/makkong1-github.io/src/pages/projects/petory/domains/DeepPagePaginationDetail.jsx`
- Modify: `/Users/maknkkong/project/makkong1-github.io/src/App.jsx` (라우트 추가)
- Modify: `/Users/maknkkong/project/makkong1-github.io/src/pages/projects/petory/PetoryRefactoringPage.jsx` (09번 케이스 링크)

**Interfaces:**
- Consumes: Task 5의 실측 수치(정본). `OverFetchingDetail.jsx` 를 구조 템플릿으로.

- [ ] **Step 1: OverFetchingDetail 구조 참고**

```bash
sed -n '1,60p' /Users/maknkkong/project/makkong1-github.io/src/pages/projects/petory/domains/OverFetchingDetail.jsx
grep -n "over-fetching" /Users/maknkkong/project/makkong1-github.io/src/App.jsx
```

- [ ] **Step 2: DeepPagePaginationDetail.jsx 작성 (상황→포인트→해결)**

`OverFetchingDetail` 의 레이아웃(래퍼·카드·TableOfContents·MermaidDiagram)을 그대로 따르고, 섹션을 **상황 → 왜 느린가(OFFSET O(offset)) → 대안 3개 검토(키셋/지연조인/무시) → 공유 컴포넌트 발견과 일관성 판단 → author_visible 함정과 트리거 → 실측 전후**로 구성. 수치는 Task 5 정본만 사용. (구체 문안은 실측 후 확정하되, 구조·톤은 기존 detail 페이지와 동일하게.)

- [ ] **Step 3: 라우트 + 09번 케이스 링크**

`App.jsx` 에 기존 `over-fetching` 라우트 옆에 `/domains/refactoring/deep-page` 추가, `PetoryRefactoringPage.jsx` 케이스 배열에 09번 항목(상황/문제/해결 요약 + detail 링크) 추가. 기존 08번 케이스 객체 형태를 그대로 따른다.

- [ ] **Step 4: 빌드 검증**

```bash
cd /Users/maknkkong/project/makkong1-github.io
npx eslint src/pages/projects/petory/ 2>&1 | grep -v baseline | tail -3
npm run build 2>&1 | grep -E "✓ built|error" | head
```

Expected: eslint 통과, 빌드 성공.

- [ ] **Step 5: 커밋 (푸시는 사용자 확인 후)**

```bash
cd /Users/maknkkong/project/makkong1-github.io
git add src/pages/projects/petory/domains/DeepPagePaginationDetail.jsx src/App.jsx src/pages/projects/petory/PetoryRefactoringPage.jsx
git commit -m "feat(petory): board 깊은 페이지 페이징 판단 케이스 페이지 (09)

키셋 검토 → 공유 PageNavigation 발견 → 일관성 판단 → 지연조인+author_visible 비정규화
까지의 판단 여정과 실측 전후를 상황→포인트→해결 구조로 정리.

Co-Authored-By: Claude Opus 4.8 <noreply@anthropic.com>"
```

---

## Self-Review

**Spec coverage (스펙 §별 → 태스크 매핑):**
- §3.3 스키마(컬럼·백필·인덱스 2개·트리거) → Task 1 ✅
- §3.4 엔티티 매핑(updatable=false) → Task 1 ✅
- §3.2 트리거 동기화 + 검증 → Task 1(생성) + Task 3(테스트) ✅
- §3.5 2단계 쿼리·COUNT·닉네임 검색 → Task 4 ✅
- §4 시드 분배 + 측정(EXPLAIN/COUNT/너덜너덜/k6) → Task 2(시드) + Task 5(측정) ✅
- §6 문서화(증거 문서 + 포폴 페이지 + 다른 도메인 몇 줄) → Task 5(증거) + Task 6(포폴). 다른 도메인 판단은 증거 문서 "남은 것/판단" 절에 몇 줄로 포함.
- §3.1 동작 변화(정지 보임) → Task 4 쿼리 필터가 반영, 문서에 명시.

**Placeholder scan:** Task 6 Step 2의 상세 문안을 "실측 후 확정"으로 둔 것은 포트폴리오 산문이라 수치 확정 후 작성이 불가피(구조·톤은 기존 페이지로 고정). 그 외 코드 스텝은 전부 실제 코드 포함. 통과.

**Type consistency:** `findVisibleBoardIds(long offset, int size)`·`findBoardListItemsByIdIn(List<Long>)`·`countVisible()`·`authorVisible:Boolean`·트리거명 `trg_board_author_visible`·인덱스명 `idx_board_visible_created`/`idx_board_cat_visible_created` — Task 1·3·4·5에서 동일하게 사용. 통과.

**주의(구현자 확인 필요):** ① `Users` 세터명(`setStatus`/`setIsDeleted`) 실제 존재 여부 — 없으면 native UPDATE 로 대체(Task 3 Step 1 주석). ② 목록 API 응답 DTO 키(`BoardPageResponseDTO`의 `boards`/`totalCount`) — Task 4 Step 5에서 실제 키로 확인. ③ k6 미설치 시 문서에 명시하고 EXPLAIN/curl 로 대체(Task 5 Step 4).
