# 엔티티 캡슐화 리팩토링 백로그

> 최초 발견: 2026-06-02 / 마지막 갱신: 2026-06-04
> 범위: 전 도메인 엔티티 `@Setter` 남용 → 비즈니스 불변조건 미보호 + 추가 동시성·버그 이슈

---

## 왜 문제인가

현재 모든 엔티티에 클래스 레벨 `@Setter`가 달려 있어 아무 서비스에서나 필드를 직접 수정할 수 있다.

```java
// 지금: 어디서든 가능 — 거래내역 기록 없이 잔액 변경, 잘못된 상태 전환 등 막을 수 없음
user.setPetCoinBalance(0);
escrow.setStatus(EscrowStatus.REFUNDED); // HOLD 상태인지 체크 없이
board.setIsDeleted(true);               // deletedAt 빠뜨려도 컴파일 통과
```

**현재 락(findByIdForUpdate)이 있어 Race Condition은 방지되지만**,
불변조건(잔액 >= 0, 상태 전환 규칙, 소프트 삭제 원자성)은 서비스 코드에만 존재해서
새 기능/새 서비스가 이 규칙을 모르고 직접 set하면 데이터 정합성이 깨진다.

---

## 작업 순서 (우선순위 기준)

```
0단계  [버그, 즉시]    SUSPEND_USER 버그 수정
1단계  [동시성-조회수] viewCount 원자화 + BoardViewLog race condition 해소
2단계  [동시성-카운터] likeCount / dislikeCount / commentCount 원자화
3단계  [N+1]          게시글 삭제 시 댓글 벌크 UPDATE + commentCount 정책 정립
4단계  [캡슐화-결제]   Users.creditCoins/debitCoins + PetCoinEscrow.release/refund
5단계  [캡슐화-제재]   Users.suspend/ban/activate
6단계  [캡슐화-공통]   BaseTimeEntity.softDelete/restore — 전 도메인 소프트 삭제 일괄 통일
7단계  [나머지]        care/board/chat/location 상태 전환 메서드화, 레거시 메서드 제거
```

각 단계 완료 기준: `@Setter` 해당 필드 제거 → 서비스 직접 set 제거 → 엔티티 메서드 내 불변조건 검증 → `./gradlew test` 통과

---

## 상세 항목

---

### [0단계] 버그: `applySanctionFromReport` SUSPEND_USER → addBan 호출

| 항목 | 내용 |
| ---- | ---- |
| 파일 | `UserSanctionService.java:210` |
| 심각도 | 🔴 운영 버그 |

**현황**:

```java
case SUSPEND_USER -> addBan(userId, reason, adminId, reportId); // 정지는 영구 차단으로 처리
```

관리자가 신고 처리에서 "일시 정지"를 선택해도 실제로는 영구 차단이 적용된다.

**개선 방향** (의도 확인 후 선택):

- 일시 정지가 의도라면: `addSuspension(userId, reason, adminId, reportId, AUTO_SUSPENSION_DAYS)`
- 영구 차단이 의도라면: `ReportActionType.SUSPEND_USER` → `BAN_USER` rename

---

### [1단계] viewCount 원자성 없음 + BoardViewLog race condition

| 항목 | 내용 |
| ---- | ---- |
| 파일 | `BoardService.java:551-553`, `BoardService.java:556-580` |
| 심각도 | 🔴 동시성 |

**현황**:

```java
// BoardService.incrementViewCount — Read-Modify-Write, 비관적 락 없음
Integer current = board.getViewCount();
board.setViewCount((current == null ? 0 : current) + 1);
boardRepository.save(board);

// BoardService.shouldIncrementView — exists → save → increment 가 non-atomic
boolean alreadyViewed = boardViewLogRepository.existsByBoardAndUser(board, viewer);
// ← 동시 요청 두 개가 여기서 모두 false 반환 가능
if (!alreadyViewed) {
    boardViewLogRepository.save(log);   // ViewLog 이중 저장
    return true;                        // incrementViewCount 두 번 호출
}
```

`existsByBoardAndUser` 체크와 `BoardViewLog` insert가 원자적이지 않아 두 요청이 동시에 들어오면 중복 조회 기록 및 조회수 이중 증가 가능.

