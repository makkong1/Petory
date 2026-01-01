# Missing Pet 도메인 - 트러블슈팅 발생 가능성 분석

## 1. N+1 문제

### 1.1 게시글 목록 조회 시 심각한 N+1 문제 (실제 발생 확인됨) ✅ **해결됨**
**위치**: `MissingPetBoardService.getBoards()`

**문제점**: ✅ **실제 SQL 로그에서 확인됨** → ✅ **해결 완료**
- 게시글 목록 조회 후 각 게시글마다 댓글과 파일을 개별 조회
- `MissingPetConverter.toBoardDTO()`에서 `board.getComments()` 호출 시 지연 로딩 발생

**실제 SQL 로그 분석**:
```
Line 857: 게시글 목록 조회 (1번 쿼리) - JOIN FETCH로 사용자 정보 포함 ✅
  SELECT mpb1_0.*, u1_0.* FROM missing_pet_board mpb1_0 
  JOIN users u1_0 ON u1_0.idx=mpb1_0.user_idx 
  WHERE mpb1_0.is_deleted=0 AND u1_0.is_deleted=0 AND u1_0.status='ACTIVE' 
  ORDER BY mpb1_0.created_at DESC

Line 858: 첫 번째 게시글의 댓글 조회 (N번 쿼리) ❌
  SELECT c1_0.*, u1_0.* FROM missing_pet_comment c1_0 
  LEFT JOIN users u1_0 ON u1_0.idx=c1_0.user_idx 
  WHERE c1_0.board_idx=?

Line 859: 첫 번째 게시글의 파일 조회 (N번 쿼리) ❌
  SELECT af1_0.* FROM file af1_0 
  WHERE af1_0.target_type=? AND af1_0.target_idx=?

Line 860: 두 번째 게시글의 댓글 조회 (N번 쿼리) ❌
Line 861: 두 번째 게시글의 파일 조회 (N번 쿼리) ❌
Line 862: 세 번째 게시글의 댓글 조회 (N번 쿼리) ❌
Line 863: 세 번째 게시글의 파일 조회 (N번 쿼리) ❌
```

**쿼리 수 계산**:
- 게시글 3개 조회 시: 1 (게시글) + 3 (댓글) + 3 (파일) = **7번 쿼리**
- 게시글 10개 조회 시: 1 + 10 + 10 = **21번 쿼리**
- 게시글 100개 조회 시: 1 + 100 + 100 = **201번 쿼리** ❌

**원인 분석**:
```java
// MissingPetBoardService.getBoards()
public List<MissingPetBoardDTO> getBoards(MissingPetStatus status) {
    List<MissingPetBoard> boards = ...;  // 댓글과 파일은 JOIN FETCH 없음
    return boards.stream()
            .map(this::mapBoardWithAttachments)  // 각 게시글마다 호출
            .collect(Collectors.toList());
}

// MissingPetConverter.toBoardDTO()
public MissingPetBoardDTO toBoardDTO(MissingPetBoard board) {
    List<MissingPetCommentDTO> commentDTOs = board.getComments() == null  // 지연 로딩 발생!
            ? Collections.emptyList()
            : board.getComments().stream()...
}
```

**해결 완료**: ✅
- **1단계: 댓글 N+1 해결** - Repository 쿼리에 `LEFT JOIN FETCH b.comments c` 및 `LEFT JOIN FETCH c.user cu` 추가
- **2단계: 파일 N+1 해결** - `getAttachmentsBatch()` 메서드로 게시글 ID 목록을 한 번에 조회
- **성능 개선 결과**: 207개 쿼리 → 3개 쿼리 (98.5% 감소), 571ms → 79ms (86% 감소)
- 자세한 내용은 `docs/domains/missing-pet.md` 7.4, 7.5 섹션 참고

### 1.2 게시글 단건 조회 시 댓글 및 파일 N+1 문제 (실제 발생 확인됨) ✅ **해결됨**
**위치**: `MissingPetBoardService.getBoard()`

