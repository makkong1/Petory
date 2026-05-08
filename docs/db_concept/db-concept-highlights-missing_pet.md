# DB 개념 어필 포인트 — MissingPet 도메인

> 코드베이스 실측 데이터 기준 (실제 파일 확인)
>
> 참조 코드:
> - `backend/main/java/com/linkup/Petory/domain/board/service/MissingPetBoardService.java`
> - `backend/main/java/com/linkup/Petory/domain/board/service/MissingPetCommentService.java`
> - `backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaMissingPetCommentRepository.java`
> - `backend/main/java/com/linkup/Petory/domain/board/repository/SpringDataJpaMissingPetBoardRepository.java`
> - `backend/test/java/com/linkup/Petory/domain/board/service/MissingPetBoardConcurrencyTest.java`

---

## 1. N+1 문제 발견 및 해결 — Converter 메서드 분리 + 배치 조회

### 어필 포인트

- **문제 발견**: 게시글 목록 조회(`GET /api/missing-pets`) 시 `MissingPetConverter.toBoardDTO()`가 `board.getComments()`를 접근하면서 게시글마다 댓글 조회 쿼리가 개별 발생
- **실제 측정**: 103개 게시글 조회 시 총 105개 쿼리 (게시글 1 + 파일 1 + 댓글 103)
- **해결 방법 1 — Converter 메서드 분리**: `toBoardDTOWithoutComments()` 메서드를 별도로 추가해 댓글 필드를 전혀 접근하지 않아 LAZY 로딩 자체를 차단
- **해결 방법 2 — 댓글 수 배치 조회**: `countCommentsByBoardIds(boardIds)` — IN 절 + GROUP BY로 모든 게시글의 댓글 수를 쿼리 1회에 조회
- **해결 방법 3 — 파일 배치 조회**: `getAttachmentsBatch(FileTargetType.MISSING_PET, boardIds)` — IN 절로 모든 게시글의 파일을 쿼리 1회에 조회
- **결과**: 105개 쿼리 → 3개 쿼리 (97% 감소), 571ms → 106ms (81% 개선), 메모리 11MB → 3MB (73% 감소)

실제 배치 조회 쿼리 (`SpringDataJpaMissingPetCommentRepository`):

```java
@Query("SELECT mc.board.idx, COUNT(mc) FROM MissingPetComment mc " +
       "JOIN mc.user u " +
       "WHERE mc.board.idx IN :boardIds " +
       "AND mc.isDeleted = false " +
       "AND u.isDeleted = false " +
       "AND u.status = 'ACTIVE' " +
       "GROUP BY mc.board.idx")
List<Object[]> countCommentsByBoardIds(@Param("boardIds") List<Long> boardIds);
```

서비스에서의 실제 사용 패턴 (`MissingPetBoardService.getBoardsWithPaging()`):

```java
// 파일 배치 조회 (N+1 문제 해결)
Map<Long, List<FileDTO>> filesByBoardId = attachmentFileService.getAttachmentsBatch(
        FileTargetType.MISSING_PET, boardIds);

// 댓글 수 배치 조회 (N+1 문제 해결)
Map<Long, Integer> commentCountsByBoardId = missingPetCommentService.getCommentCountsBatch(boardIds);

// toBoardDTOWithoutComments 사용으로 N+1 문제 방지 (댓글 lazy loading 트리거 안함)
List<MissingPetBoardDTO> boardDTOs = boards.stream()
        .map(board -> {
            MissingPetBoardDTO dto = missingPetConverter.toBoardDTOWithoutComments(board);
            dto.setAttachments(filesByBoardId.getOrDefault(board.getIdx(), List.of()));
            dto.setCommentCount(commentCountsByBoardId.getOrDefault(board.getIdx(), 0));
            return dto;
        })
        .collect(Collectors.toList());
```

### 말할 내용