**비교**: `warningCount`는 이미 `usersRepository.incrementWarningCount(userId)` 원자적 쿼리를 사용 중.

**개선**:

`BoardViewLog.java:15`에 `(board_id, user_id)` UNIQUE constraint가 이미 존재한다.
"UNIQUE 추가"가 아니라, 동시 INSERT 경합을 예외 없이 처리하는 **원자 insert-if-absent**가 목표다.
`save()` + `catch DataIntegrityViolationException` 패턴은 같은 영속성 컨텍스트에서 예외를 흡수한 뒤 계속 사용하는 구조라 Hibernate 명세상 안전하지 않다.
MySQL `INSERT IGNORE` 네이티브 쿼리를 사용해 예외 자체를 발생시키지 않고 affected row count로 성공 여부를 판단한다.

```java
// SpringDataJpaBoardViewLogRepository — INSERT IGNORE 네이티브 쿼리
@Modifying @Transactional
@Query(value = "INSERT IGNORE INTO board_view_log (board_id, user_id, viewed_at) VALUES (:boardId, :userId, NOW())", nativeQuery = true)
int insertIgnore(@Param("boardId") Long boardId, @Param("userId") Long userId);

// BoardService.shouldIncrementView — exists 체크 + catch 제거, insertIgnore 단일 호출
private boolean shouldIncrementView(Board board, Long viewerId) {
    if (viewerId == null) return true;
    Users viewer = usersRepository.findById(viewerId).orElse(null);
    if (viewer == null) return true;
    return boardViewLogRepository.insertIgnore(board.getIdx(), viewer.getIdx()) > 0;
}

// BoardRepository — COALESCE로 null 방어
@Modifying
@Query("UPDATE Board b SET b.viewCount = COALESCE(b.viewCount, 0) + 1 WHERE b.idx = :idx")
void incrementViewCount(@Param("idx") Long idx);
```

---

### [2단계] likeCount / dislikeCount / commentCount 원자성 없음

| 항목 | 내용 |
| ---- | ---- |
| 파일 | `ReactionService.java:209-210`, `CommentService.java:402-412` |
| 심각도 | 🔴 동시성 |

**현황**:

```java
// ReactionService.updateBoardReactionCounts — Read-Modify-Write
board.setLikeCount(likeCount);       // 비관적 락 없이 Board 조회 후 set
board.setDislikeCount(dislikeCount);

// CommentService.incrementBoardCommentCount — Read-Modify-Write
Integer currentCount = board.getCommentCount() != null ? board.getCommentCount() : 0;
board.setCommentCount(currentCount + 1);
```

viewCount와 동일한 패턴. 세 카운터 모두 비관적 락 없이 Read-Modify-Write.

**BoardReaction race condition 추가**:

`BoardReaction.java:24`에 `(board_idx, user_idx)` UNIQUE constraint가 이미 존재한다.
`ReactionService.reactToBoard`의 `findByBoardAndUser` → 없으면 `save` 패턴은 BoardViewLog와 동일하게
동시 요청 시 경합이 발생한다.
1단계와 동일하게 `INSERT IGNORE` 네이티브 쿼리를 사용하고, `inserted == 0`이면 현재 상태를 그대로 반환한다.
`catch DataIntegrityViolationException` 패턴은 사용하지 않는다.

**개선**: 원자적 UPDATE 쿼리로 대체 (COALESCE + 음수 방어 포함)

```java
// BoardRepository
// 증가: COALESCE로 null 방어
@Modifying
@Query("UPDATE Board b SET b.likeCount = COALESCE(b.likeCount, 0) + :delta WHERE b.idx = :idx")
void adjustLikeCount(@Param("idx") Long idx, @Param("delta") int delta);

@Modifying
@Query("UPDATE Board b SET b.dislikeCount = COALESCE(b.dislikeCount, 0) + :delta WHERE b.idx = :idx")
void adjustDislikeCount(@Param("idx") Long idx, @Param("delta") int delta);

// 감소: 0 미만 방지 (서비스의 Math.max(0, ...) 대체)
@Modifying
@Query("UPDATE Board b SET b.commentCount = GREATEST(0, COALESCE(b.commentCount, 0) + :delta) WHERE b.idx = :idx")
void adjustCommentCount(@Param("idx") Long idx, @Param("delta") int delta);
```

