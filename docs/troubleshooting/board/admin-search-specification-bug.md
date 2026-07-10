# 관리자 검색 기능 버그 — Specification(Criteria API) 조합 오류

## 📋 개요

관리자 게시글 목록/실종동물 제보 목록에서 검색어(`q`)를 입력하면 500 에러가 발생하는 문제.
`Specification`(Spring Data JPA의 동적 쿼리 인터페이스) 도입 여부를 검토하던 중,
테스트 커버리지가 0이었던 이 기능을 테스트로 채우다가 발견했다.

| 항목          | 내용                                                                                       |
| ------------- | ------------------------------------------------------------------------------------------ |
| **증상**      | 관리자 실종동물 제보 탭에서 검색어 입력 시 500 에러 (서버 로그로 실사용 환경에서도 확인됨) |
| **영향 범위** | `board` 도메인 2곳만 — `BoardService`, `MissingPetBoardService`의 관리자 검색              |
| **재현**      | ✅ 가능 (매번 100% 재현)                                                                   |
| **분류**      | DB 쿼리 / Hibernate Criteria API                                                           |

---

## 0. Specification이 뭔지부터

`Specification<T>`는 Spring Data JPA가 제공하는 **동적 쿼리 조합 인터페이스**다. DDD의 "명세(Specification) 패턴"을 JPA Criteria API 위에 얹은 것.

**핵심 아이디어**: "조건 하나 = 객체 하나"로 만들고, 필요한 것만 `.and()`로 이어붙인다.

```java
// (root, query, criteriaBuilder) -> Predicate 를 반환하는 람다 하나가 조건 하나
Specification<Board> deletedSpec = (root, query, cb) -> cb.equal(root.get("isDeleted"), deleted);
Specification<Board> categorySpec = (root, query, cb) -> cb.equal(root.get("category"), category);

// 파라미터가 있을 때만 조건을 붙인다 — 없으면 그 조건 자체가 안 생김
Specification<Board> spec = null;
if (deleted != null)  spec = spec == null ? deletedSpec  : spec.and(deletedSpec);
if (category != null) spec = spec == null ? categorySpec : spec.and(categorySpec);

// null이면 "조건 없음"으로 처리되어 전체 조회됨
boardRepository.findAll(spec, pageable);   // Page<Board> — 페이징까지 자동 처리
```

이렇게 하면 필터 조합마다 쿼리 메서드를 따로 만들 필요가 없다 (`관리자검색_전체()`, `관리자검색_상태별()`, `관리자검색_상태별_키워드포함()` ... 식으로 조합 폭발하는 걸 방지).

**주의할 점**: `root.get("필드명")`처럼 필드를 **문자열**로 참조한다. 이게 이번 버그와는 직접 관련 없지만, 엔티티 필드를 리네임하면 컴파일 에러 없이 런타임에 깨질 수 있는 지점이라는 것만 기억해두면 된다.

---

## 1. 어떻게 여기까지 왔나 (조사 타임라인)

이 버그는 처음부터 "버그를 찾자"고 시작한 게 아니라, 아래 순서로 자연스럽게 드러났다.

```
1. JPA 쿼리 작성 방식 4가지(애노테이션/EntityManager/네이티브/QueryDSL) 비교 요청
        ↓
2. 프로젝트 현황 조사 → @Query 31개 파일, native 9개, EntityManager 3개, QueryDSL 0개
        ↓
3. "QueryDSL 도입할 만큼 동적 검색 화면 있나?" 조사
   → board 도메인 2곳(게시글/실종제보 관리자 목록)이 이미 Specification으로
     동적 검색을 구현 중인 것 발견. QueryDSL은 이미 있는 걸 다시 만드는 셈이라 보류.
        ↓
4. "런타임보다 컴파일타임이 낫지 않냐" 논의
   → hibernate-jpamodelgen(정적 메타모델) 도입 검토
        ↓
5. 검토 중 이 관리자 필터 메서드들의 테스트 커버리지가 0(전부 주석 처리됨)이라는 걸 확인
   → "필드명 오타 하나만 잡아주는 정적 메타모델보다, 테스트를 채우는 게
      비용 대비 효과가 낫다"고 판단 (로직 버그까지 잡아줌)
        ↓
6. board/missingPetBoard/user 관리자 필터 3개 메서드에 대해 정상/예외/경계 테스트 작성
        ↓
7. 테스트 실행 → 22개 중 8개 실패, 전부 "검색어(q)" 관련
        ↓
8. 사용자가 실제 서버 켜서 확인 → 서버 로그에서 동일 에러 실사용 환경에서도 재현됨
        ↓
9. "이 버그 다른 도메인에도 있나?" 전수 조사 → board 도메인 2곳에만 국한 확인
        ↓
10. (현재) 문서화 + 해결 방안 정리
```

즉 **테스트 커버리지 0이었던 코드에 테스트를 채우는 과정에서 실제 프로덕션 버그 2건이 드러난 것**이 이 문서의 본질이다.

---

## 2. 발견된 버그

### 버그 1: `MissingPetBoardService` — 검색어 입력 시 무조건 500 에러