> "게시글 목록 조회 시 N+1 문제를 발견하고 해결한 경험이 있습니다. 103개 게시글을 조회할 때 댓글 조회 쿼리가 103번 실행되어 총 105개의 쿼리가 발생했습니다. 원인을 분석해보니 Converter 메서드가 내부에서 `board.getComments()`를 호출해 LAZY 로딩을 트리거하고 있었습니다. 해결 방법은 두 가지였습니다. 첫 번째로 댓글 필드에 접근하지 않는 `toBoardDTOWithoutComments()` 메서드를 별도로 만들어 LAZY 로딩 자체를 차단했습니다. 두 번째로 댓글 수와 파일을 각각 IN 절 + GROUP BY 배치 쿼리로 한 번에 조회하도록 변경했습니다. 결과적으로 쿼리 수를 97% 줄이고 응답 시간을 571ms에서 106ms로 개선했습니다."

---

## 2. 배치 UPDATE를 통한 댓글 일괄 소프트 삭제 최적화

### 어필 포인트

- **문제**: 기존 `deleteAllCommentsByBoard()`는 댓글 전체 조회 후 루프마다 `save()` 호출 — 댓글 1000개면 SELECT 1회 + UPDATE 1000회 = 1001 쿼리
- **해결**: `@Modifying` + `@Query`를 사용한 배치 UPDATE 쿼리로 대체
- **결과**: 1001 쿼리 → 1 쿼리 (댓글 1000개 기준)
- **추가 처리**: `@Modifying(clearAutomatically = true)` 적용 — bulk UPDATE는 영속성 컨텍스트를 우회하므로 PC와 DB 정합성 유지를 위해 실행 후 PC를 자동 초기화

실제 구현 (`SpringDataJpaMissingPetCommentRepository`):

```java
@Modifying(clearAutomatically = true)
@Query("UPDATE MissingPetComment mc SET mc.isDeleted = true, mc.deletedAt = :deletedAt " +
       "WHERE mc.board.idx = :boardIdx AND mc.isDeleted = false")
int softDeleteAllByBoardIdx(@Param("boardIdx") Long boardIdx,
                            @Param("deletedAt") LocalDateTime deletedAt);
```

서비스 호출부 (`MissingPetCommentService.deleteAllCommentsByBoard()`):

```java
@Transactional
public void deleteAllCommentsByBoard(MissingPetBoard board) {
    commentRepository.softDeleteAllByBoardIdx(board.getIdx(), java.time.LocalDateTime.now());
}
```

### 말할 내용

> "게시글 삭제 시 관련 댓글을 소프트 삭제하는 로직에서 N건 루프 UPDATE 문제를 발견했습니다. 기존 코드는 댓글을 모두 조회한 뒤 루프마다 save()를 호출하는 구조라, 댓글 수만큼 UPDATE 쿼리가 발생했습니다. 이를 JPQL `@Modifying` 배치 UPDATE 쿼리로 교체해 쿼리 1회로 처리하도록 바꿨습니다. 이때 bulk UPDATE는 영속성 컨텍스트를 우회하기 때문에 `clearAutomatically = true`를 설정해 PC와 DB의 정합성을 유지했습니다."

---

## 3. JPA 단일 COUNT 쿼리로 댓글 수 조회

### 어필 포인트

- **문제**: 기존에는 `commentRepository.findByBoardAndIsDeletedFalse(board)`로 댓글 전체를 로드한 뒤 `size()`로 댓글 수를 구하는 방식이었음 — 댓글 데이터를 메모리에 올리는 낭비
- **해결**: `countByBoardAndIsDeletedFalse(board)` — COUNT 쿼리 1회

실제 쿼리 (`SpringDataJpaMissingPetCommentRepository`):

```java
@Query("SELECT COUNT(mc) FROM MissingPetComment mc JOIN mc.user u " +
       "WHERE mc.board = :board AND mc.isDeleted = false " +
       "AND u.isDeleted = false AND u.status = 'ACTIVE'")
long countByBoardAndIsDeletedFalse(@Param("board") MissingPetBoard board);
```

서비스 호출부 (`MissingPetCommentService.getCommentCount()`):

```java
public int getCommentCount(MissingPetBoard board) {
    return (int) commentRepository.countByBoardAndIsDeletedFalse(board);
}
```

### 말할 내용

> "댓글 수를 구할 때 기존에는 댓글 전체를 메모리에 로드한 뒤 size()를 호출하는 구조였습니다. COUNT 쿼리 1회로 대체함으로써 불필요한 데이터 로딩을 없앴습니다. 특히 댓글이 많은 게시글일수록 효과가 큽니다."

