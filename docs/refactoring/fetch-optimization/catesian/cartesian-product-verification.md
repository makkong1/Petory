# 카타시안 곱(Fetch Join 행 폭증) 발생 확인

## 목적

`JOIN FETCH`로 **루트 엔티티**와 **둘 이상의 컬렉션(`@OneToMany`)**을 한 JPQL에서 동시에 가져오거나, **하나의 컬렉션**을 fetch하면서 결과 행이 루트×자식 수만큼 불어나는지 코드 기준으로 정리한다.  
(트러블슈팅 시 SQL 로그의 row 수·전송량이 비정상일 때 이 문서를 1차 체크리스트로 쓴다.)

---

## 배경 (짧게)

- Hibernate는 컬렉션을 fetch join하면 SQL에서 **부모 행이 자식 행 수만큼 반복**된다.
- **두 개의 독립적인 `@OneToMany`를 한 쿼리에서 동시에 fetch**하면 행 수가 대략 **n×m**으로 불어나는 전형적인 카타시안 곱이 된다. (Hibernate 5.1+는 다중 컬렉션 fetch join을 제한하기도 함.)
- `SELECT DISTINCT 루트`는 persistence context에서 **엔티티 인스턴스 중복**을 줄이지만, **DB가 반환하는 물리 행 수** 자체는 그대로일 수 있어 I/O·메모리 부담은 남는다.

---

## 확인 결과 요약

| 구분 | 위험도 | 비고 |
|------|--------|------|
| 일반 게시글 `Board` 단건/목록 (`user`만 fetch) | 낮음 | 컬렉션 fetch 없음 |
| 실종 게시글 `MissingPetBoard` (`user`만 fetch) | 낮음 | 댓글 fetch join 메서드는 **미사용이라 제거됨**. 댓글은 `SpringDataJpaMissingPetCommentRepository` 등 별도 조회 |
| 유저 `Users` (`pets`만 fetch) | 낮음 | **단일 컬렉션**만. 컬렉션×컬렉션 곱은 아님 |
| 모임 `Meetup` 상세 (`participants` 등) | **중간~높음** | 참가자 수만큼 행 증가. `DISTINCT m` |
| 케어 요청 `CareRequest` 목록/상세 (`applications` 등) | **중간~높음** | 지원 건수만큼 행 증가. 목록은 `DISTINCT cr` |
| 리뷰·에스크로 등 (연관이 전부 `@ManyToOne`) | 낮음 | 컬렉션 fetch가 없으면 곱셈 구조 아님 |

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
- **두 개의 `@OneToMany`를 동시에 fetch하는 패턴이 아니면** 전형적인 “이중 컬렉션 카타시안”은 아님.

### 4. 모임 — `SpringDataJpaMeetupRepository`

- `findByIdWithDetails`: `organizer` + `participants` + `p.user` fetch.
- `participants`가 컬렉션이면 **참가자 수만큼** 루트 행이 반복됨. `SELECT DISTINCT m` 사용.

### 5. 펫케어 요청 — `SpringDataJpaCareRequestRepository`

- 목록 계열: `JOIN FETCH cr.user` + `LEFT JOIN FETCH cr.pet` + `LEFT JOIN FETCH cr.applications` + **`SELECT DISTINCT cr`**
- `findByIdWithApplications`: `pet`, `user`, `applications`, `a.provider` — **`applications` 컬렉션**으로 행 폭증 가능. 단건이어도 **SQL row 수**는 지원 건수에 비례.

### 6. 기타 (참고)

- `SpringDataJpaPetCoinEscrowRepository` 등: `requester`, `provider`, `careRequest`가 모두 **다대일**이면 컬렉션 fetch 곱은 없음.
- `SpringDataJpaLocationServiceReviewRepository`: `user` + `service` 동시 fetch는 **다대일 2개** → 루트당 1행 수준.

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