**파일**: `MissingPetBoardService.java:406`

```java
Specification<MissingPetBoard> searchSpec = (root, query, cb) -> {
    Join<MissingPetBoard, Users> userJoin = root.join("user", JoinType.LEFT);
    return cb.or(
            cb.like(cb.lower(root.get("title")), keyword),
            cb.like(cb.lower(root.get("content")), keyword),   // ← 여기
            cb.like(cb.lower(root.get("petName")), keyword),
            cb.like(cb.lower(userJoin.get("username")), keyword));
};
```

**에러**:

```
org.hibernate.query.sqm.produce.function.FunctionArgumentException:
Parameter 1 of function 'lower()' has type 'STRING',
but argument is of type 'java.lang.String' mapped to 'CLOB'
```

**원인**: `MissingPetBoard.content` 필드가 `@Lob`(CLOB)으로 매핑되어 있다.

```java
// MissingPetBoard.java:54
@Lob
private String content;
```

Hibernate 6의 `lower()` 함수는 인자 타입을 엄격하게 검증하는데, CLOB 타입에는 `lower()`를 직접 적용할 수 없다. Java 코드는 둘 다 `String`이라 컴파일도 되고 눈으로 봐도 문제가 없어 보이지만, DB 매핑 타입(CLOB vs VARCHAR)이 다르면 런타임에 깨진다 — **컴파일 타임 타입 체크로는 절대 못 잡는 종류의 버그**다.

### 버그 2: `BoardService` — 검색어 + 다른 필터 동시 사용 시 SQL 문법 오류

**파일**: `BoardService.java:543-553`

```java
Specification<Board> searchSpec = (root, query, cb) -> {
    Join<Board, Users> userJoin = root.join("user", JoinType.LEFT);
    return cb.or(
            cb.gt(
                    cb.function("MATCH", Double.class,
                            root.get("title"), root.get("content"),
                            cb.literal(trimmed)),
                    0.0),
            cb.like(cb.lower(userJoin.get("username")), trimmed.toLowerCase() + "%"));
};
spec = spec == null ? searchSpec : spec.and(searchSpec);
```

**에러**:

```
java.sql.SQLSyntaxErrorException: You have an error in your SQL syntax;
... near ''강아지')>0.0 or lower(u1_0.username) like '강아지%' escape '') and 1=1 or' at line 1
```

**원인**: MySQL FULLTEXT `MATCH() AGAINST()`(via `cb.function`)와 `cb.like()`를 `cb.or()`로 묶은 뒤 다른 Specification과 `.and()`로 합성하면, Hibernate가 `LIKE ... ESCAPE ''`(빈 이스케이프 문자)를 생성한다. MySQL은 `ESCAPE`에 반드시 문자 1개를 요구하므로 문법 오류가 난다. 검색어 필터가 카테고리/삭제여부 필터와 **동시에** 걸릴 때만 터지기 때문에 (검색어만 단독으로 쓰면 상대적으로 덜 보이는 조합이라) 더 늦게 발견됐다.

---

## 3. 영향 범위 확인

Specification(`cb.lower`, `cb.like`, `cb.function`)을 실제로 사용하는 곳이 프로젝트 전체에서 이 2곳뿐인지 전수 조사했다.

```bash
grep -rn "cb.lower\|cb\.upper" backend/main/java --include="*.java"
# → BoardService.java:551, MissingPetBoardService.java:405~408 (총 2개 파일)

grep -rln "LOWER(" backend/main/java --include="*.java"   # JPQL 문자열 쪽
# → 0건
```

다른 도메인(care, meetup, location, file, user)의 관리자 검색은 전부 `Specification`이 아니라 `(:keyword IS NULL OR x LIKE %:keyword%)` 형태의 **JPQL 문자열 방식**을 쓴다. 이는 완전히 다른 쿼리 생성 경로라 이번 버그(CLOB `lower()`, `escape ''`)의 영향을 받지 않는다.

**결론: 이 두 곳이 버그의 전체 범위다.** 다른 도메인은 안전하다.

---

## 4. 해결 방안과 실제 적용 내역

### 버그 1: CLOB `lower()` 문제 — ✅ 적용 완료

**적용한 수정**: `content`(CLOB)에서만 `cb.lower()`를 제거했다. 테이블 콜레이션이 `utf8mb4_0900_ai_ci`(대소문자 미구분)임을 `SHOW TABLE STATUS`로 직접 확인했으므로, `lower()` 없이도 검색 결과는 동일하다.

```java
// MissingPetBoardService.java
cb.like(root.get("content"), keyword)   // cb.lower() 제거, title/petName/username은 그대로 유지
```

title/petName/username은 VARCHAR라 원래 문제 없었기 때문에 그대로 두고, 문제가 된 `content` 한 줄만 고쳤다(외과적 변경).

### 버그 2: `MATCH...AGAINST` 구문 오류 — ✅ 적용 완료 (최초 진단은 틀렸음)

**최초 진단(문서 v1)은 틀렸다.** `escape ''` 때문이라고 보고 `cb.like(..., '\\')`로 이스케이프 문자를 명시했지만, 재테스트 결과 **똑같은 자리에서 똑같이 실패**했다. 에러 위치를 다시 보니 진짜 원인은 별개였다:

`cb.function("MATCH", Double.class, title, content, cb.literal(keyword))`는 `MATCH(title,content,'키워드')`라는 **함수 호출 하나**로 렌더링되는데, 이는 MySQL 문법이 아니다. 올바른 문법은 `MATCH(title,content) AGAINST('키워드')` — **괄호가 분리된 두 절**이다. `cb.function()`은 "이름(인자,...)" 형태만 만들 수 있어 이 구조를 표현할 방법이 없었다. 즉 **이 검색 조건은 Specification 도입 이후 한 번도 정상 동작한 적이 없었다.**

**적용한 근본 해결**: Hibernate `FunctionContributor` SPI로 `matchAgainst`라는 패턴 함수를 새로 등록해서, `MATCH(...) AGAINST(...)` 두-괄호 구문을 Criteria API에서도 쓸 수 있게 만들었다.

```java
// global/config/MySqlFunctionContributor.java (신규)
public class MySqlFunctionContributor implements FunctionContributor {
    @Override
    public void contributeFunctions(FunctionContributions functionContributions) {
        BasicType<Double> doubleType = functionContributions.getTypeConfiguration()
                .getBasicTypeRegistry().resolve(StandardBasicTypes.DOUBLE);
        functionContributions.getFunctionRegistry()
                .registerPattern("matchAgainst", "match(?1,?2) against (?3)", doubleType);
    }
}
```
```
# resources/META-INF/services/org.hibernate.boot.model.FunctionContributor (신규, SPI 등록)
com.linkup.Petory.global.config.MySqlFunctionContributor
```
```java
// BoardService.java — 호출부만 "MATCH" → "matchAgainst"로 교체
cb.function("matchAgainst", Double.class, root.get("title"), root.get("content"), cb.literal(trimmed))
```

수정 후 생성되는 실제 SQL을 로그로 확인:
```sql
... and (match(b1_0.title,b1_0.content) against ('...')>? or lower(u1_0.username) like ? escape '\\') and 1=1 ...
```
`MATCH(...) AGAINST(...)`가 정확히 두 괄호로 분리되어 렌더링되는 것을 확인했다. 이스케이프 문자 명시(`'\\'`)는 원인은 아니었지만 부작용이 없어 그대로 유지했다.

### 부수적으로 발견한 것: InnoDB FULLTEXT는 커밋된 데이터만 검색한다

버그 2를 수정한 뒤에도 검색 테스트가 실패해서 더 파봤다. **InnoDB FULLTEXT 인덱스는 트랜잭션이 커밋되기 전까지는 검색 대상에 포함되지 않는다** — 일반 B-tree 인덱스와 달리 같은 트랜잭션 안에서 방금 삽입한 행이 `MATCH...AGAINST`에 안 잡힌다. `mysql` 클라이언트로 직접 커밋해서 확인해보니 관련도 164.1로 정상 매칭되는데, `@Transactional` 롤백 방식의 테스트 안에서는 항상 0건이었던 이유가 이거였다.

해결: `TestTransaction.flagForCommit(); TestTransaction.end(); TestTransaction.start();`로 setUp()의 삽입을 실제 커밋한 뒤 새 트랜잭션에서 검색하도록 테스트를 수정했고, `tearDown()`에서도 동일하게 커밋까지 명시해서 삭제가 실제로 반영되도록 했다(안 그러면 테스트 데이터가 개발 DB에 영구히 남는다).

### 공통 재발 방지

- 두 버그 모두 **테스트가 0개였기 때문에** 지금까지 발견 못 했다. `BoardServiceAdminFilterTest`, `MissingPetBoardServiceAdminFilterTest`를 CI에 포함시켜 회귀 방지.
- Specification 람다를 서비스 메서드 안에 인라인으로 두지 말고, `BoardPopularitySnapshotSpecs`처럼 별도 static 팩토리 클래스로 분리하면 조건 단위 유닛 테스트가 쉬워진다 (이번엔 범위를 벗어나 적용하지 않음).

---

## 5. 향후 계획과의 연관성

`Meetup` 도메인의 `findAllForAdminWithKeyword`도 동일하게 `MATCH() AGAINST()` + `LIKE`를 섞어 쓰는 네이티브 쿼리다. 여기를 Specification으로 통합할 계획이 있다면, 이번에 등록한 `matchAgainst` 패턴 함수를 그대로 재사용하면 된다.

---

## 6. 상태

- [x] 원인 분석 완료
- [x] 재현 테스트 작성 (`BoardServiceAdminFilterTest`, `MissingPetBoardServiceAdminFilterTest`)
- [x] 버그 1 수정 (CLOB `lower()` 제거) — 6/6 테스트 통과
- [x] 버그 2 수정 (`MySqlFunctionContributor` 등록으로 `MATCH...AGAINST` 정상화) — 10/10 테스트 통과
- [x] board/admin 도메인 전체 회귀 테스트 (46개) 통과 확인
- [ ] Meetup 도메인 Specification 통합 (별도 진행)