**토글 패턴 대응** (likeCount/dislikeCount):

- 새 반응: `adjustLikeCount(+1)` 또는 `adjustDislikeCount(+1)`
- 반응 타입 변경: `adjust기존(-1)` + `adjust새로운(+1)` (두 쿼리, 같은 TX)
- 반응 삭제(토글): `adjustLikeCount(-1)` 또는 `adjustDislikeCount(-1)` (GREATEST로 0 미만 방지)

---

### [3단계] 게시글 삭제 시 댓글 벌크 UPDATE + commentCount 정책

| 항목 | 내용 |
| ---- | ---- |
| 파일 | `BoardService.java:346-350` |
| 심각도 | 🔴 N+1 |

**현황**:

```java
// BoardService.deleteBoard — LAZY comments 루프 → SELECT 1 + UPDATE N
if (board.getComments() != null) {
    board.getComments().forEach(c -> {
        c.setStatus(ContentStatus.DELETED);
        c.setIsDeleted(true);
        c.setDeletedAt(LocalDateTime.now());
    });
}
```

**개선**:

```java
// CommentRepository — 벌크 UPDATE
@Modifying
@Query("UPDATE Comment c SET c.isDeleted = true, c.deletedAt = :now, c.status = 'DELETED' " +
       "WHERE c.board.idx = :boardIdx AND c.isDeleted = false")
void softDeleteByBoardIdx(@Param("boardIdx") Long boardIdx, @Param("now") LocalDateTime now);
```

**commentCount 정책 결정 필요**:

게시글 삭제(소프트) 시 `commentCount`를 어떻게 처리할지 정책 명시 필요.

- 옵션 A: 게시글 자체가 소프트 삭제되므로 commentCount를 건드리지 않음 (조회 불가이므로 무의미)
  - **단, 복구 시 stale count 발생**: `BoardService.restoreBoard`는 게시글만 복구하고 댓글은 복구하지 않음 (`BoardService.java:606` 참고). 댓글 벌크 삭제 후 count를 그대로 두면 복구 후 commentCount가 실제 활성 댓글 수와 틀어짐. 복구 경로에서 `COUNT(active comments)` 재집계 또는 댓글 일괄 복구 여부를 함께 결정해야 함.
- 옵션 B: 삭제 시 `adjustCommentCount(-activeCommentCount)` 처리, 복구 시 `+activeCommentCount` 재집계

---

### [4단계] Users.petCoinBalance / PetCoinEscrow 상태 — 캡슐화

| 항목 | 내용 |
| ---- | ---- |
| 파일 | `PetCoinService.java:57,111,160,210`, `PetCoinEscrowService.java:112-113,154-155` |
| 심각도 | 🔴 불변조건 미보호 |

**현황**:

`PetCoinService` 4개 메서드 모두 `currentUser.setPetCoinBalance(balanceAfter)` 직접 호출.
`deductCoins`에만 음수 방지 체크가 있고 나머지 3개(`charge/payout/refund`)에는 없음.

`PetCoinEscrowService.releaseToProvider/refundToRequester`에서 HOLD 검증은 서비스가 하고 상태 변경도 서비스가 직접 set.

**개선**:

```java
// Users.java
public void creditCoins(int amount) {
    this.petCoinBalance += amount;
}
public void debitCoins(int amount) {
    if (this.petCoinBalance < amount) throw InsufficientBalanceException.of(petCoinBalance, amount);
    this.petCoinBalance -= amount;
}

// PetCoinEscrow.java
public void release() {
    if (this.status != EscrowStatus.HOLD) throw new IllegalStateException("HOLD 상태만 지급 가능");
    this.status = EscrowStatus.RELEASED;
    this.releasedAt = LocalDateTime.now();
}
public void refund() {
    if (this.status != EscrowStatus.HOLD) throw PaymentConflictException.holdStatusRequiredForRefund();
    this.status = EscrowStatus.REFUNDED;
    this.refundedAt = LocalDateTime.now();
}
```

---

### [5단계] Users 제재 상태 전환 — 캡슐화

