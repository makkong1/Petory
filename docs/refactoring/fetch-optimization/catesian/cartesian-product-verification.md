# 카타시안 곱(Fetch Join 행 폭증) 발생 확인

## 목적

`JOIN FETCH`로 **루트**와 연관을 가져올 때 **SQL 행 수·전송량**이 비정상적으로 불어나는지 코드 기준으로 정리한다.  
(트러블슈팅 시 SQL 로그의 row 수를 볼 때 이 문서를 1차 체크리스트로 쓴다.)

**용어 구분 (이 문서에서 쓰는 말)**

| 표현 | 의미 |
|------|------|
| **다중 컬렉션 카타시안** (문제로 자주 지적되는 패턴) | 한 JPQL에서 **서로 다른 `@OneToMany` 둘 이상**을 동시에 fetch → 행 수가 대략 **n×m**으로 불어남. |
| **1:N fetch fan-out** (단일 컬렉션) | `LEFT JOIN FETCH u.pets`처럼 **컬렉션 하나**만 fetch하면, 부모 컬럼이 자식 행 수만큼 **반복**되는 것. 수학적 “곱집합”이 아니라 **조인 결과의 정상적인 행 반복**에 가깝고, 전형적인 **이중 컬렉션 카타시안**과는 구분한다. |
| **`WHERE`로 유저 한 명만** | 루트가 한 행으로 좁혀져도, 펫을 fetch하면 **펫 개수만큼** JDBC 행이 생김. 그래도 **단일 컬렉션 fan-out**일 뿐, “한 명인데도 카타시안”이 되는 것은 아님. |

---

## 배경 (짧게)

- 컬렉션을 fetch join하면 SQL에서 **부모 컬럼이 자식 행 수만큼 반복**된다(1:N **fan-out**). 이것만으로는 관계형 조인의 당연한 결과이며, **엄밀한 의미의 카타시안 곱**(조인 조건 없이 두 집합을 곱한 것)과는 다르다.
- **두 개의 독립적인 `@OneToMany`를 한 쿼리에서 동시에 fetch**하면 행 수가 대략 **n×m**으로 불어나는 것이 이 문서에서 말하는 **다중 컬렉션 카타시안**에 해당한다. (Hibernate 5.1+는 다중 컬렉션 fetch join을 제한하기도 함.)
- `SELECT DISTINCT 루트`는 persistence context에서 **루트 엔티티 인스턴스 중복**을 줄이지만, **DB가 반환하는 물리 행 수** 자체는 그대로일 수 있어 I/O·메모리 부담은 남는다.
- **`@ManyToOne`만 여러 개** fetch하는 경우(예: `user` + `service`)는 “컬렉션×컬렉션” 곱은 아니고, 보통 루트당 1행에 가깝다. (이 문서의 **높음** 위험은 주로 **OneToMany fetch**에 해당.)

---

## 확인 결과 요약

| 구분 | 위험도 | 비고 |
|------|--------|------|
| 일반 게시글 `Board` 단건/목록 (`user`만 fetch) | 낮음 | 컬렉션 fetch 없음 |
| 실종 게시글 `MissingPetBoard` (`user`만 fetch) | 낮음 | 댓글 fetch join 메서드는 **미사용이라 제거됨**. 댓글은 `SpringDataJpaMissingPetCommentRepository` 등 별도 조회 |
| 유저 `Users` (`pets`만 fetch) | 낮음 | **단일 컬렉션**만 → **1:N fan-out**(펫 수만큼 행·부모 컬럼 반복). **다중 컬렉션 카타시안(n×m)** 은 아님 |
| 모임 `Meetup` 상세 (`participants` 등) | 낮음~중간 | **`@OneToMany`는 `participants` 하나** → **다중 컬렉션 카타시안(n×m) 아님**. 참가자 수만큼 **1:N fan-out**·`DISTINCT m` |
| 케어 요청 `CareRequest` 목록/상세 (`applications` 등) | 낮음~중간 | **`@OneToMany`는 `applications`만** fetch(`comments` 미포함) → **다중 컬렉션 카타시안(n×m) 아님**. 지원 수만큼 **1:N fan-out**·목록은 `DISTINCT cr` |
| 장소 리뷰 `LocationServiceReview` (목록·단건에서 `user`/`service` fetch) | 낮음 | 연관이 **다대일 2개** — 전형적인 이중 **컬렉션** 곱은 아님 |
| 펫코인 `PetCoinTransaction` (목록 `EntityGraph`·단건 `JOIN FETCH user`) | 낮음 | **`user` 하나만** eager 그래프 — 곱 구조 아님 |
| 펫코인 에스크로 `PetCoinEscrow` (`findByCareRequestIdxWithDetails`) | 낮음 | `requester`·`provider`·`careRequest`는 모두 **다대일** |

