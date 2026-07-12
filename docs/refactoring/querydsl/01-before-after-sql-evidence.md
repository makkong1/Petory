---
date: 2026-07-11
domains: [user]
type: sql-evidence
problem: dynamic-query-antipattern
status: verified
metric: "':param IS NULL OR' 안티패턴 제거 확인(SQL 로그). 성능 개선은 modest — LIKE/CAST가 실행계획 지배"
---

# 관리자 유저 검색 QueryDSL 전환 — before/after SQL 증거

> 작성일: 2026-07-11
> 관련: [`00-admin-user-search-querydsl-decision.md`](./00-admin-user-search-querydsl-decision.md)
> 출처: `AdminUserFacadeGetUsersTest` 실행 시 Hibernate `show-sql` 로그 (실제 MySQL 통합 테스트)

---

## 핵심: `:param IS NULL OR` 안티패턴 제거의 실증

전환의 목표는 "파라미터가 null이면 그 조건이 SQL WHERE 절에서 **통째로 사라지는가**"였다. 실제 로그로 확인됨.

### Before (JPQL, `SpringDataJpaUsersRepository`)

파라미터 값과 무관하게 **항상 3조건이 모두 SQL에 남았다**:

```sql
select ... from users u1_0
where (? is null or cast(u1_0.role as char) = ?)
  and (? is null or cast(u1_0.status as char) = ?)
  and (? is null or u1_0.username like ? or u1_0.nickname like ? or u1_0.email like ?)
order by u1_0.created_at desc
limit ?, ?
```

role/status가 null이어도 `(? is null or ...)` 분기가 그대로 실행계획에 포함된다.

### After (QueryDSL, `JpaUsersAdapter.adminUserFilter`)

**role만 지정(status=null)** — status 조건이 WHERE에서 사라짐:

```sql
select u1_0.idx, u1_0.id, u1_0.nickname, u1_0.username, u1_0.email, cast(u1_0.role as char),
       u1_0.is_deleted, u1_0.is_dormant, u1_0.created_at, cast(u1_0.status as char),
       u1_0.warning_count, u1_0.suspended_until
from users u1_0
where cast(u1_0.role as char) = ?
  and (u1_0.username like ? escape '!' or u1_0.nickname like ? escape '!' or u1_0.email like ? escape '!')
order by u1_0.created_at desc
limit ?, ?
```

**status만 지정(role=null)** — role 조건이 WHERE에서 사라짐:

```sql
... where cast(u1_0.status as char) = ?
      and (u1_0.username like ? escape '!' or u1_0.nickname like ? escape '!' or u1_0.email like ? escape '!')
order by u1_0.created_at desc limit ?, ?
```

→ null 파라미터의 조건이 실행계획에서 완전히 제거됨. 목표 달성.

## 부수 효과 2가지

1. **LIKE 와일드카드 이스케이프** — QueryDSL `.contains()`가 `like ? escape '!'`를 생성한다. 기존 JPQL `%:keyword%`는 사용자 입력의 `%`, `_`를 이스케이프하지 않았다. 검색어에 `%`가 들어와도 리터럴로 처리되는 미세한 견고성 개선.
2. **WHERE 절 공유** — `findAllForAdmin`(엔티티)과 `findAdminUserListItems`(projection)가 `adminUserFilter()` predicate 하나를 공유. 필터 규칙이 한 곳에만 존재.

## 정직한 한계 (00 문서 §3과 동일)

- `cast(... as char)`는 여전히 남아 있다. role/status는 `@Enumerated(EnumType.STRING)`이라, 기존 동작(존재하지 않는 role 문자열 → 빈 결과, `AdminUserFacadeGetUsersTest`의 경계 테스트로 검증됨)을 **정확히 보존**하기 위해 `stringValue().eq()`를 유지했다. `Role.valueOf()`로 바꾸면 cast는 없앨 수 있으나 잘못된 값 입력 시 예외가 나 기존 계약이 깨진다 → 채택 안 함.
- `LIKE %kw%` 선행 와일드카드는 그대로라 이 쿼리의 인덱스 사용성은 근본적으로 개선되지 않는다. 이 작업의 가치는 성능이 아니라 **안티패턴 제거 + WHERE 공유 + QueryDSL 실사용**임을 재확인.

## 검증

- `./gradlew test --tests AdminUserFacadeGetUsersTest` → 6개 전부 PASSED (role/status/keyword 필터, 전체조회, 존재하지 않는 role→빈결과 경계).
- `./gradlew test` (전체) → BUILD SUCCESSFUL. 신규 `JPAQueryFactory` 빈이 전체 `@SpringBootTest` 컨텍스트에서 정상 로드.