**문제점**: ✅ **실제 SQL 로그에서 확인됨** → ✅ **해결 완료**
- `getBoard()`에서 `mapBoardWithAttachments()`를 호출하지만, 댓글과 파일은 별도 조회
- `MissingPetConverter.toBoardDTO()`에서 `board.getComments()` 호출 시 지연 로딩 발생
- `mapBoardWithAttachments()`에서 `attachmentFileService.getAttachments()` 개별 호출

**실제 SQL 로그 분석**:
```
Line 1025: 사용자 조회 (인증 관련)
  SELECT u1_0.* FROM users u1_0 WHERE u1_0.id=?

Line 1026: 게시글 단건 조회 (1번 쿼리) ✅
  SELECT mpb1_0.*, u1_0.* 
  FROM missing_pet_board mpb1_0 
  JOIN users u1_0 ON u1_0.idx=mpb1_0.user_idx 
  WHERE mpb1_0.idx=? 
    AND mpb1_0.is_deleted=0 
    AND u1_0.is_deleted=0 
    AND u1_0.status='ACTIVE'

Line 1027: 댓글 조회 (추가 쿼리) ❌
  SELECT c1_0.*, u1_0.* 
  FROM missing_pet_comment c1_0 
  LEFT JOIN users u1_0 ON u1_0.idx=c1_0.user_idx 
  WHERE c1_0.board_idx=?

Line 1028: 파일 조회 (추가 쿼리) ❌
  SELECT af1_0.* 
  FROM file af1_0 
  WHERE af1_0.target_type=? AND af1_0.target_idx=?
```

**쿼리 수 분석**:
- 게시글 단건 조회 시: 1 (게시글) + 1 (댓글) + 1 (파일) = **3번 쿼리**
- 단건 조회임에도 불구하고 총 3번의 쿼리 발생

**현재 코드**:
```java
public MissingPetBoardDTO getBoard(Long id) {
    MissingPetBoard board = boardRepository.findByIdWithUser(id)  // 댓글 JOIN FETCH 없음
            .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));
    return mapBoardWithAttachments(board);  // 댓글과 파일을 별도 조회
}
```

**해결 완료**: ✅
- 게시글 목록 조회와 동일하게 JOIN FETCH 및 배치 조회 적용
- 자세한 내용은 `docs/domains/missing-pet.md` 7.4, 7.5 섹션 참고

### 1.3 댓글 조회 시 게시글 정보 N+1 문제
**위치**: `MissingPetCommentRepository.findByBoardAndIsDeletedFalseOrderByCreatedAtAsc()`

**현재 상태**: ✅ 이미 `JOIN FETCH mc.user u`로 사용자 정보는 함께 조회
- 게시글 정보는 필요 없을 수 있으나, 만약 필요하다면 추가 조인 필요

## 2. 트랜잭션 및 동시성 문제

### 2.1 게시글 삭제 시 댓글 Soft Delete 동시성 문제
**위치**: `MissingPetBoardService.deleteBoard()`

**문제점**:
```java
if (board.getComments() != null) {
    for (MissingPetComment c : board.getComments()) {
        c.setIsDeleted(true);
        c.setDeletedAt(java.time.LocalDateTime.now());
    }
}
```
- `board.getComments()`는 영속성 컨텍스트에 로드된 댓글만 포함
- 트랜잭션 중간에 다른 사용자가 댓글을 추가하면 해당 댓글은 삭제되지 않음
- `orphanRemoval = true` 설정과 충돌 가능성

**해결 방안**:
- Repository를 통해 직접 댓글을 조회하여 삭제 처리
- 또는 `@Query`를 사용하여 한 번에 업데이트

### 2.2 댓글 추가 시 수동 리스트 관리
**위치**: `MissingPetBoardService.addComment()`