| 항목 | 내용 |
| ---- | ---- |
| 파일 | `UserSanctionService.java:106-108,138-140,152-155,184-186` |
| 심각도 | 🟡 불변조건 분산 |

**현황**: `user.setStatus(SUSPENDED)` + `user.setSuspendedUntil(endsAt)` 쌍이 4개 메서드에서 각각 반복.

**개선**:

```java
// Users.java
public void suspend(LocalDateTime until) {
    this.status = UserStatus.SUSPENDED;
    this.suspendedUntil = until;
}
public void ban() {
    this.status = UserStatus.BANNED;
    this.suspendedUntil = null;
}
public void activate() {
    this.status = UserStatus.ACTIVE;
    this.suspendedUntil = null;
}
```

---

### [6단계] 소프트 삭제 쌍 — 전 도메인 일괄 통일

| 항목 | 내용 |
| ---- | ---- |
| 심각도 | 🟡 정합성 위험 (한 줄 누락 시 isDeleted=true, deletedAt=null) |

`setIsDeleted(true)` + `setDeletedAt(now)` + (Board의 경우) `setStatus(DELETED)` 세 필드가 항상 함께 변경돼야 하는데 서비스에서 직접 set.

**주의**: `BaseTimeEntity.java:17`는 `createdAt/updatedAt`만 갖고 있고 `isDeleted/deletedAt`은 각 엔티티에 개별 선언되어 있다. `softDelete()`를 BaseTimeEntity에 직접 추가할 수 없음.

**두 가지 구현 경로 중 선택 필요**:

- 경로 A — 공통 추상 클래스: `isDeleted/deletedAt`을 `BaseTimeEntity`로 끌어올림 (스키마 영향 없음, 이미 모든 테이블에 컬럼 존재). `softDelete()/restore()` BaseTimeEntity에 추가.
- 경로 B — `SoftDeletable` 인터페이스: 각 엔티티에 인터페이스를 구현하고, 엔티티별로 `softDelete()/restore()` 메서드를 선언. BaseTimeEntity는 건드리지 않음.

```java
// 경로 A: BaseTimeEntity에 필드 이동 후
public void softDelete() {
    this.isDeleted = true;
    this.deletedAt = LocalDateTime.now();
}
public void restore() {
    this.isDeleted = false;
    this.deletedAt = null;
}

// 경로 B: 인터페이스 방식
public interface SoftDeletable {
    void softDelete();
    void restore();
}
// 각 엔티티가 구현
```

`Board`처럼 `status` 필드도 함께 변경해야 하는 엔티티는 `softDelete()` 오버라이드 또는 별도 메서드로 처리.

| 도메인 | 엔티티 |
| ------ | ------ |
| board | `Board`, `Comment`, `MissingPetBoard`, `MissingPetComment` |
| care | `CareRequest`, `CareRequestComment` |
| chat | `ChatMessage`, `ConversationParticipant`, `Conversation` |
| location | `LocationService`, `LocationServiceReview` |
| meetup | `Meetup` |
| user | `Users`, `Pet` |

---

### [7단계] 상태 전환 메서드화 + 레거시 정리

**상태 전환 직접 set 목록**:

| 엔티티 | 전환 메서드(안) | 현재 직접 set 위치 |
| ------ | -------------- | ------------------ |
| `PetCoinEscrow` | `release()`, `refund()` | `PetCoinEscrowService.java:112-113,154-155` |
| `CareRequest` | `complete()`, `cancel()`, `delete()`, `restore()` | `CareRequestService.java:289-291,335-338,389-391` |
| `CareApplication` | `accept()`, `reject()` | `ConversationService` 내부 |
| `Conversation` | `close()` | `ConversationService` 내부 |
| `ConversationParticipant` | `leave()`, `activate()` | `ConversationService` 내부 |
| `Board` | `delete()`, `restore()`, `updateStatus(status)` | `BoardService.java:342-344,590-594,607-612` |
| `Comment` | `delete()`, `restore()` | `CommentService` 내부, `BoardService.java:347-350` |
| `MissingPetBoard` | `updateStatus(status)`, `delete()` | `MissingPetBoardService` 내부 |
| `Report` | `handle(action, admin, note)` | `ReportService` 내부 |

