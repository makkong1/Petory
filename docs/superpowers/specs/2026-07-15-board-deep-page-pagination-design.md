# board 깊은 페이지 페이징 — 지연 조인 + author_visible 비정규화 (설계)

- 날짜: 2026-07-15
- 범위: `board` 목록 페이징의 깊은 페이지 비용 + 그에 딸린 자동 COUNT 비용
- 상태: 설계 확정 (구현 전)

---

## 1. 문제

`board` 목록은 OFFSET 페이징이다. 깊은 페이지로 갈수록 비용이 **O(offset)**으로 선형 증가한다.
실측(로컬 `petory`, board 50,000행):

| OFFSET (페이지) | 시간 |
|---|---|
| 0 (1페이지) | 1.5ms |
| 10,000 (500페이지) | 51ms |
| 25,000 | 68ms |
| 40,000 | 87ms |
| 49,980 (마지막) | 114~147ms |

원인 두 겹:
1. **OFFSET은 "건너뛰기"가 아니라 "만들고 버리기"다.** 49,980번째를 알려면 앞의 49,980개를 정렬 순서대로 실제로 만들어야 한다. `Limit/Offset: 20/49980 ... rows=50000` → 5만 개를 만들어 20개만 남기고 버린다.
2. **필터가 조인 건너편에 있다.** 목록은 `board JOIN users` 후 `u.is_deleted=0 AND u.status='ACTIVE'`로 거른다. 어떤 board 행이 페이지에 낄 자격이 있는지가 users를 조인해봐야 정해지므로, board 인덱스만으론 offset을 셀 수 없고 **5만 행 × users PK 조회**가 강제된다.

곁가지: 같은 이유로 **자동 COUNT도 users를 조인**해 매 호출 60,001행을 검사한다(목록 SELECT와 형제 문제).

트리거: 프론트의 공유 `PageNavigation` 컴포넌트에 **"맨 뒤" 버튼(`showEdges`)**이 있어, 마지막 페이지(최대 offset)에 사용자가 실제로 도달할 수 있다.

## 2. 왜 이 방식인가 (대안 검토 결론)

세 대안을 검토했다.

- **키셋 페이징** — 성능은 최고(O(1))이고 COUNT도 없어지지만, 앱 전체 페이징이 **단일 공유 컴포넌트 `Common/PageNavigation.js`(12개 화면)** 로 통일돼 있고 이 컴포넌트는 총건수·페이지 점프·맨뒤에 의존한다. 키셋은 이 컴포넌트에 원리적으로 못 얹혀서, board만 무한스크롤로 빠지면 **12화면 중 유일하게 다른 페이징 패러다임**이 된다. 앱의 페이징 정체성을 board 하나 때문에 쪼개는 비용이 크다 → 기각.
- **아무것도 안 함** — 비싼 구간(수백~수천 페이지)이 사람이 실제로 안 가는 구간과 겹친다. 단, "맨 뒤" 버튼이 유일한 실사용 트리거다. 후보였으나, 아래 지연 조인이 **COUNT까지 함께 고치는** 이점이 커서 채택하지 않음.
- **지연 조인 + author_visible 비정규화** — 채택. 번호 UI(공유 컴포넌트)를 그대로 두면서 깊은 페이지 스캔을 완화하고, 비정규화 덕에 **COUNT도 단일 테이블로 싸진다.**

핵심 통찰: 느림의 근본 원인이 "조인 건너편 필터"다. 그 판정(`미탈퇴 AND 미영구정지`)을 board 컬럼으로 내리면 — ① 지연 조인 서브쿼리가 커버링이 되고, ② 페이지가 너덜너덜해지지 않고, ③ COUNT가 board 단일 인덱스 카운트가 된다. **하나의 비정규화로 셋이 풀린다.**

## 3. 설계 결정

### 3.1 author_visible 의미 (제품 결정)

`author_visible = (user.is_deleted = 0 AND user.status <> 'BANNED')`

- **영구 상태만 숨긴다**: 탈퇴(is_deleted) + 영구정지(BANNED). **정지(SUSPENDED)는 보인다.**
- 근거: 정지는 일시적이고, care 도메인이 이미 "SUSPENDED는 상태 변경 없이 읽기필터로, BANNED만 행 변경"(`UserSanctionCareEventListener`)으로 취급하는 철학과 방향이 맞다. 영구 이벤트에만 동기화가 걸려 **일시정지↔복구 churn과 write 증폭이 사라진다.**
- **동작 변화(의도됨)**: 기존엔 정지 회원 글도 목록에서 숨었으나, 이후 **정지 회원 글은 목록에 보인다.** 탈퇴·영구정지만 숨는다.