**문제점**:
```java
if (board.getComments() != null) {
    board.getComments().add(saved);
}
```
- `@OneToMany` 관계에서 양방향 연관관계를 수동으로 관리
- `cascade = CascadeType.ALL`과 `orphanRemoval = true` 설정으로 인해 예상치 못한 삭제 가능성
- 영속성 컨텍스트에 `board.getComments()`가 로드되지 않은 경우 `null` 체크만으로는 부족

**해결 방안**:
- `MissingPetComment` 엔티티에 `setBoard()` 메서드 호출로 양방향 연관관계 관리
- 또는 단방향 관계로 변경 고려

### 2.3 게시글 수정 시 사용자 정보 조회
**위치**: `MissingPetBoardService.updateBoard()`

**문제점**:
```java
MissingPetBoard board = boardRepository.findById(id)  // JOIN FETCH 없음
Users user = board.getUser();  // 지연 로딩 발생 가능
```
- `findById()`는 기본 메서드로 `JOIN FETCH` 없음
- `board.getUser()` 호출 시 지연 로딩 발생 가능 (트랜잭션 범위 내에서는 문제 없으나, 성능 이슈)

**해결 방안**:
- `findByIdWithUser()` 메서드 사용 또는 `@EntityGraph` 활용

## 3. 데이터 일관성 문제

### 3.1 위치 정보 타입 불일치
**위치**: `MissingPetBoard` (BigDecimal) vs `MissingPetComment` (Double)

**문제점**:
- `MissingPetBoard`: `BigDecimal latitude, longitude` (precision = 15, scale = 12)
- `MissingPetComment`: `Double latitude, longitude`
- 타입 불일치로 인한 정밀도 손실 가능성
- 위치 기반 검색 시 타입 변환 필요

**해결 방안**:
- `MissingPetComment`도 `BigDecimal`로 통일
- 또는 DTO 레벨에서만 `Double` 사용하고 엔티티는 `BigDecimal` 유지

### 3.2 Soft Delete와 Cascade 설정 충돌
**위치**: `MissingPetBoard` 엔티티

**문제점**:
```java
@OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
private List<MissingPetComment> comments;
```
- `orphanRemoval = true`는 실제 삭제를 의미
- Soft Delete와 함께 사용 시 혼란 가능성
- 게시글 삭제 시 댓글이 실제로 삭제될 수 있음

**해결 방안**:
- Soft Delete만 사용한다면 `orphanRemoval = false`로 변경
- 또는 Cascade 제거 후 수동 관리

### 3.3 삭제된 게시글의 댓글 조회
**위치**: `MissingPetBoardService.getComments()`

**문제점**:
```java
public List<MissingPetCommentDTO> getComments(Long boardId) {
    MissingPetBoard board = boardRepository.findById(boardId)  // 삭제된 게시글도 조회 가능
    List<MissingPetComment> comments = commentRepository.findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(board);
}
```
- 삭제된 게시글의 댓글도 조회 가능
- `board.getIsDeleted()` 체크 없음

**해결 방안**:
- 게시글 삭제 여부 확인 추가
- 또는 Repository에서 삭제되지 않은 게시글만 조회

## 4. 권한 및 보안 문제

### 4.1 게시글 수정/삭제 권한 검증 부족
**위치**: `MissingPetBoardService.updateBoard()`, `deleteBoard()`

**문제점**:
- 이메일 인증만 확인하고, 작성자 본인인지 확인하지 않음
- 다른 사용자가 다른 사용자의 게시글을 수정/삭제할 수 있음

**현재 코드**:
```java
@PutMapping("/{id}")
public ResponseEntity<MissingPetBoardDTO> updateBoard(
        @PathVariable Long id,
        @RequestBody MissingPetBoardDTO request) {
    // request.getUserId()와 게시글 작성자 비교 없음
}
```

