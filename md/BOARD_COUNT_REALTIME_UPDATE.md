# 게시글 카운트 실시간 업데이트 구현

## 📋 개요

게시글 테이블의 카운트 컬럼들(`view_count`, `like_count`, `comment_count`)을 실시간으로 업데이트하도록 구현했습니다.

## 🔍 문제 분석

### 기존 상태

1. **조회수 (view_count)**: ✅ 이미 실시간 업데이트 구현됨
   - `BoardService.incrementViewCount()`에서 게시글 조회 시마다 업데이트
   - `BoardViewLog`를 사용하여 중복 조회 방지

2. **좋아요 수 (like_count)**: ❌ 업데이트 안 됨
   - `Board` 엔티티의 `likeCount` 필드가 업데이트되지 않음
   - 매번 `boardReactionRepository.countByBoardAndReactionType()`으로 실시간 계산만 수행
   - `BoardPopularityService`가 부정확한 값을 사용할 수 있음

3. **댓글 수 (comment_count)**: ❌ 업데이트 안 됨
   - `CommentService.addComment()`에서 댓글 추가 시 `board.commentCount`를 증가시키지 않음
   - `BoardConverter`에서 `board.getComments().size()`로 계산하거나 기존 값 사용
   - `BoardPopularityService`가 부정확한 값을 사용할 수 있음

### 문제점

- 인기 게시글 계산이 부정확할 수 있음
- 매번 COUNT 쿼리 실행으로 성능 부담
- 데이터 불일치 가능성

## ✅ 선택한 해결 방안: 실시간 업데이트

### 선택 이유

1. **즉시 정확한 데이터 보장**: 반응/댓글이 생성/삭제될 때마다 즉시 카운트 업데이트
2. **인기 게시글 계산 정확성**: `BoardPopularityService`가 정확한 카운트 값을 사용 가능
3. **사용자 경험 향상**: 실시간으로 정확한 카운트 표시
4. **성능 개선**: COUNT 쿼리 대신 단순 업데이트로 성능 향상
5. **배치 작업의 복잡성 제거**: 스케줄러 관리 불필요

### 배치 작업 대신 실시간 업데이트를 선택한 이유

- 배치 작업의 단점:
  - 지연 시간 발생 (배치 실행 전까지 부정확한 값)
  - 스케줄러 관리 복잡성
  - 배치 실패 시 복구 복잡
  - 실시간성이 떨어짐

- 실시간 업데이트의 장점:
  - 즉시 정확한 데이터
  - 트랜잭션 내에서 처리되어 데이터 일관성 보장
  - 구현이 단순하고 유지보수 용이

## 🔧 구현 내용

### 1. 좋아요 수 (like_count) 실시간 업데이트

**파일**: `backend/main/java/com/linkup/Petory/domain/board/service/ReactionService.java`

**변경 사항**:
- `reactToBoard()` 메서드에서 반응 생성/수정/삭제 시 `likeCount` 업데이트
- `updateBoardLikeCount()` 헬퍼 메서드 추가

**로직**:
```java
// 이전 반응이 좋아요였으면 감소
if (previousReactionType == ReactionType.LIKE) {
    currentLikeCount = Math.max(0, currentLikeCount - 1);
}

// 현재 반응이 좋아요면 증가
if (currentReactionType == ReactionType.LIKE) {
    currentLikeCount = currentLikeCount + 1;
}
```

**처리 시나리오**:
- 새로 좋아요 추가: `likeCount +1`
- 좋아요 취소 (토글): `likeCount -1`
- 좋아요 → 싫어요 변경: `likeCount -1` (좋아요 감소)
- 싫어요 → 좋아요 변경: `likeCount +1` (좋아요 증가)
- 싫어요 → 싫어요 취소: `likeCount` 변경 없음

### 2. 댓글 수 (comment_count) 실시간 업데이트

**파일**: `backend/main/java/com/linkup/Petory/domain/board/service/CommentService.java`

**변경 사항**:
- `addComment()`: 댓글 추가 시 `commentCount +1`
- `deleteComment()`: 댓글 삭제 시 `commentCount -1` (삭제된 댓글은 카운트에서 제외)
- `restoreComment()`: 댓글 복구 시 `commentCount +1`
- `incrementBoardCommentCount()`, `decrementBoardCommentCount()` 헬퍼 메서드 추가