### 3.2 동기화 — DB 트리거 (결정: 접근 A)

회원 상태를 바꾸는 경로가 여럿이다: 관리자 `PATCH /users/{id}/status`(밴/언밴), 제재 서비스, 탈퇴(`UsersService.deleteUser → softDelete`), 로그인 재활성화(`AuthService.confirmReactivate`). **모두 결국 `users` 행을 UPDATE 한다.**

→ `users AFTER UPDATE` 트리거 하나가 모든 경로를 잡는다. 이벤트 리스너(경로마다 배선, 빠뜨리기 쉬움)보다 "못 지나침"이 값지다. 이 저장소는 이미 `geo_point` 비정규화를 트리거로 한다(V4·V5) → 패턴 일관.

```sql
CREATE TRIGGER trg_board_author_visible AFTER UPDATE ON users
FOR EACH ROW
  IF (OLD.is_deleted <> NEW.is_deleted)
     OR ((OLD.status = 'BANNED') <> (NEW.status = 'BANNED')) THEN
    UPDATE board SET author_visible = IF(NEW.is_deleted = 0 AND NEW.status <> 'BANNED', 1, 0)
    WHERE user_idx = NEW.idx;
  END IF;
```

- **`is_deleted` 또는 BANNED 경계가 바뀔 때만** 발동 → 로그인의 `last_login_at` 갱신 같은 흔한 UPDATE엔 안 걸린다. SUSPENDED↔ACTIVE는 BANNED 경계가 안 바뀌므로 board를 안 건드린다(정지 회원 글 계속 보임).
- 팬아웃은 사용자당 평균 ~5글, 영구 이벤트라 드묾.

### 3.3 스키마 (Flyway V6)

```sql
ALTER TABLE board ADD COLUMN author_visible TINYINT(1) NOT NULL DEFAULT 1;
-- 새 글은 항상 보임: 밴/탈퇴 회원은 생성 경로에서 차단되므로 기본값이 정확.

UPDATE board b JOIN users u ON u.idx = b.user_idx
SET b.author_visible = IF(u.is_deleted = 0 AND u.status <> 'BANNED', 1, 0);

-- 전체 목록 + COUNT 커버링
ALTER TABLE board ADD INDEX idx_board_visible_created (is_deleted, author_visible, created_at DESC);
-- 카테고리 목록 커버링
ALTER TABLE board ADD INDEX idx_board_cat_visible_created (category, is_deleted, author_visible, created_at DESC);

-- 위 트리거
```

### 3.4 엔티티 매핑

```java
@Column(name = "author_visible", updatable = false)
private boolean authorVisible = true;
```

- `updatable = false` — JPA는 INSERT 때만 true를 쓰고 **UPDATE는 안 한다.** 글 수정 시 트리거가 바꿔둔 값을 되돌리는 lost update를 막는다. 갱신 소유권은 트리거 하나.

### 3.5 쿼리 — 2단계 (missing_pet·meetup과 동일 패턴)

`SpringDataJpaBoardRepository`의 목록 projection 메서드를 `@Query` → `default` 조립으로 바꾼다. **어댑터·도메인 인터페이스·서비스·컨트롤러·프론트 전부 무변경**(시그니처 유지).

```
1단계 (native, 깊은 skip):
  SELECT idx FROM board
  WHERE is_deleted = 0 AND author_visible = 1
  ORDER BY created_at DESC LIMIT :size OFFSET :offset
  → idx_board_visible_created 커버링. users 조인 없음.

2단계 (JPQL projection, 20건만):
  SELECT new BoardListItemDTO(...) FROM Board b JOIN b.user u
  WHERE b.idx IN :ids ORDER BY b.createdAt DESC
  → 살아남은 20개만 작성자 조인. 재필터 불필요(1단계에서 이미 걸림).

COUNT (단일 테이블):
  SELECT COUNT(*) FROM board WHERE is_deleted = 0 AND author_visible = 1
  → users 조인 사라짐.

default 메서드가 셋을 조립해 PageImpl 반환.
```

적용 대상:
- `findBoardListItems` (전체) → 2단계 지연 조인.
- `findBoardListItemsByCategory` (카테고리) → 2단계 지연 조인 (+ `idx_board_cat_visible_created`).
- `searchBoardListItemsByNickname` (닉네임 검색) → **지연 조인 안 함** (닉네임으로 users 조인 필수, 결과가 작아 깊은 offset 없음). 단일 쿼리 유지, 필터만 `u.is_deleted=0 AND u.status='ACTIVE'` → `b.author_visible = true`로 통일.