**해결 방안**:
- `@PreAuthorize` 또는 서비스 레벨에서 작성자 확인
- `SecurityContext`에서 현재 사용자 정보 추출하여 비교

### 4.2 댓글 삭제 권한 검증 부족
**위치**: `MissingPetBoardService.deleteComment()`

**문제점**:
- 댓글 작성자 확인 없이 삭제 가능
- 다른 사용자가 다른 사용자의 댓글을 삭제할 수 있음

**해결 방안**:
- 댓글 작성자 또는 게시글 작성자만 삭제 가능하도록 권한 검증 추가

### 4.3 실종제보 채팅 시작 권한 검증
**위치**: `MissingPetBoardController.startMissingPetChat()`

**문제점**:
```java
@PostMapping("/{boardIdx}/start-chat")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<ConversationDTO> startMissingPetChat(
        @PathVariable Long boardIdx,
        @RequestParam Long witnessId) {
    // witnessId가 실제로 목격자인지 확인 없음
    // witnessId가 게시글 작성자와 다른지 확인 없음
}
```
- `witnessId`가 실제로 해당 게시글에 댓글을 작성한 사용자인지 확인 없음
- 게시글 작성자와 목격자가 같은 경우 처리 없음

**해결 방안**:
- `witnessId`가 해당 게시글에 댓글을 작성한 사용자인지 확인
- 게시글 작성자와 목격자가 다른지 확인

## 5. 예외 처리 문제

### 5.1 일관성 없는 예외 메시지
**위치**: 전체 서비스

**문제점**:
- `IllegalArgumentException`을 일반적인 예외로 사용
- "Missing pet board not found"와 "User not found" 등 일관성 없는 메시지
- 클라이언트가 구분하기 어려움

**해결 방안**:
- 커스텀 예외 클래스 생성 (`MissingPetBoardNotFoundException`, `UserNotFoundException` 등)
- `@ControllerAdvice`에서 일관된 에러 응답 처리

### 5.2 파일 첨부 실패 시 롤백 처리
**위치**: `MissingPetBoardService.createBoard()`, `updateBoard()`, `addComment()`

**문제점**:
```java
MissingPetBoard saved = boardRepository.save(board);
if (dto.getImageUrl() != null) {
    attachmentFileService.syncSingleAttachment(...);  // 실패 시 예외 발생?
}
```
- 파일 첨부 실패 시 게시글/댓글은 이미 저장됨
- 트랜잭션 롤백 여부 불명확

**해결 방안**:
- `syncSingleAttachment()`의 예외 처리 확인
- 필요 시 트랜잭션 롤백 보장

## 6. 성능 문제

### 6.1 게시글 목록 조회 시 파일 조회 N+1 (실제 발생 확인됨) ✅ **해결됨**
**위치**: `MissingPetBoardService.getBoards()`

**문제점**: ✅ **실제 SQL 로그에서 확인됨** → ✅ **해결 완료**
```java
return boards.stream()
        .map(this::mapBoardWithAttachments)  // 각 게시글마다 파일 조회
        .collect(Collectors.toList());
```
- 각 게시글마다 `attachmentFileService.getAttachments()` 호출
- 게시글 수만큼 추가 쿼리 발생
- **실제 로그**: Line 859, 861, 863에서 각 게시글마다 파일 조회 쿼리 발생

**해결 완료**: ✅
- `getAttachmentsBatch()` 메서드로 게시글 ID 목록을 한 번에 조회하여 N+1 문제 해결
- **효과**: 103개 쿼리 → 1개 쿼리 (배치 조회, IN 절 사용)
- 자세한 내용은 `docs/domains/missing-pet.md` 7.4, 7.5 섹션 참고

### 6.2 댓글 목록 조회 시 파일 조회 N+1 ✅ **해결됨**
**위치**: `MissingPetBoardService.getComments()`

**문제점**: ✅ **해결 완료**
- 각 댓글마다 `attachmentFileService.getAttachments()` 호출
- 댓글 수만큼 추가 쿼리 발생