---

## 4. 프로젝션 쿼리로 경량 조회 — getUserIdByBoardIdx

### 어필 포인트

- **문제**: 채팅 시작(`startMissingPetChat`) 시 제보자 ID만 필요한데, 기존에는 `getBoard()` 전체 조회 (파일, 댓글 수 포함) 호출
- **해결**: `findUserIdByIdx(boardIdx)` 프로젝션 쿼리로 userId 하나만 SELECT

실제 쿼리 (`SpringDataJpaMissingPetBoardRepository`):

```java
@Query("SELECT b.user.idx FROM MissingPetBoard b WHERE b.idx = :idx AND b.isDeleted = false")
Optional<Long> findUserIdByIdx(@Param("idx") Long idx);
```

서비스 사용 (`MissingPetBoardService.getUserIdByBoardIdx()`):

```java
public Long getUserIdByBoardIdx(Long boardIdx) {
    return missingPetBoardRepository.findUserIdByIdx(boardIdx)
            .orElseThrow(() -> new MissingPetBoardNotFoundException());
}
```

### 말할 내용

> "채팅방 생성 시 제보자의 userId만 필요한데, 기존 코드는 게시글 전체 DTO(파일, 댓글 수 포함)를 조회하고 있었습니다. 프로젝션 쿼리를 사용해 userId 컬럼 하나만 SELECT 하도록 바꿔 불필요한 데이터 조회를 제거했습니다."

---

## 5. Specification 기반 관리자 DB 레벨 필터링

### 어필 포인트

- **문제**: 기존 관리자 목록 조회는 `getBoards(status)` 전체 로드 후 Java Stream으로 `deleted`, `q` 필터링 — 데이터가 1만 건이면 1만 건을 메모리에 올림; 게다가 `isDeleted=false` 조건이 내장돼 있어 `deleted=true`(삭제된 게시글) 요청 시 항상 빈 목록 반환
- **해결**: `JpaSpecificationExecutor<MissingPetBoard>` 추가 + `Specification` 빌드 + `findAll(spec, pageable)` — status, deleted, q(제목/내용/반려동물명/작성자명) 필터를 DB 레벨에서 처리

실제 Specification 빌드 코드 (`MissingPetBoardService.getAdminBoardsWithPaging()`):

```java
// 검색어 필터 (제목, 내용, 반려동물 이름, 작성자명) - join으로 user 조인
if (q != null && !q.isBlank()) {
    String keyword = "%" + q.toLowerCase() + "%";
    Specification<MissingPetBoard> searchSpec = (root, query, cb) -> {
        Join<MissingPetBoard, Users> userJoin = root.join("user", JoinType.LEFT);
        return cb.or(
                cb.like(cb.lower(root.get("title")), keyword),
                cb.like(cb.lower(root.get("content")), keyword),
                cb.like(cb.lower(root.get("petName")), keyword),
                cb.like(cb.lower(userJoin.get("username")), keyword));
    };
    spec = spec == null ? searchSpec : spec.and(searchSpec);
}
```

### 말할 내용

> "관리자 목록 조회에서 전체 데이터를 메모리에 올린 뒤 Java Stream으로 필터링하는 구조를 발견했습니다. `JpaSpecificationExecutor`와 `Specification`을 사용해 status, 삭제 여부, 검색어 필터를 모두 DB 레벨 WHERE 절로 내렸고, 페이징도 DB에서 처리하도록 바꿨습니다. 덕분에 삭제된 게시글도 정상 조회됩니다."

---

## 6. 게시글 삭제 시 댓글 Soft Delete 동시성 문제 인식 및 해결

### 어필 포인트