읽기 필터 통일: 세 쿼리 모두 "보임" 판정을 `author_visible = true`(board 컬럼)로 바꾼다.

## 4. 측정 계획 (구현 후, 이 저장소의 방법론 그대로)

전제: **시드에 정지·영구정지·탈퇴 회원을 심어야** `author_visible` 필터가 실제로 행을 걸러 측정이 유의미하다. (0건이면 missing_pet 빈 테이블과 같은 함정.)
- `seed-dev-data.sql` users 생성에 상태 분배 추가: 약 3% SUSPENDED(보임), 2% BANNED·2% 탈퇴(숨김). `n % 50` 류 결정적 분배.

측정 항목:

| 항목 | 수정 전 | 수정 후 (기대) | 방법 |
|---|---|---|---|
| 깊은 페이지 스캔 | 5만 조인 · 114~147ms | 커버링 skip · 수십 ms | EXPLAIN ANALYZE |
| COUNT | users 조인 60,001행 | board 단일 인덱스 카운트 | EXPLAIN ANALYZE |
| 너덜너덜 증명 | 순진한 지연조인(비정규화 없이) → 페이지 <20건 | 비정규화 → 항상 20건 | 실제 응답 건수 비교 |
| 종단 p95 | 얕은 / 깊은 / **맨뒤 버튼** | 전후 비교 | k6 |

- A/B(인과 확정): `idx_board_visible_created` 제거 → 스캔 복귀 → 재적용 → 사라짐.
- 전부 `curl` 실호출 + `performance_schema` 관측.
- 회귀 테스트: 기존 `IndexUsageRegressionTest`/`QueryCountScalingRegressionTest` 계열에 board 지연조인 케이스 추가 검토 (원칙 6: 수정 전 빨간불 먼저 확인).

## 5. 다른 도메인 적용 판단 (문서엔 몇 줄만)

지연 조인+비정규화가 의미 있으려면 **"큰 테이블(깊은 offset) + 조인 건너편 작성자 필터"** 두 조건이 필요하다.
- **board(5만)** — 둘 다 충족. 유일한 실익. → 적용.
- **missing_pet·meetup·care(3~5천)** — 필터는 있으나 끝까지 가도 수천 행이라 몇 ms. → 보류(현 규모 실익 없음).
- **관리자 목록** — 번호·총계·점프가 기능 요구라 그대로. 필터 우선이라 깊은 offset 자체가 드묾. → 유지.
- **chat** — 방별 파티션(평균 ~5건). → 해당 없음.

## 6. 문서화

1. **증거 문서** → Petory `docs/analysis/`에 새 파일. `query-audit/fixes-*.md`와 같은 형식·톤. EXPLAIN·COUNT·k6 수치 정본.
2. **포트폴리오 페이지** → 상황 → 포인트 → 해결 구조의 전용 detail 페이지(`OverFetchingDetail`처럼 독립). `PetoryRefactoringPage`에 09번 케이스로 링크. 콘텐츠 = 이 설계의 판단 여정(키셋 검토 → 공유 컴포넌트 발견 → 일관성 판단 → 지연조인+비정규화 → 함정과 트리거).
3. **다른 도메인 판단** → §5를 몇 줄로.

## 7. 범위 밖 (명시)

- 키셋 페이징 전환(앱 전체 페이징 정체성 변경) — 하지 않음.
- 죽은 엔티티 Page 오버로드 3개(`findAllByIsDeletedFalse...` 등, 서비스 호출 0) — 손대지 않음.
- 단건 조회·관리자 board 쿼리 — 별도 규칙, 범위 밖.
- 다른 도메인 지연 조인 적용 — 보류(§5).

## 8. 리스크

- **트리거 팬아웃**: 글 많은 회원의 상태 변경 시 board 다수 행 UPDATE. 평균 ~5글·영구 이벤트라 작지만, 극단 사용자 존재 시 상태변경 트랜잭션이 길어질 수 있음.
- **동기화 드리프트**: 트리거가 유일한 갱신 소유자이므로, 트리거를 우회하는 직접 SQL/벌크 업데이트가 있으면 어긋남. 현재 그런 경로는 없음.
- **동작 변화 노출**: 정지 회원 글이 이제 목록에 보임 — 의도된 제품 결정이나, 관계자에게 알릴 것.
- **엔티티/스키마 정합**: `author_visible`는 매핑(`updatable=false`)하므로 `ddl-auto=validate` 통과. 트리거는 검증 대상 아님.