**해결 완료**: ✅
- `mapCommentWithAttachments()`에서 배치 조회 방식으로 최적화
- 자세한 내용은 `docs/domains/missing-pet.md` 7.4, 7.5 섹션 참고

### 6.3 위치 기반 검색 미구현
**위치**: 문서에는 언급되어 있으나 구현 없음

**문제점**:
- 문서에 "위치 기반 검색 (반경 내)" 기능이 언급되어 있으나
- 실제 API 엔드포인트나 서비스 메서드가 없음

**해결 방안**:
- 위치 기반 검색 기능 구현
- 또는 문서에서 제거

## 7. 데이터 검증 문제

### 7.1 필수 필드 검증 부족
**위치**: `MissingPetBoardService.createBoard()`

**문제점**:
- `title`은 `@Column(nullable = false)`이지만 서비스 레벨 검증 없음
- `latitude`, `longitude` 범위 검증 없음 (-90 ~ 90, -180 ~ 180)
- `lostDate`가 미래 날짜인지 확인 없음

**해결 방안**:
- `@Valid` 및 Bean Validation 사용
- 커스텀 검증 로직 추가

### 7.2 상태 전환 검증 부족
**위치**: `MissingPetBoardService.updateStatus()`

**문제점**:
```java
public MissingPetBoardDTO updateStatus(Long id, MissingPetStatus status) {
    MissingPetBoard board = boardRepository.findById(id)...
    board.setStatus(status);  // 어떤 상태로든 변경 가능
}
```
- 상태 전환 규칙 없음 (예: RESOLVED → MISSING 불가능해야 함)
- 권한 검증 없음 (작성자만 변경 가능한지?)

**해결 방안**:
- 상태 전환 규칙 정의 및 검증 로직 추가
- 권한 검증 추가

## 8. 알림 발송 문제

### 8.1 알림 발송 실패 시 처리
**위치**: `MissingPetBoardService.addComment()`

**문제점**:
```java
notificationService.createNotification(...);  // 실패 시?
```
- 알림 발송 실패 시 댓글은 이미 저장됨
- 트랜잭션 롤백 여부 불명확
- 알림 발송은 비동기로 처리되어야 할 수도 있음

**해결 방안**:
- 알림 발송을 비동기로 처리 (`@Async`)
- 또는 알림 발송 실패 시에도 댓글은 저장되도록 처리 (트랜잭션 분리)

## 9. 엔티티 관계 문제

### 9.1 댓글 추가 시 양방향 관계 관리
**위치**: `MissingPetBoardService.addComment()`

**문제점**:
```java
MissingPetComment saved = commentRepository.save(comment);
if (board.getComments() != null) {
    board.getComments().add(saved);  // 수동 관리
}
```
- JPA 양방향 관계 관리 규칙 위반
- `comment.setBoard(board)`는 이미 Builder에서 설정되었지만, 반대편 리스트 업데이트는 수동

**해결 방안**:
- `MissingPetComment` 엔티티에 `setBoard()` 메서드에서 양방향 관계 관리
- 또는 단방향 관계로 변경

## 10. API 설계 문제

### 10.1 상태 변경 API 설계
**위치**: `MissingPetBoardController.updateStatus()`

**문제점**:
```java
@PatchMapping("/{id}/status")
public ResponseEntity<MissingPetBoardDTO> updateStatus(
        @PathVariable Long id,
        @RequestBody Map<String, String> body) {
    String statusValue = body.get("status");
    MissingPetStatus status = MissingPetStatus.valueOf(statusValue);  // 예외 처리 없음
}
```
- `valueOf()` 실패 시 `IllegalArgumentException` 발생
- DTO 대신 `Map` 사용으로 타입 안정성 부족

**해결 방안**:
- DTO 클래스 사용
- 예외 처리 개선