**레거시 정리**:

| 위치 | 문제 |
| ---- | ---- |
| `BoardService.getAdminBoardsWithPaging` (line 105-189) | 전체 로드 후 메모리 필터링 — `getAdminBoardsWithPagingOptimized` 있으나 구버전 미제거 |
| `BoardService.getAdminBoardsWithPagingOptimized` (line 657) | `%keyword%` 양쪽 와일드카드 LIKE — 인덱스 미사용 |

---

## 잘된 점 (변경 필요 없음)

- `ConversationParticipant.incrementUnreadCount/decrementUnreadCount` — 카운트 조작 이미 엔티티 메서드로 캡슐화
- `PetCoinService.findByIdForUpdate` 비관적 락 — Race Condition 방지 구조 이미 갖춰짐
- `usersRepository.incrementWarningCount(userId)` — warningCount는 이미 원자적 쿼리 적용
- `CareRequestRepository.searchWithPaging` FULLTEXT 검색 — `MATCH...AGAINST` 사용

---

## 2026-06-04 리팩토링 시행 후 코드 리뷰 — 잘못된 수정 목록

> 0~7단계 실행 완료 후 전체 리뷰. 아래 항목은 **현재 코드에 남아있는 결함**이다.

---

### 🔴 Critical-1: `shouldIncrementView` / `reactToBoard` — 같은 영속성 컨텍스트에서 UNIQUE 위반을 catch 후 계속 사용하는 구조가 불안전

| 항목 | 내용 |
| ---- | ---- |
| 파일 | `BoardService.java:467-478`, `ReactionService.java:63-75` |
| 의도 | UNIQUE 위반 시 `DataIntegrityViolationException`을 catch → 증가 생략 또는 현재 상태 반환 |
| 실제 동작 | `BoardViewLog.id`가 IDENTITY 전략이라 `save()` 호출 시 Hibernate가 INSERT를 **즉시** 실행한다. 따라서 UNIQUE 위반 예외는 try-catch 안에서 발생한다. **단**, Hibernate 명세는 "예외 발생 후 Session을 즉시 버려라"고 명시한다. 같은 영속성 컨텍스트에서 예외를 catch하고 계속 `boardRepository.incrementViewCount()` 같은 연산을 수행하는 구조는 안전성 보장이 없다. 예외가 간헐적으로 세션 상태를 오염시켜 이후 연산이 실패할 수 있다. |

**근본 원인**: try-catch로 예외를 "흡수"하면 되는 게 아니라, **애초에 예외가 발생하지 않는 방식**으로 바꿔야 한다.

**올바른 수정 — MySQL `INSERT IGNORE` 네이티브 쿼리 (REQUIRES_NEW보다 권장)**

`REQUIRES_NEW`는 커넥션 풀에서 별도 커넥션을 가져오므로 고트래픽 조회수 엔드포인트에 부담이 있다. `INSERT IGNORE`는 예외 없이 affected row count만 반환하므로 세션 오염 문제가 아예 없다.

```java
// SpringDataJpaBoardViewLogRepository.java
@Modifying
@Transactional
@Query(
    value = "INSERT IGNORE INTO board_view_log (board_id, user_id, viewed_at) VALUES (:boardId, :userId, NOW())",
    nativeQuery = true
)
int insertIgnore(@Param("boardId") Long boardId, @Param("userId") Long userId);
```

```java
// BoardService.shouldIncrementView — try-catch 완전 제거
private boolean shouldIncrementView(Board board, Long viewerId) {
    if (viewerId == null) return true;
    Users viewer = usersRepository.findById(viewerId).orElse(null);
    if (viewer == null) return true;
    int inserted = boardViewLogRepository.insertIgnore(board.getIdx(), viewer.getIdx());
    return inserted > 0;  // 1 = 새로 삽입됨, 0 = 이미 존재
}
```

`BoardReaction`, `CommentReaction`도 동일하게 `INSERT IGNORE` 전환 필요. 카운터 조정은 `inserted > 0`일 때만 실행.

---

### 🔴 Critical-2: `reactToBoard` — `INSERT IGNORE` 전환 시 카운터 조정 조건 연동 필요

