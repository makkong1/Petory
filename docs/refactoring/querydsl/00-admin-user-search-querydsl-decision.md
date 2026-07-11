# 관리자 유저 검색 동적 쿼리 개선 — 방식 결정 문서 (QueryDSL)

> 작성일: 2026-07-11
> 기준 브랜치: dev
> 상태: **구현 완료 (2026-07-11)** — QueryDSL 도입, 테스트 통과
> 관련 문서: [`docs/analysis/job-fit-gap-analysis-2026-07.md`](../../analysis/job-fit-gap-analysis-2026-07.md) · before/after SQL 증거: [`01-before-after-sql-evidence.md`](./01-before-after-sql-evidence.md)

---

## 1. 왜 지금 이걸 하게 됐나 (배경)

이 작업은 코드베이스가 **기능적으로 요구해서**가 아니라, **채용공고 대비 갭 분석**에서 출발했다.

1. 백엔드 채용공고(집품 플랫폼, Spring/Java)가 자격요건에 **"Database ORM (JPA/Hibernate/Querydsl 등) 관련 경험"**, 우대사항에 **"RDBMS 실행계획을 통한 인덱스 튜닝 / 쿼리 튜닝 경험"**을 명시.
2. `job-fit-gap-analysis-2026-07.md`에서 QueryDSL이 🔴 높음 우선순위로 분류됨 — 현재 `build.gradle`에 의존성 자체가 없고 JPQL/native `@Query`만 사용 중이기 때문.
3. "정말 도입할 이유가 있는가"를 재점검하는 과정에서 코드를 훑던 중, **관리자 유저 검색 쿼리에서 실제 안티패턴을 발견** → 억지 도입이 아니라 실재하는 개선 대상이 있음을 확인.

즉 이 문서의 목적은 "QueryDSL을 넣기로 했으니 정당화하기"가 아니라, **실제 문제가 있는지, 있다면 QueryDSL이 다른 선택지 대비 나은지**를 먼저 판단하는 것이다.

## 2. 발견한 문제

**파일**: `backend/main/java/com/linkup/Petory/domain/user/repository/SpringDataJpaUsersRepository.java`

```java
// 관리자 필터 페이징 조회 (findAllForAdmin)
@Query("SELECT u FROM Users u WHERE "
    + "(:role IS NULL OR CAST(u.role AS string) = :role) AND "
    + "(:status IS NULL OR CAST(u.status AS string) = :status) AND "
    + "(:keyword IS NULL OR u.username LIKE %:keyword% OR u.nickname LIKE %:keyword% OR u.email LIKE %:keyword%) "
    + "ORDER BY u.createdAt DESC")
Page<Users> findAllForAdmin(...);

// 관리자 목록 projection 조회 (findAdminUserListItems) — WHERE 절이 위와 완전히 동일
@Query("SELECT new ...AdminUserListDTO(...) FROM Users u WHERE "
    + "(:role IS NULL OR CAST(u.role AS string) = :role) AND "
    + "(:status IS NULL OR CAST(u.status AS string) = :status) AND "
    + "(:keyword IS NULL OR u.username LIKE %:keyword% OR ...) "
    + "ORDER BY u.createdAt DESC")
Page<AdminUserListDTO> findAdminUserListItems(...);
```

### 문제점 2가지

**(1) `:param IS NULL OR ...` 동적 쿼리 안티패턴**
파라미터가 null이어도 조건이 SQL WHERE 절에 그대로 남는다. 옵티마이저는 "null이면 전체, 아니면 필터"를 **하나의 실행계획**으로 처리해야 하므로, 인덱스가 있어도 활용하지 못하고 스캔형 계획을 택하기 쉽다. 이것이 QueryDSL/Specification 같은 동적 쿼리 도구가 존재하는 근본 이유다.

**(2) WHERE 절 중복**
동일한 3조건 WHERE + ORDER BY가 `findAllForAdmin`과 `findAdminUserListItems` 두 메서드에 복붙되어 있다. 한쪽 필터 규칙이 바뀌면 두 곳을 모두 고쳐야 한다(변경 누락 위험).

## 3. ⚠️ 정직한 전제 — 이 쿼리의 성능 이득은 "modest"다

과대포장을 막기 위해 명시한다. 이 **특정 쿼리**는 QueryDSL로 바꿔도 실행계획이 극적으로 좋아지지 않는다:

- `CAST(u.role AS string) = :role` — 컬럼에 함수/캐스팅을 씌우면 해당 컬럼 인덱스를 못 탄다.
- `LIKE %:keyword%` — 선행 와일드카드(`%`)라 어떤 경우에도 인덱스 사용 불가.
- `role`, `status`는 enum → 저카디널리티라 단독 인덱스의 선택도가 낮다.

따라서 이 작업의 **진짜 가치는 런타임 성능이 아니다.** 아래 3가지다:

1. **공고에 명시된 QueryDSL 실사용 경험 확보** (이력서 키워드).
2. **`:param IS NULL OR` 안티패턴을 "언제 문제가 되는지"(고카디널리티 인덱스 컬럼일 때) 설명할 수 있는 면접 talking point.** — 이 쿼리 자체는 LIKE/CAST에 지배되지만, 패턴의 원리를 이해하고 있음을 보여주는 소재.
3. **중복 WHERE 절 제거** (predicate를 한 번 정의해 두 메서드가 공유).

> 면접에서 이걸 "성능 X배 개선"으로 말하면 역풍. "동적 쿼리 안티패턴을 인지하고 QueryDSL로 정리했으며, 이 쿼리는 LIKE/CAST 때문에 이득이 제한적이지만 패턴 자체는 고카디널리티 컬럼에서 유효하다"까지 말할 수 있어야 진짜 실력으로 읽힌다.

## 4. 선택지 비교