### 10.2 실종제보 채팅 시작 API 설계
**위치**: `MissingPetBoardController.startMissingPetChat()`

**문제점**:
- `witnessId`를 쿼리 파라미터로 받음
- RESTful하지 않은 설계
- `witnessId` 검증 없음

**해결 방안**:
- Request Body로 변경 또는 경로 변수로 변경
- `witnessId` 검증 로직 추가

## 11. 실제 SQL 로그 분석 (실종제보 전체 조회)

### 11.1 발생한 SQL 쿼리 분석

**API 호출**: `GET /api/missing-pets` (게시글 목록 조회)

**실제 SQL 로그** (게시글 3개 기준):
```
Line 856: 사용자 조회 (초기 로딩 또는 인증 관련)
  SELECT u1_0.* FROM users u1_0 WHERE u1_0.id=?

Line 857: 게시글 목록 조회 (1번) ✅
  SELECT mpb1_0.*, u1_0.* 
  FROM missing_pet_board mpb1_0 
  JOIN users u1_0 ON u1_0.idx=mpb1_0.user_idx 
  WHERE mpb1_0.is_deleted=0 
    AND u1_0.is_deleted=0 
    AND u1_0.status='ACTIVE' 
  ORDER BY mpb1_0.created_at DESC

Line 858: 첫 번째 게시글의 댓글 조회 (N번) ❌
  SELECT c1_0.*, u1_0.* 
  FROM missing_pet_comment c1_0 
  LEFT JOIN users u1_0 ON u1_0.idx=c1_0.user_idx 
  WHERE c1_0.board_idx=?

Line 859: 첫 번째 게시글의 파일 조회 (N번) ❌
  SELECT af1_0.* 
  FROM file af1_0 
  WHERE af1_0.target_type=? AND af1_0.target_idx=?

Line 860: 두 번째 게시글의 댓글 조회 (N번) ❌
  SELECT c1_0.*, u1_0.* 
  FROM missing_pet_comment c1_0 
  LEFT JOIN users u1_0 ON u1_0.idx=c1_0.user_idx 
  WHERE c1_0.board_idx=?

Line 861: 두 번째 게시글의 파일 조회 (N번) ❌
  SELECT af1_0.* 
  FROM file af1_0 
  WHERE af1_0.target_type=? AND af1_0.target_idx=?

Line 862: 세 번째 게시글의 댓글 조회 (N번) ❌
  SELECT c1_0.*, u1_0.* 
  FROM missing_pet_comment c1_0 
  LEFT JOIN users u1_0 ON u1_0.idx=c1_0.user_idx 
  WHERE c1_0.board_idx=?

Line 863: 세 번째 게시글의 파일 조회 (N번) ❌
  SELECT af1_0.* 
  FROM file af1_0 
  WHERE af1_0.target_type=? AND af1_0.target_idx=?
```

### 11.2 문제점 요약

**쿼리 수 분석**:
- 게시글 3개: 1 (게시글) + 3 (댓글) + 3 (파일) = **7번 쿼리**
- 게시글 10개: 1 + 10 + 10 = **21번 쿼리**
- 게시글 100개: 1 + 100 + 100 = **201번 쿼리** ❌

**성능 영향**:
- 게시글 수가 증가할수록 쿼리 수가 선형적으로 증가
- 각 쿼리마다 네트워크 왕복 시간 발생
- 데이터베이스 부하 증가

**원인**:
1. `MissingPetBoardRepository.findAllByOrderByCreatedAtDesc()`에서 댓글과 파일을 JOIN FETCH하지 않음
2. `MissingPetConverter.toBoardDTO()`에서 `board.getComments()` 호출 시 지연 로딩 발생
3. `mapBoardWithAttachments()`에서 각 게시글마다 `attachmentFileService.getAttachments()` 개별 호출

### 11.3 즉시 개선 방안