- **식별한 문제**: 기존 `deleteBoard()`는 `board.getComments()`(영속성 컨텍스트에 로드된 컬렉션)를 순회하며 소프트 삭제했음 — 트랜잭션 중간에 다른 사용자가 댓글을 추가하면 해당 댓글이 누락됨
- **추가 문제**: `@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)` 설정이 Soft Delete 방식과 충돌 — `orphanRemoval`은 물리적 DELETE를 전제로 하는데 Soft Delete에서는 부모가 실제로 삭제되지 않으므로 동작 안 함; 반대로 컬렉션에서 직접 제거 시 예상치 못한 물리적 DELETE 발생 가능
- **해결**: `softDeleteAllByBoardIdx()` 배치 UPDATE 쿼리로 교체 — Repository를 통해 DB에서 직접 최신 댓글 집합을 대상으로 UPDATE 수행, 영속성 컨텍스트 상태와 무관하게 동작
- **현재 엔티티 상태**: 리팩토링 후 `orphanRemoval` 없이 `cascade = CascadeType.ALL`만 남아 있음 (확인 완료)

`MissingPetBoardConcurrencyTest`는 이 문제를 설명하는 전용 테스트로 작성되어 있으며, 게시글 삭제 전 댓글이 추가되는 시나리오를 시뮬레이션함.

### 말할 내용

> "게시글 삭제 시 관련 댓글을 소프트 삭제하는 로직에서 동시성 문제를 발견했습니다. 기존 코드가 영속성 컨텍스트에 로드된 `board.getComments()` 컬렉션을 순회하는 구조라, 트랜잭션 중간에 새 댓글이 추가되면 해당 댓글이 소프트 삭제 대상에서 빠질 수 있었습니다. 또한 `orphanRemoval = true`가 Soft Delete 방식과 충돌한다는 것도 분석했습니다. `orphanRemoval`은 물리적 삭제를 전제로 하는데, Soft Delete에서는 부모가 실제로 삭제되지 않아 트리거가 걸리지 않고, 반대로 컬렉션에서 엔티티를 직접 제거하면 의도치 않은 물리적 DELETE가 실행될 수 있습니다. 해결책으로 JPQL 배치 UPDATE 쿼리(`softDeleteAllByBoardIdx`)를 사용해 DB에서 직접 최신 댓글 집합에 대해 UPDATE를 수행하도록 바꿔 동시성 안전성을 확보했습니다."

---

## 7. JOIN FETCH를 통한 LAZY 로딩 N+1 방지

### 어필 포인트

- **목록 조회**: `findAllByOrderByCreatedAtDesc()` — JOIN FETCH로 `user` 정보를 함께 로딩해 게시글마다 사용자 조회 쿼리 발생 차단
- **단건 조회**: `findByIdWithUser(id)` — 마찬가지로 user JOIN FETCH
- **댓글 목록 조회**: `findByBoardAndIsDeletedFalseOrderByCreatedAtAsc()` — `JOIN FETCH mc.user u`로 댓글 작성자 정보 포함
- **updateBoard 개선**: 기존 `findById()` 사용 시 `board.getUser()` 접근에서 LAZY 로딩 발생 가능 → `findByIdWithUser()`로 통일

실제 쿼리 (`SpringDataJpaMissingPetBoardRepository`):

```java
@Query("SELECT b FROM MissingPetBoard b JOIN FETCH b.user u " +
       "WHERE b.isDeleted = false AND u.isDeleted = false AND u.status = 'ACTIVE' " +
       "ORDER BY b.createdAt DESC")
List<MissingPetBoard> findAllByOrderByCreatedAtDesc();
```

댓글 조회 (`SpringDataJpaMissingPetCommentRepository`):

```java
@Query("SELECT mc FROM MissingPetComment mc JOIN FETCH mc.user u " +
       "WHERE mc.board = :board AND mc.isDeleted = false " +
       "AND u.isDeleted = false AND u.status = 'ACTIVE' ORDER BY mc.createdAt ASC")
List<MissingPetComment> findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(@Param("board") MissingPetBoard board);
```

### 말할 내용

> "JPA의 LAZY 로딩으로 인해 발생하는 N+1 문제를 JOIN FETCH로 방지했습니다. 게시글 조회 시 작성자 정보를, 댓글 조회 시 댓글 작성자 정보를 함께 로딩하도록 JPQL에 JOIN FETCH를 명시했습니다. 또한 수정 로직에서 `findById()` 대신 `findByIdWithUser()`를 사용하도록 통일해 Lazy 로딩이 발생하는 경로를 차단했습니다."