| 항목 | 내용 |
| ---- | ---- |
| 파일 | `ReactionService.java:63-75` |
| 현재 문제 | Critical-1과 동일한 패턴. `boardReactionRepository.save(reaction)` 후 예외 catch로 처리 시도. |
| 추가 문제 | 예외 catch 후 `getBoardSummary(boardId, userId)` 호출 시도 → 세션이 오염 상태면 이 조회도 실패 가능 |

**올바른 수정**: `BoardReaction` 신규 삽입도 `INSERT IGNORE` 네이티브 쿼리로 전환, `inserted > 0`일 때만 `adjustLikeCount`/`adjustDislikeCount` 실행.

---

### 🟡 Warning-1: `adjustLikeCount` / `adjustDislikeCount` — 음수 방어 없음

| 항목 | 내용 |
| ---- | ---- |
| 파일 | `SpringDataJpaBoardRepository.java:100-106` |
| 문제 | delta=-1, likeCount=0이면 `COALESCE(0,0)+(-1) = -1` → DB에 음수 저장 가능 |
| 비교 | `adjustCommentCount`는 `GREATEST(0, COALESCE(b.commentCount,0)+:delta)` 적용됨 |

**현재 코드**
```sql
UPDATE Board b SET b.likeCount = COALESCE(b.likeCount, 0) + :delta WHERE b.idx = :idx
UPDATE Board b SET b.dislikeCount = COALESCE(b.dislikeCount, 0) + :delta WHERE b.idx = :idx
```

**올바른 수정**
```sql
UPDATE Board b SET b.likeCount = GREATEST(0, COALESCE(b.likeCount, 0) + :delta) WHERE b.idx = :idx
UPDATE Board b SET b.dislikeCount = GREATEST(0, COALESCE(b.dislikeCount, 0) + :delta) WHERE b.idx = :idx
```

---

### 🟡 Warning-2: `releaseToProvider` — 코인 지급 후 상태 검증

| 항목 | 내용 |
| ---- | ---- |
| 파일 | `PetCoinEscrowService.java:95-103` |
| 문제 | `petCoinService.payoutCoins(...)` 실행 후 `escrow.release()` (HOLD 체크 포함) 호출. 비관적 락으로 보호되므로 데이터 불일치는 없으나, "검증 → 실행" 순서가 역전된 구조. `refundToRequester`도 동일 패턴. |

**올바른 순서**
```java
public PetCoinEscrow releaseToProvider(PetCoinEscrow escrow) {
    escrow = escrowRepository.findByIdForUpdate(escrow.getIdx())
            .orElseThrow(() -> new PetCoinEscrowNotFoundException());
    escrow.release();  // 상태 검증 먼저
    petCoinService.payoutCoins(...);  // 그 다음 실행
    statisticsService.recordPayment(...);
    return escrowRepository.save(escrow);
}
```

---

### 🟡 Warning-3: `reactToComment` — CommentReaction race condition 미처리 + `INSERT IGNORE` 전환 대상

| 항목 | 내용 |
| ---- | ---- |
| 파일 | `ReactionService.java:119-126` |
| 문제 | `reactToBoard`에는 (불완전하지만) 처리를 시도했으나 `reactToComment`의 신규 반응 분기에는 아무 처리 없음. Critical-1/2 수정 시 `CommentReaction`도 `INSERT IGNORE` 패턴으로 동시에 전환해야 함. |

---

### 🔴 Critical-3 (누락된 버그): `getBoard()` 조회수 응답이 증가 전 값으로 stale하게 반환됨

| 항목 | 내용 |
| ---- | ---- |
| 파일 | `BoardService.java:157-166` |
| 증상 | `getBoard()` 응답의 `viewCount`가 항상 실제보다 1 낮게 반환됨 |
| 원인 | `incrementViewCount(board)` → `@Modifying` UPDATE 실행 → DB는 6으로 업데이트됨. 단, `@Modifying` 쿼리는 Hibernate 1차 캐시를 갱신하지 않는다. 이후 `mapBoardWithDetails(board)`는 메모리의 `board.viewCount` (여전히 5)를 사용해 응답을 만든다. |