**1단계: 댓글 JOIN FETCH 추가** (주의: 페이징 불가능)
```java
@Query("SELECT DISTINCT b FROM MissingPetBoard b " +
       "JOIN FETCH b.user u " +
       "LEFT JOIN FETCH b.comments c " +
       "LEFT JOIN FETCH c.user cu " +
       "WHERE b.isDeleted = false " +
       "AND u.isDeleted = false AND u.status = 'ACTIVE' " +
       "ORDER BY b.createdAt DESC")
List<MissingPetBoard> findAllWithCommentsByOrderByCreatedAtDesc();
```

**2단계: 파일 배치 조회 구현**
```java
// AttachmentFileService에 추가 필요
public Map<Long, List<FileDTO>> getAttachmentsBatch(
    FileTargetType targetType, List<Long> targetIds) {
    // IN 절로 한 번에 조회
}
```

**3단계: 서비스 로직 수정**
```java
public List<MissingPetBoardDTO> getBoards(MissingPetStatus status) {
    List<MissingPetBoard> boards = status == null
            ? boardRepository.findAllWithCommentsByOrderByCreatedAtDesc()
            : boardRepository.findByStatusWithCommentsOrderByCreatedAtDesc(status);
    
    // 배치로 파일 조회
    List<Long> boardIds = boards.stream()
            .map(MissingPetBoard::getIdx)
            .collect(Collectors.toList());
    Map<Long, List<FileDTO>> filesByBoardId = attachmentFileService
            .getAttachmentsBatch(FileTargetType.MISSING_PET, boardIds);
    
    // DTO 변환 시 파일 정보 포함
    return boards.stream()
            .map(board -> mapBoardWithAttachments(board, 
                filesByBoardId.getOrDefault(board.getIdx(), Collections.emptyList())))
            .collect(Collectors.toList());
}
```

**예상 개선 효과**:
- 게시글 100개 조회 시: 201번 → **3번 쿼리** (게시글+댓글, 파일 배치, 사용자 정보)
- 성능 향상: 약 **67배** 개선


### 13.3 측정 항목

#### 필수 측정 항목
1. **쿼리 수**: 총 실행된 SQL 쿼리 개수
2. **응답 시간**: API 요청부터 응답까지의 시간 (ms)
3. **게시글 수**: 조회된 게시글 개수

#### 선택 측정 항목
4. **메모리 사용량**: 힙 메모리 사용량 (MB)
5. **DB 연결 시간**: 데이터베이스 연결 소요 시간
6. **쿼리 실행 시간**: 각 쿼리별 실행 시간

### 13.4 측정 시나리오

#### 시나리오 1: 게시글 목록 조회 (10개)
```bash
# API 호출
GET /api/missing-pets

# 측정 항목
- 쿼리 수
- 응답 시간
- 조회된 게시글 수
```

#### 시나리오 2: 게시글 목록 조회 (50개)
```bash
# API 호출
GET /api/missing-pets

# 측정 항목
- 쿼리 수
- 응답 시간
- 조회된 게시글 수
```

#### 시나리오 3: 게시글 목록 조회 (100개)
```bash
# API 호출
GET /api/missing-pets

# 측정 항목
- 쿼리 수
- 응답 시간
- 조회된 게시글 수
```

#### 시나리오 4: 게시글 단건 조회
```bash
# API 호출
GET /api/missing-pets/{id}

# 측정 항목
- 쿼리 수
- 응답 시간
```

### 11.4 게시글 단건 조회 SQL 로그 분석

**API 호출**: `GET /api/missing-pets/{id}` (게시글 단건 조회)