**로직**:
```java
// 댓글 추가
private void incrementBoardCommentCount(Board board) {
    Integer currentCount = board.getCommentCount() != null ? board.getCommentCount() : 0;
    board.setCommentCount(currentCount + 1);
}

// 댓글 삭제
private void decrementBoardCommentCount(Board board) {
    Integer currentCount = board.getCommentCount() != null ? board.getCommentCount() : 0;
    board.setCommentCount(Math.max(0, currentCount - 1));
}
```

**처리 시나리오**:
- 댓글 추가: `commentCount +1`
- 댓글 삭제 (소프트 삭제): `commentCount -1`
- 댓글 복구: `commentCount +1`

**주의사항**:
- 삭제된 댓글(`isDeleted = true`)은 카운트에서 제외
- 복구된 댓글은 다시 카운트에 포함

### 3. 조회수 (view_count)

**이미 구현됨**: `BoardService.incrementViewCount()`에서 처리
- 게시글 조회 시마다 업데이트
- `BoardViewLog`로 중복 조회 방지

## 📊 영향받는 부분

### 1. BoardPopularityService

**이전**: 부정확한 카운트 값 사용 가능
```java
int likes = defaultValue(board.getLikeCount());  // 0이거나 오래된 값
int comments = defaultValue(board.getCommentCount());  // 부정확할 수 있음
```

**이후**: 정확한 카운트 값 사용
```java
int likes = defaultValue(board.getLikeCount());  // 정확한 값
int comments = defaultValue(board.getCommentCount());  // 정확한 값
```

### 2. BoardConverter

**이전**: `board.getComments().size()`로 계산하거나 기존 값 사용
```java
Integer aggregatedCommentCount = board.getCommentCount();
if (aggregatedCommentCount == null && board.getComments() != null) {
    aggregatedCommentCount = board.getComments().size();
}
```

**이후**: `board.getCommentCount()` 값이 항상 정확하므로 신뢰 가능

### 3. 성능 개선

**이전**: 매번 COUNT 쿼리 실행
```java
long likeCount = boardReactionRepository.countByBoardAndReactionType(board, ReactionType.LIKE);
```

**이후**: 단순 필드 읽기
```java
board.getLikeCount();  // 이미 업데이트된 값 사용
```

## 🔒 동시성 처리

- 모든 업데이트는 `@Transactional` 내에서 수행되어 데이터 일관성 보장
- JPA의 낙관적 락(Optimistic Lock) 또는 데이터베이스 레벨 락으로 동시성 문제 해결
- 카운트 업데이트는 단순 증가/감소 연산이므로 경쟁 조건(race condition) 위험이 낮음

## 📝 테스트 권장 사항

1. **좋아요 수 테스트**:
   - 좋아요 추가/취소 시 `likeCount` 정확히 업데이트되는지 확인
   - 좋아요 ↔ 싫어요 변경 시 `likeCount` 정확히 업데이트되는지 확인

2. **댓글 수 테스트**:
   - 댓글 추가 시 `commentCount` 증가 확인
   - 댓글 삭제 시 `commentCount` 감소 확인
   - 댓글 복구 시 `commentCount` 증가 확인
   - 삭제된 댓글은 카운트에서 제외되는지 확인

3. **인기 게시글 테스트**:
   - `BoardPopularityService`가 정확한 카운트 값을 사용하는지 확인

4. **동시성 테스트**:
   - 동시에 여러 사용자가 같은 게시글에 좋아요/댓글을 추가할 때 카운트가 정확한지 확인

## 🚀 향후 개선 사항

1. **배치 동기화 작업 (선택사항)**:
   - 기존 데이터의 카운트 값이 부정확할 수 있으므로, 초기 동기화 배치 작업 고려
   - 주기적으로 실제 데이터와 비교하여 동기화하는 배치 작업 추가 가능

2. **모니터링**:
   - 카운트 값의 정확성을 모니터링하는 메트릭 추가
   - 불일치 감지 시 알림 발송

3. **캐시 무효화**:
   - 카운트 업데이트 시 관련 캐시 자동 무효화 (이미 `@CacheEvict`로 처리됨)

## 📅 구현 일자

2025년 (구현 시점)

## 👤 작성자

AI Assistant (Composer)