**현재 코드**
```java
// BoardService.java:157-166
Board board = boardRepository.findByIdWithUser(idx)...;  // viewCount=5 로드
if (shouldIncrementView(board, viewerId)) {
    incrementViewCount(board);  // DB → 6, 메모리 board.viewCount = 5 그대로
}
return mapBoardWithDetails(board);  // viewCount: 5 반환 (1 낮은 값)
```

**올바른 수정**: `@Modifying` 쿼리 후 응답에 사용할 viewCount를 메모리에서 직접 +1.

```java
boolean incremented = shouldIncrementView(board, viewerId);
if (incremented) {
    boardRepository.incrementViewCount(board.getIdx());
}
BoardDTO dto = mapBoardWithDetails(board);
if (incremented) {
    dto.setViewCount((board.getViewCount() != null ? board.getViewCount() : 0) + 1);
}
return dto;
```

또는 `@Modifying(clearAutomatically = true)`로 1차 캐시를 무효화 후 재조회 (추가 SELECT 비용 발생).

---

### 🔴 Critical-4 (누락된 버그): `reactToComment()` — 반응 타입 변경 후 `userReaction: null` 잘못 반환

| 항목 | 내용 |
| ---- | ---- |
| 파일 | `ReactionService.java:112-131` |
| 증상 | 사용자가 LIKE → DISLIKE로 반응 변경 시 응답의 `userReaction`이 `null`로 반환됨 |
| 원인 | "change reaction type" 분기에서 `reaction.setReactionType(reactionType)`이 `existing.get()`이 가리키는 **동일 객체**를 변경함. 이후 `toggledOff` 계산 시 `existing.get().getReactionType()`이 이미 새 값(DISLIKE)으로 바뀌어 있으므로 `existing.get().getReactionType() == reactionType`이 true → `toggledOff = true` → `userReaction = null` |

**현재 코드 (버그)**
```java
} else if (existing.isPresent()) {
    CommentReaction reaction = existing.get();
    reaction.setReactionType(reactionType);  // existing.get()도 같은 객체 — 변경됨!
    commentReactionRepository.save(reaction);
}

// toggledOff 계산 시점에 existing.get().getReactionType()은 이미 newType
boolean toggledOff = (existing.isPresent() && existing.get().getReactionType() == reactionType);
// LIKE→DISLIKE 변경 케이스: (true && DISLIKE == DISLIKE) = true → userReaction = null (버그!)
ReactionType userReaction = toggledOff ? null : reactionType;
```

**올바른 수정**: 뮤테이션 전에 이전 타입을 저장

```java
Optional<CommentReaction> existing = commentReactionRepository.findByCommentAndUser(comment, user);
ReactionType previousType = existing.map(CommentReaction::getReactionType).orElse(null);

if (existing.isPresent() && previousType == reactionType) {
    commentReactionRepository.delete(existing.get());
} else if (existing.isPresent()) {
    existing.get().setReactionType(reactionType);
    commentReactionRepository.save(existing.get());
} else { ... }

boolean toggledOff = (previousType == reactionType);  // 뮤테이션 전 값 기준으로 판단
ReactionType userReaction = toggledOff ? null : reactionType;
```

같은 패턴이 `reactToBoard`의 `toggledOff` 계산에도 잠재적으로 있으나, `reactToBoard`는 `previousReactionType`을 별도로 저장하고 있어 해당 버그 없음. `reactToComment`만 해당.

---

### 🟢 Info-1: `BoardService.updateBoardStatus` — 직접 `setStatus` 잔존

| 항목 | 내용 |
| ---- | ---- |
| 파일 | `BoardService.java:491` |
| 문제 | 7단계 리팩토링에서 다른 엔티티 상태 전환은 entity 메서드로 이동했으나, `updateBoardStatus`만 `board.setStatus(status)` 직접 호출 잔존. `Board.changeStatus(ContentStatus)` 메서드 추가로 통일 가능. |

---

### 🟢 Info-2: `ReactionService.reactToBoard` — 반응 변경 분기 직접 `setReactionType`

| 항목 | 내용 |
| ---- | ---- |
| 파일 | `ReactionService.java:60` |
| 문제 | 기존 반응을 다른 타입으로 변경하는 분기에서 `existing.get().setReactionType(reactionType)` 직접 호출. `BoardReaction`에 `changeReactionType(ReactionType)` 메서드가 없어 @Setter 의존 상태. 캡슐화 방향과 불일치. |