**실제 SQL 로그**:
```
Line 1025: 사용자 조회 (인증 관련)
  SELECT u1_0.* FROM users u1_0 WHERE u1_0.id=?

Line 1026: 게시글 단건 조회 (1번) ✅
  SELECT mpb1_0.*, u1_0.* 
  FROM missing_pet_board mpb1_0 
  JOIN users u1_0 ON u1_0.idx=mpb1_0.user_idx 
  WHERE mpb1_0.idx=? 
    AND mpb1_0.is_deleted=0 
    AND u1_0.is_deleted=0 
    AND u1_0.status='ACTIVE'

Line 1027: 댓글 조회 (추가 쿼리) ❌
  SELECT c1_0.*, u1_0.* 
  FROM missing_pet_comment c1_0 
  LEFT JOIN users u1_0 ON u1_0.idx=c1_0.user_idx 
  WHERE c1_0.board_idx=?

Line 1028: 파일 조회 (추가 쿼리) ❌
  SELECT af1_0.* 
  FROM file af1_0 
  WHERE af1_0.target_type=? AND af1_0.target_idx=?
```

**쿼리 수 분석**:
- 게시글 단건 조회 시: 1 (게시글) + 1 (댓글) + 1 (파일) = **3번 쿼리**
- 단건 조회임에도 불구하고 총 3번의 쿼리 발생

**문제점**:
1. `findByIdWithUser()`에서 댓글을 JOIN FETCH하지 않음
2. `MissingPetConverter.toBoardDTO()`에서 `board.getComments()` 호출 시 지연 로딩 발생
3. `mapBoardWithAttachments()`에서 `attachmentFileService.getAttachments()` 개별 호출

**개선 방안**:
```java
// Repository에 추가
@Query("SELECT DISTINCT b FROM MissingPetBoard b " +
       "JOIN FETCH b.user u " +
       "LEFT JOIN FETCH b.comments c " +
       "LEFT JOIN FETCH c.user cu " +
       "WHERE b.idx = :id AND b.isDeleted = false " +
       "AND u.isDeleted = false AND u.status = 'ACTIVE'")
Optional<MissingPetBoard> findByIdWithComments(@Param("id") Long id);

// Service 수정
public MissingPetBoardDTO getBoard(Long id) {
    MissingPetBoard board = boardRepository.findByIdWithComments(id)  // 댓글 포함
            .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));
    return mapBoardWithAttachments(board);  // 파일만 별도 조회 (폴리모픽 관계)
}
```

## 12. 우선순위별 정리

### 🔴 높은 우선순위 (즉시 수정 필요)
1. **권한 검증 부족** (4.1, 4.2, 4.3) - 보안 이슈
2. **Soft Delete와 Cascade 충돌** (3.2) - 데이터 일관성
3. **삭제된 게시글의 댓글 조회** (3.3) - 비즈니스 로직 오류

### 🟡 중간 우선순위 (개선 권장)
4. **N+1 문제** (1.1, 1.2, 6.1, 6.2, 11) - 성능 이슈 ✅ **해결됨**
   - 게시글 목록 조회 시 댓글 N+1 (1.1) ✅ **해결됨**
   - 게시글 단건 조회 시 댓글/파일 N+1 (1.2) ✅ **해결됨**
   - 게시글 목록 조회 시 파일 N+1 (6.1) ✅ **해결됨**
   - 댓글 목록 조회 시 파일 N+1 (6.2) ✅ **해결됨**
   - **성능 개선 결과**: 207개 쿼리 → 3개 쿼리 (98.5% 감소), 571ms → 79ms (86% 감소)
   - 자세한 내용은 `docs/domains/missing-pet.md` 7.4, 7.5 섹션 참고
5. **위치 정보 타입 불일치** (3.1) - 데이터 정합성
6. **트랜잭션 및 동시성 문제** (2.1, 2.2, 2.3) - 데이터 일관성
7. **예외 처리 개선** (5.1, 5.2) - 사용자 경험

### 🟢 낮은 우선순위 (점진적 개선)
8. **데이터 검증 강화** (7.1, 7.2) - 데이터 품질
9. **알림 발송 최적화** (8.1) - 성능 및 안정성
10. **API 설계 개선** (10.1, 10.2) - 코드 품질