---

## 리포지토리 정리와 이 문서

미사용 조회 메서드 제거(location·meetup·payment 등)로 **JPQL/네이티브 노출 면적**은 줄었지만, **남아 있는 fetch join**의 행 폭증 특성은 아래 표와 동일하다. 제거된 쿼리는 더 이상 런타임에 실행되지 않는다.

---

## 코드 위치별 상세

### 1. 일반 게시글 — `SpringDataJpaBoardRepository`

- `findByIdWithUser` 등: **`JOIN FETCH b.user`만** 사용.
- 댓글은 이 쿼리에 묶이지 않음 → **게시글×유저** 수준에서의 카타시안 곱 **해당 없음**.

### 2. 실종 게시글 — `SpringDataJpaMissingPetBoardRepository`

- 목록·단건·페이징은 **`JOIN FETCH b.user`만** (컬렉션 fetch 없음) → 게시글 레포 기준 카타시안 **해당 없음**.
- 예전에 있던 `findAllWithCommentsByOrderByCreatedAtDesc` 등 **댓글까지 한 번에 fetch하는 메서드**는 서비스에서 쓰이지 않아 **삭제함.** 댓글은 `SpringDataJpaMissingPetCommentRepository` 등에서 따로 로드하는 패턴이 유지된다.
- 나중에 비슷한 JPQL을 다시 넣을 경우: `comments` fetch 시 **게시글당 댓글 수만큼 SQL 행이 불어날 수 있음** → `SELECT DISTINCT`·쿼리 분리 등을 검토할 것.

### 3. 유저 — `SpringDataJpaUsersRepository`

- `findByIdWithPets` / `findByIdStringWithPets`: `LEFT JOIN FETCH u.pets` **한 컬렉션** + `DISTINCT u`.
- **확인·예상**: 유저 한 명·펫 3마리면 JDBC 결과는 **대략 3행**이고, 유저 쪽 컬럼은 행마다 반복된다. 이는 **1:N 조인 fan-out**이지, “유저 1명으로 특정했는데도 카타시안인가?”에 대한 답은 **아니오** — **다중 `@OneToMany` 동시 fetch**에서 나오는 **n×m** 유형의 카타시안과 구분된다.
- `DISTINCT u`는 위 **행 반복**을 엔티티 그래프에서 **User 인스턴스 하나**로 합치기 위한 것이다.
- **두 개의 `@OneToMany`를 동시에 fetch하지 않으므로** “이중 컬렉션 카타시안” 위험은 해당 없음.

### 4. 모임 — `SpringDataJpaMeetupRepository`

- `findByIdWithDetails`: `LEFT JOIN FETCH m.organizer` + `LEFT JOIN FETCH m.participants p` + `LEFT JOIN FETCH p.user` + `SELECT DISTINCT m`.
- **다중 컬렉션 카타시안(n×m)은 아님** — JPQL에 **`@OneToMany` 컬렉션은 `participants`만** 있고, `organizer`·`p.user`는 **`@ManyToOne`**이라 행 수를 서로 곱하지 않는다.
- 그래도 `participants` 때문에 **참가자 N명이면 JDBC 결과는 대략 N행**이고, `meetup`·`organizer` 컬럼은 행마다 반복된다 → **단일 컬렉션 1:N fan-out**(§3 유저+펫과 같은 부류). 참가자가 매우 많으면 **전송량·메모리**만 유의하면 된다.
- (참고) `findByDateBetween…` 등 **미사용** 메서드는 meetup 레포에서 제거됨. 상세 fetch 패턴은 그대로.