---

## 8. 비동기 알림 발송 — @Async로 댓글 작성 트랜잭션 분리

### 어필 포인트

- **설계 원칙**: 알림 발송 실패가 댓글 작성 트랜잭션을 롤백하면 안 됨 → `@Async`로 별도 스레드에서 발송
- **조건 처리**: 댓글 작성자와 게시글 작성자가 같은 경우 알림 미발송 — `boardOwnerId.equals(user.getIdx())` 체크
- **실패 처리**: catch 블록에서 로그만 기록하고 예외를 다시 던지지 않아 알림 실패가 댓글 저장에 영향 없음

실제 코드 (`MissingPetCommentService`):

```java
Long boardOwnerId = board.getUser().getIdx();
if (!boardOwnerId.equals(user.getIdx())) {
    sendMissingPetCommentNotificationAsync(boardOwnerId, user.getUsername(),
            dto.getContent(), board.getIdx());
}

@Async
public void sendMissingPetCommentNotificationAsync(...) {
    try {
        notificationService.createNotification(...);
    } catch (Exception e) {
        log.error("실종제보 댓글 알림 발송 실패: ...", e);
        // 알림 발송 실패는 로깅만 하고 예외를 던지지 않음 (댓글 작성과 분리)
    }
}
```

### 말할 내용

> "알림 발송을 댓글 작성 트랜잭션과 분리했습니다. 알림 서버 장애가 생겨도 댓글 저장은 성공해야 하기 때문에 `@Async`로 별도 스레드에서 발송하고, 발송 실패는 로그만 기록한 뒤 예외를 다시 던지지 않도록 했습니다. 또한 자기 글에 자신이 댓글을 달 때는 알림이 발송되지 않도록 작성자 동일 여부를 체크했습니다."

---

## 인덱스 설계

### missing_pet_board 테이블

| 인덱스명 | 컬럼(순서) | 타입 | 설계 근거 |
|---|---|---|---|
| `PRIMARY` | `idx` | BTREE | PK — 단건 조회·FK 참조 기준 |
| `FKrid0u1qvm8e07etghggxnu1b1` | `user_idx` | BTREE | FK — 작성자별 게시글 조회, `idx_missing_pet_user`와 협력 |
| `idx_missing_pet_location` | `latitude, longitude` | BTREE | 반경 검색 시 위도·경도 범위 스캔. 복합 순서(위도→경도)는 위도 범위 필터 후 경도 범위 좁히기에 최적 |
| `idx_missing_pet_status` | `status, is_deleted, created_at` | BTREE | 상태·삭제 여부 필터 후 생성일 정렬. 관리자 목록 조회(`getAdminBoardsWithPaging`) 및 상태별 조회에 사용 |
| `idx_missing_pet_user` | `user_idx, is_deleted, created_at` | BTREE | 특정 사용자의 활성 게시글을 최신순 조회할 때 인덱스 전체를 커버(커버링 인덱스 효과) |

**설계 포인트**: `idx_missing_pet_status`와 `idx_missing_pet_user` 모두 `is_deleted` 컬럼을 포함해 Soft Delete 필터가 인덱스 레벨에서 처리된다. `idx_missing_pet_location`은 `(latitude, longitude)` 복합 인덱스로 위도 범위를 먼저 좁히고 경도를 필터링하는 2단계 범위 스캔을 지원한다.

### missing_pet_comment 테이블

| 인덱스명 | 컬럼(순서) | 타입 | 설계 근거 |
|---|---|---|---|
| `PRIMARY` | `idx` | BTREE | PK |
| `FKe3sca61815j9cxi608oxmrfjt` | `user_idx` | BTREE | FK — 작성자 JOIN 시 사용 |
| `FKpodx5stuchr73mrjgffir72ii` | `board_idx` | BTREE | FK — 게시글별 댓글 조회 기본 경로 |
| `idx_missing_pet_comment_board_is_deleted` | `board_idx, is_deleted` | BTREE | 게시글 삭제 시 댓글 소프트 삭제(`softDeleteAllByBoardIdx`) 및 배치 댓글 수 조회(`countCommentsByBoardIds`)에서 `board_idx IN (...)` + `is_deleted = false` 조건을 인덱스 레벨에서 처리 |