| 기준                                   | A. QueryDSL 도입                                                            | B. Specification으로 통일                                      | C. 그냥 두기   |
| -------------------------------------- | --------------------------------------------------------------------------- | -------------------------------------------------------------- | -------------- |
| `:param IS NULL OR` 안티패턴 해결      | ✅ (null이면 조건 자체 제거)                                                | ✅ (null이면 조건 제거)                                        | ❌             |
| WHERE 절 중복 제거                     | ✅ (BooleanBuilder predicate 공유)                                          | ✅ (Specification 조합)                                        | ❌             |
| 공고 "QueryDSL" 키워드 확보            | ✅                                                                          | ❌                                                             | ❌             |
| 기존 코드베이스 일관성                 | ⚠️ 동적쿼리 방식 3개로 분기<br>(JPQL null-check / Specification / QueryDSL) | ✅ board 도메인의 `BoardPopularitySnapshotSpecs` 스타일과 일치 | ✅ 변경 없음   |
| 빌드 복잡도                            | ⚠️ annotation processor + Q클래스 생성 설정 추가                            | ✅ 신규 의존성 0                                               | ✅ 0           |
| 타입 안정성/가독성                     | ✅ 컴파일 타임 체크, 메서드 체이닝                                          | △ Criteria API는 다소 장황                                     | ❌ 문자열 쿼리 |
| CLAUDE.md 단순함·외과적 변경 원칙 부합 | ⚠️ 새 추상화 도입                                                           | ✅ 기존 패턴 재사용                                            | ✅             |

### 각 선택지 요약

**A. QueryDSL** — 공고가 이름을 콕 집어 요구하므로 취업 목적에는 가장 강한 카드. 단, 이 프로젝트는 이미 board 도메인에서 Specification으로 동적 쿼리를 처리 중이라, QueryDSL을 넣으면 동적 쿼리 방식이 3가지로 갈라진다. 순수 "코드베이스 건강" 관점에서는 일관성 손해.

**B. Specification 통일** — 같은 안티패턴을 고치면서 `BoardPopularitySnapshotSpecs`와 동일한 스타일을 써서 CLAUDE.md의 "기존 스타일에 맞춤·단순함" 원칙에 완벽히 부합. 신규 의존성/빌드 변경 0. 하지만 이력서에 "QueryDSL" 한 줄은 안 생긴다.

**C. 그냥 두기** — 저번 결론. 코드베이스 자체의 필요도는 낮고(Specification이 이미 있음), 이 쿼리의 성능 이득도 modest라 "진짜 안 해도 되는" 것도 사실. QueryDSL 요건은 다른 작업(별도 토이 프로젝트 등)으로 채우는 선택.

## 5. 결정

**목적이 "코드베이스 개선"이 아니라 "채용 대비"라는 점**이 저울을 가른다.

- 코드베이스 순수 관점만 보면 → **B(Specification)**가 정답. 일관성·단순함 모두 우위.
- 채용 대비 관점(이 문서의 실제 목적) → **A(QueryDSL)**. 공고가 이름을 명시했고, 안티패턴을 실제로 고치는 "가짜가 아닌" 실습이 되며, 면접 talking point가 구체적으로 남는다.

→ **결정: A. QueryDSL 도입.** 단, 아래 조건을 지킨다.

1. **범위 최소화** — 관리자 유저 검색 2개 메서드(`findAllForAdmin`, `findAdminUserListItems`)만 대상. 나머지 정적 `@Query` 29곳은 건드리지 않는다(외과적 변경).
2. **정직한 포지셔닝** — 성능 개선이 아니라 "동적 쿼리 안티패턴 정리 + QueryDSL 학습"으로 기록/설명한다.
3. **일관성 손해 명시** — Specification과 병존하게 되는 점을 인지하고, "왜 board는 Specification, 여기는 QueryDSL인가"라는 면접 질문에 답할 수 있도록 이 문서를 근거로 남긴다. (답: 기존 board는 이미 Specification으로 동작 중이라 유지, 신규 학습·검증 목적으로 admin에 QueryDSL 도입)

## 6. 구현 결과 (완료)

포트/어댑터 구조를 반영해, 애초 계획의 `UsersRepositoryCustom`/`Impl` 대신 **어댑터(`JpaUsersAdapter`)에 QueryDSL을 직접** 넣었다("어댑터 = JPA 기술 구현체"라는 이 프로젝트 철학에 부합, Spring Data custom fragment 의식 절차 회피).

1. ✅ `build.gradle` — `querydsl-jpa:5.1.0:jakarta` + apt 프로세서 추가. `compileJava` 통과(Q클래스 생성 확인).
2. ✅ `global/config/QuerydslConfig` — `JPAQueryFactory` 빈 등록.
3. ✅ `JpaUsersAdapter` — `findAllForAdmin`/`findAdminUserListItems`를 QueryDSL로 구현, `adminUserFilter()` predicate 공유. 위임하던 두 메서드를 대체.
4. ✅ `SpringDataJpaUsersRepository` — JPQL 두 메서드 제거, 불필요해진 import(`Page`, `AdminUserListDTO`) 정리.
5. ✅ 검증 — `AdminUserFacadeGetUsersTest` 6개 통과 + 전체 `./gradlew test` BUILD SUCCESSFUL. before/after SQL은 [`01-before-after-sql-evidence.md`](./01-before-after-sql-evidence.md)에 첨부.

**구현 중 확정된 판단**: role/status 필터는 `stringValue().eq()`(cast 유지)로 구현. 기존 "존재하지 않는 role 문자열 → 빈 결과" 계약(경계 테스트로 검증됨)을 보존하기 위함. `Role.valueOf()`는 cast를 없애지만 잘못된 값에 예외를 던져 계약을 깨므로 채택하지 않음.