---

### 수정 우선순위 요약

| 순서 | 등급 | 항목 | 파일 | 완료 |
| ---- | ---- | ---- | ---- | ---- |
| 1 | 🔴 Critical | BoardViewLog / BoardReaction / CommentReaction INSERT IGNORE 전환 + 카운터 조정 조건 연동 | `BoardService.java`, `ReactionService.java` | ✅ |
| 2 | 🟡 Warning | likeCount / dislikeCount `GREATEST(0, ...)` 음수 방어 | `SpringDataJpaBoardRepository.java` | ✅ |
| 3 | 🔴 Critical | `getBoard()` 조회수 stale 응답 (메모리 viewCount + 1 반영) | `BoardService.java` | ✅ |
| 4 | 🔴 Critical | `reactToComment()` 반응 변경 시 `userReaction: null` 버그 | `ReactionService.java` | ✅ |
| 5 | 🟡 Warning | 에스크로 release/refund — 상태 검증 먼저, 코인 이동 나중으로 순서 변경 | `PetCoinEscrowService.java` | ✅ |
| 6 | 🟢 Info | `updateBoardStatus` 직접 `setStatus` → entity 메서드로 통일 | `BoardService.java` | ✅ |
| 7 | 🟢 Info | `reactToBoard`·`reactToComment` 반응 변경 분기 직접 `setReactionType` | `ReactionService.java` | ✅ |

---

## 2026-06-04 완료 기록

### 실행된 수정 (0~7단계 + 리뷰 후 버그 수정)

| 단계 | 내용 | 주요 변경 파일 |
| ---- | ---- | ------------ |
| 0 | `applySanctionFromReport` SUSPEND_USER → addBan 버그 수정 | `UserSanctionService.java` |
| 1 | viewCount 원자 UPDATE + BoardViewLog INSERT IGNORE 전환 | `SpringDataJpaBoardRepository.java`, `SpringDataJpaBoardViewLogRepository.java`, `BoardService.java` |
| 2 | likeCount / dislikeCount / commentCount 원자 UPDATE (COALESCE + GREATEST) | `SpringDataJpaBoardRepository.java`, `ReactionService.java`, `CommentService.java` |
| 3 | 게시글 삭제 시 댓글 벌크 softDelete + commentCount 정책 정립 | `SpringDataJpaCommentRepository.java`, `BoardService.java` |
| 4 | `Users.creditCoins/debitCoins`, `PetCoinEscrow.release/refund` 캡슐화 | `Users.java`, `PetCoinEscrow.java`, `PetCoinService.java`, `PetCoinEscrowService.java` |
| 5 | `Users.suspend/ban/activate` 제재 메서드화 | `Users.java`, `UserSanctionService.java` |
| 6 | 전 도메인 `softDelete()/restore()` entity 메서드 추가 (13개 엔티티) | `Board`, `Comment`, `CareRequest`, `Conversation` 등 |
| 7 | 상태 전환 메서드화 (`transitionTo`, `accept`, `reject`, `handle`, `close`) + 레거시 제거 | `CareRequest`, `CareApplication`, `Report`, `Conversation`, `BoardService` |
| 리뷰 후 | `getBoard()` stale viewCount 응답 수정 (+1 메모리 반영) | `BoardService.java` |
| 리뷰 후 | `reactToComment()` userReaction null 버그 수정 (previousType 뮤테이션 전 저장) | `ReactionService.java` |
| 리뷰 후 | BoardReaction / CommentReaction INSERT IGNORE 전환 | `SpringDataJpaBoardReactionRepository.java`, `SpringDataJpaCommentReactionRepository.java`, `ReactionService.java` |
| 리뷰 후 | likeCount / dislikeCount 쿼리 GREATEST(0, ...) 추가 | `SpringDataJpaBoardRepository.java` |
| 리뷰 후 | `releaseToProvider` / `refundToRequester` 상태 검증 순서 수정 | `PetCoinEscrowService.java` |

### 잔여 (Info — 다음 기회)

없음. 전체 수정 완료.
