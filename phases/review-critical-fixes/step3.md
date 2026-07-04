# Step 3 — `@ManyToOne` fetch=LAZY 전환 10곳 + 조회 경로 검증

## 배경

`@ManyToOne`의 기본 fetch는 EAGER다. 아래 5개 엔티티 10곳이 fetch 전략 미지정 상태라, 이 엔티티들을 조회할 때마다 연관 엔티티(User, Board 등)를 무조건 추가 로딩한다. 반응(Reaction)류는 게시글당 수백 건씩 조회되는 테이블이라 N+1 직격탄.

핵심 엔티티(PetCoinEscrow, Meetup, CareRequest 등)에는 LAZY가 잘 붙어있고, 부속 엔티티에서만 누락됐다.

## 수정 대상 (10곳)

| 엔티티 | 파일 | 라인 | 연관 필드 |
|---|---|---|---|
| CareRequestComment | `domain/care/entity/CareRequestComment.java` | 30, 35 | careRequest 계열, user 계열 |
| BoardReaction | `domain/board/entity/BoardReaction.java` | 38, 42 | board, user |
| CommentReaction | `domain/board/entity/CommentReaction.java` | 38, 42 | comment, user |
| MissingPetComment | `domain/board/entity/MissingPetComment.java` | 38, 42 | board, user |
| LocationServiceReview | `domain/location/entity/LocationServiceReview.java` | 41, 45 | locationService, user |

(라인 번호는 2026-07 리뷰 시점 기준 — 실제 파일에서 `@ManyToOne` 위치를 재확인할 것)

## 수정 내용

각 지점을 다음과 같이 변경:

```java
// Before
@ManyToOne
@JoinColumn(name = "...")

// After
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "...")
```

`import jakarta.persistence.FetchType;` 누락 주의.

## ⚠️ 필수 검증 프로토콜 — OSIV가 꺼져 있다

이 프로젝트는 `spring.jpa.open-in-view=false`다. **EAGER→LAZY 전환 시 트랜잭션 밖에서 연관에 접근하는 지점이 있으면 즉시 `LazyInitializationException`이 터진다.** 단순 어노테이션 추가로 끝내지 말고, 엔티티별로 조회 경로를 전수 확인한다:

### 검증 절차 (엔티티별 반복)

1. **리포지토리 조회 메서드 나열**: 해당 엔티티를 반환하는 모든 쿼리 확인.
   ```bash
   grep -n "BoardReaction" backend/main/java/com/linkup/Petory/domain/board/repository/*.java
   ```
2. **fetch join 여부 분류**: `JOIN FETCH` / `@EntityGraph`가 있는 쿼리는 안전. 없는 쿼리(파생 쿼리 `findBy...`, `existsBy...`)가 반환한 엔티티의 연관 필드를 누가 접근하는지 추적.
3. **접근 지점 추적**: 서비스/컨버터에서 `entity.getUser()`, `entity.getBoard()` 등 접근 지점 확인. 접근이 `@Transactional` 메서드 안에서 완결(DTO 변환까지)되면 안전 — 이 프로젝트는 서비스가 DTO를 반환하는 구조라 대부분 안전할 것.
4. **위험 지점 발견 시**: 해당 리포지토리 쿼리에 `JOIN FETCH` 추가 (이것이 이 step의 정당한 scope — LAZY 전환이 유발한 수정).

### 사전 조사된 사용처 (여기부터 확인)

- **BoardReaction / CommentReaction**: `ReactionService`, `CommentService`, `BoardPopularityService` — 대부분 exists/count/delete 용도라 연관 접근이 드물 것으로 예상. `ReactionService`의 조회→DTO 변환 경로만 주의.
- **MissingPetComment**: `MissingPetConverter`, `MissingPetCommentService`, `ActivityConverter`(활동 피드 통합 조회) — `SpringDataJpaMissingPetCommentRepository`에 fetch join 이미 존재. **ActivityConverter 경로의 원본 쿼리에 fetch join이 있는지 반드시 확인.**
- **CareRequestComment**: `CareRequestCommentConverter`, `CareRequestCommentService`, `ActivityConverter`, `ActivityService` — `SpringDataJpaCareRequestCommentRepository`에 fetch join 이미 존재. 마찬가지로 Activity 경로 확인.
- **LocationServiceReview**: `SpringDataJpaLocationServiceReviewRepository`에 fetch join 이미 존재. 리뷰 요약 DTO(`LocationServiceReviewSummaryDTO`) 생성 경로 확인.

## 가드레일

- fetch join 추가 외의 쿼리 리팩터링 금지 (외과적 변경).
- 페이징 쿼리에 **컬렉션** fetch join을 추가하지 말 것 (in-memory 페이징 유발). 이 step의 연관은 전부 to-one이라 해당 없음 — to-one fetch join은 페이징과 안전하게 공존.
- `@BatchSize`가 이미 있는 컬렉션 연관은 건드리지 않는다.

## AC (Acceptance Criteria)

```bash
./gradlew compileJava
./gradlew test --tests '*Board*' --tests '*Reaction*' --tests '*Comment*' --tests '*Care*' --tests '*Location*' --tests '*Activity*'   # MySQL+Redis 필요
```

전체 테스트가 무겁다면 최소한 board / care / location / activity 도메인 테스트는 통과해야 한다.