**설계 포인트**: `idx_missing_pet_comment_board_is_deleted`의 선두 컬럼이 `board_idx`이므로 `IN (boardIds)` 배치 조회와 `softDeleteAllByBoardIdx` 배치 UPDATE 모두 인덱스를 활용한다. 단순 FK 인덱스(`FKpodx5stuchr73mrjgffir72ii`)와 복합 인덱스가 공존하지만, 삭제 여부 필터가 없는 순수 JOIN 경로는 FK 인덱스를 사용해 중복을 최소화했다.

---

## 핵심 키워드

- **N+1 문제 해결** (Converter 분리 + 배치 조회)
- **배치 UPDATE** (`@Modifying(clearAutomatically = true)`)
- **프로젝션 쿼리** (필요한 컬럼만 SELECT)
- **JpaSpecificationExecutor** (동적 DB 레벨 필터링)
- **LAZY 로딩 트리거 방지** (JOIN FETCH, `toBoardDTOWithoutComments`)
- **Soft Delete와 orphanRemoval 충돌** 분석 및 해결
- **동시성 안전성** (영속성 컨텍스트 우회 배치 쿼리)
- **@Async 트랜잭션 분리** (알림 발송)
- **IN 절 + GROUP BY** 배치 댓글 수 조회
- **COUNT 쿼리 최적화** (전체 로드 → countByBoardAndIsDeletedFalse)
- **복합 인덱스** (status+is_deleted+created_at, user_idx+is_deleted+created_at, latitude+longitude)

---

## 관련 문서

- `docs/troubleshooting/missing-pet/n-plus-one-query-issue.md` — N+1 문제 발견~해결 전체 과정, 실측 SQL 로그 포함
- `docs/troubleshooting/missing-pet/potential-issues.md` — 동시성 문제, orphanRemoval 충돌 등 식별된 이슈 목록
- `docs/troubleshooting/missing-pet/orphanRemoval-soft-delete-analysis.md` — orphanRemoval과 Soft Delete 충돌 분석
- `docs/refactoring/missing-pet/missing-pet-backend-performance-optimization.md` — 리팩토링 항목별 Before/After 및 체크리스트
- `docs/domains/missingpet.md` — 도메인 전체 API·서비스 구조 설명

---

## 면접 대답 구성

### 질문: "DB 관련 문제를 해결한 경험이 있나요?"

**대답 구조:**

1. **문제 발견** (30초)
   - "실종 제보 게시글 목록 조회 시 성능 문제를 발견했습니다."
   - "103개 게시글 조회 시 댓글 조회 쿼리가 103번 실행되어 총 105개의 쿼리와 571ms의 응답 시간이 발생했습니다."

2. **원인 분석** (1분)
   - "JPA LAZY 로딩이 원인이었습니다. Converter 메서드 내부에서 `board.getComments()`를 호출해 게시글마다 쿼리가 트리거됐습니다."
   - "또한 댓글 소프트 삭제 로직이 영속성 컨텍스트 컬렉션을 순회해 동시성 문제도 있었고, `orphanRemoval = true` 설정이 Soft Delete 방식과 충돌한다는 것도 분석했습니다."

3. **해결 방법** (1분)
   - "목록 조회용 Converter 메서드(`toBoardDTOWithoutComments`)를 분리해 댓글 필드를 전혀 접근하지 않도록 했습니다."
   - "댓글 수는 IN 절 + GROUP BY 배치 쿼리로 1회에 조회하고, 파일도 IN 절 배치 조회로 처리했습니다."
   - "댓글 일괄 삭제는 `@Modifying` 배치 UPDATE 쿼리로 교체해 N건 루프를 없애고 동시성 안전성도 확보했습니다."

4. **결과 및 학습** (30초)
   - "쿼리 수를 97% 줄이고 응답 시간을 81% 개선했습니다."
   - "JPA 영속성 컨텍스트의 동작 원리, LAZY 로딩 트리거 시점, 배치 UPDATE 후 PC 정합성 관리 등을 깊이 이해하게 됐습니다."