### 5. 펫케어 요청 — `SpringDataJpaCareRequestRepository`

- 엔티티 `CareRequest`에는 `applications`·`comments` 둘 다 `@OneToMany`이나, **현재 JPQL은 `applications`만** `LEFT JOIN FETCH`한다. (`comments`는 별도 조회·레포 주석에 **중첩 컬렉션 fetch 제한** 언급이 있음.)
- **다중 컬렉션 카타시안(n×m)은 아님** — 컬렉션 fetch는 **하나**이고, `user`·`pet`·`a.provider`는 **ManyToOne**이라 행을 서로 곱하지 않는다.
- 목록 계열: `JOIN FETCH cr.user` + `LEFT JOIN FETCH cr.pet` + `LEFT JOIN FETCH cr.applications` + **`SELECT DISTINCT cr`** → 요청마다 지원 건수만큼 행이 늘어나 **목록 전체**면 JDBC 행 수가 커질 수 있음(여전히 **단일 컬렉션 fan-out**의 합).
- `findByIdWithApplications`: 단건 `idx`라 **SQL 행 수 ≈ 지원 건수**(또는 지원 0이면 1행). 모임 상세·유저+펫과 같은 **1:N fan-out** 부류.

### 6. 기타 (참고)

- **Payment — `SpringDataJpaPetCoinEscrowRepository`**  
  - 실제 사용 경로는 `findByCareRequestIdxWithDetails` 등 **다대일 fetch** 위주.  
  - `findByRequesterOrProvider`·단순 `findByCareRequestIdx` 등은 **도메인에서 제거**되어, 카타시안 점검 대상이던 “정의만 있는” JPQL은 사라짐.

- **Payment — `SpringDataJpaPetCoinTransactionRepository`**  
  - 단건: `JOIN FETCH t.user`.  
  - 목록: `findByUserOrderByCreatedAtDesc(..., Pageable)` + `@EntityGraph(attributePaths = "user")` — **단일 ManyToOne** 그래프.

- **`SpringDataJpaLocationServiceReviewRepository`**  
  - 목록: `JOIN FETCH r.user` (또는 `service` fetch) — **컬렉션 fetch가 아니라 다대일**이면 행은 “리뷰 개수”에 비례할 뿐, **두 컬렉션의 곱**은 아님.

- **Location 장소 서비스** (`SpringDataJpaLocationServiceRepository`)  
  - 반경·지역 등 **미사용** 네이티브/JPQL 다수 제거됨. 남은 쿼리는 서비스·공공데이터 연동에서 쓰는 것만 — **fetch join으로 컬렉션을 한 번에 늘리는 패턴**은 이 레포 기준으로는 상기 리뷰·게시글 쪽과 비교적 무관.

---

## 다음 액션(리팩토링 시)

- **목록 + 큰 컬렉션**: fetch join 대신 루트만 조회 후 `@BatchSize` 또는 `WHERE board_id IN (...)` 배치 로드 등 [Fetch 최적화 README](../README.md) 규칙과 맞출 것.
- **단건 상세**: 트래픽·데이터 크기 허용 시 fetch join 유지 가능. 댓글/지원이 매우 많으면 **쿼리 분리** 검토.
- 검증: `spring.jpa.show-sql=true` 또는 로깅으로 **동일 API 호출 시 반환 row 수**를 확인.

---

## 관련 문서

- [Fetch 최적화 규칙 (README)](../README.md)
- [analysis/entity-schema/03-n-plus-one-strategy.md](../../../analysis/entity-schema/03-n-plus-one-strategy.md) (N+1과의 구분 참고)

---

*작성: 코드베이스 JPQL 기준 점검. 리포지토리 메서드명·쿼리 변경 시 본 문서를 함께 갱신할 것.*

*폴더명 `catesian`은 `cartesian` 오타. 경로·링크 일괄 변경 시 README와 상호 링크를 함께 수정할 것.*
