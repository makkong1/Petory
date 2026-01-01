# orphanRemoval = true와 Soft Delete 충돌 분석

## 1. 현재 상황

### 1.1 orphanRemoval = true 사용 현황

**전체 코드베이스에서 `orphanRemoval = true`를 사용하는 엔티티**:
- ✅ **MissingPetBoard** (유일)
  - 위치: `backend/main/java/com/linkup/Petory/domain/board/entity/MissingPetBoard.java:95`
  - 설정: `@OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)`
  - 관계: `MissingPetBoard` → `MissingPetComment`

**다른 엔티티들은 `orphanRemoval`을 사용하지 않음**:
- `Board` → `Comment`: `orphanRemoval` 없음
- `CareRequest` → `CareRequestComment`: `orphanRemoval` 없음
- `CareRequest` → `CareApplication`: `orphanRemoval` 없음
- `Pet` → `PetVaccination`: `orphanRemoval` 없음
- `Meetup` → `MeetupParticipants`: `orphanRemoval` 없음

### 1.2 Soft Delete 사용 현황

**Soft Delete를 사용하는 모든 엔티티**:
- `MissingPetBoard` (isDeleted, deletedAt)
- `MissingPetComment` (isDeleted, deletedAt)
- `Board` (isDeleted, deletedAt)
- `Comment` (isDeleted, deletedAt)
- `CareRequest` (isDeleted, deletedAt)
- `CareRequestComment` (isDeleted, deletedAt)
- `Pet` (isDeleted, deletedAt)
- `Meetup` (isDeleted, deletedAt)
- `Users` (isDeleted, deletedAt)
- 기타 다수

---

## 2. orphanRemoval = true와 Soft Delete의 충돌 문제

### 2.1 orphanRemoval의 동작 원리

```java
@OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
private List<MissingPetComment> comments;
```

**`orphanRemoval = true`의 의미**:
- 부모 엔티티에서 자식 엔티티를 컬렉션에서 제거하면 **실제 DB에서 DELETE 쿼리 실행**
- 부모 엔티티가 삭제되면 자식 엔티티도 **실제 DB에서 DELETE 쿼리 실행**
- **물리적 삭제(Physical Delete)**를 수행

### 2.2 Soft Delete의 동작 원리

```java
@Column(name = "is_deleted")
@Builder.Default
private Boolean isDeleted = false;

@Column(name = "deleted_at")
private LocalDateTime deletedAt;
```

**Soft Delete의 의미**:
- 데이터를 실제로 삭제하지 않고 `isDeleted = true`로 표시
- **논리적 삭제(Logical Delete)**를 수행
- 데이터 복구 가능, 히스토리 보존

### 2.3 충돌 문제 분석

#### 문제 1: orphanRemoval은 물리적 삭제만 지원

```java
// MissingPetBoardService.deleteBoard()
board.setIsDeleted(true);  // Soft Delete
board.setDeletedAt(LocalDateTime.now());

if (board.getComments() != null) {
    for (MissingPetComment c : board.getComments()) {
        c.setIsDeleted(true);  // Soft Delete 시도
        c.setDeletedAt(LocalDateTime.now());
    }
}
```

**문제점**:
- `orphanRemoval = true`는 부모가 삭제되면 자식도 **물리적으로 삭제**하려고 함
- 하지만 Soft Delete는 `isDeleted = true`만 설정하고 **실제 삭제하지 않음**
- JPA는 부모가 삭제되었다고 인식하지 못함 (실제로는 삭제되지 않았으므로)
- 결과: `orphanRemoval`이 작동하지 않음

#### 문제 2: 컬렉션에서 제거 시 물리적 삭제 발생

```java
// 만약 이런 코드가 있다면
board.getComments().remove(comment);  // 컬렉션에서 제거
boardRepository.save(board);  // orphanRemoval이 트리거되어 실제 DELETE 쿼리 실행!
```

**문제점**:
- 컬렉션에서 제거하면 `orphanRemoval = true`로 인해 **실제 DELETE 쿼리 실행**
- Soft Delete를 의도했지만 **물리적 삭제가 발생**
- 데이터 복구 불가능

#### 문제 3: 부모 삭제 시 자식 삭제 불일치

```java
// 현재 deleteBoard() 메서드
board.setIsDeleted(true);  // 부모만 Soft Delete
// 자식은 수동으로 Soft Delete 처리
```

**문제점**:
- `orphanRemoval = true`가 있으면 부모 삭제 시 자식도 자동 삭제되어야 함
- 하지만 Soft Delete에서는 부모가 실제로 삭제되지 않으므로 `orphanRemoval`이 트리거되지 않음
- **의미 없는 설정**이 됨

---

## 3. 판단 및 권장사항

### 3.1 결론: Soft Delete 방식에서는 orphanRemoval = true를 사용하면 안 됨

**이유**:

1. **기능적 충돌**
   - `orphanRemoval = true`는 물리적 삭제를 전제로 함
   - Soft Delete는 논리적 삭제를 수행
   - 두 방식이 근본적으로 상충함

2. **예상치 못한 동작**
   - 컬렉션에서 제거 시 실제 DELETE 쿼리 실행
   - Soft Delete 의도와 다르게 물리적 삭제 발생
   - 데이터 복구 불가능

3. **의미 없는 설정**
   - 부모가 실제로 삭제되지 않으므로 `orphanRemoval`이 트리거되지 않음
   - 수동으로 자식 삭제를 처리해야 함
   - `orphanRemoval`의 이점을 전혀 활용하지 못함

4. **일관성 부족**
   - 다른 엔티티들(`Board`, `CareRequest` 등)은 `orphanRemoval`을 사용하지 않음
   - `MissingPetBoard`만 예외적으로 사용하여 일관성 부족

### 3.2 권장 해결 방안

#### 방안 1: orphanRemoval 제거 (권장)

```java
// 변경 전
@OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
private List<MissingPetComment> comments;

// 변경 후
@OneToMany(mappedBy = "board", cascade = CascadeType.ALL)
private List<MissingPetComment> comments;
```

**장점**:
- Soft Delete와 완벽하게 호환
- 다른 엔티티들과 일관성 유지
- 예상치 못한 물리적 삭제 방지

**단점**:
- 자식 삭제를 수동으로 처리해야 함 (이미 그렇게 하고 있음)

#### 방안 2: Repository를 통한 명시적 삭제 (현재 해결 방안)

```java
@Transactional
public void deleteBoard(Long id) {
    MissingPetBoard board = boardRepository.findById(id)
            .orElseThrow(...);
    
    board.setIsDeleted(true);
    board.setDeletedAt(LocalDateTime.now());
    
    // orphanRemoval 대신 Repository를 통해 직접 조회하여 삭제
    List<MissingPetComment> comments = commentRepository
            .findByBoardAndIsDeletedFalse(board);
    
    for (MissingPetComment c : comments) {
        c.setIsDeleted(true);
        c.setDeletedAt(LocalDateTime.now());
    }
    
    boardRepository.saveAndFlush(board);
}
```

**장점**:
- Soft Delete와 완벽하게 호환
- 최신 상태의 댓글을 조회하여 삭제 (동시성 문제 해결)
- 명시적이고 예측 가능한 동작

**단점**:
- `orphanRemoval = true`가 있으면 혼란스러움 (실제로는 작동하지 않음)

#### 방안 3: @Query를 사용한 일괄 업데이트 (성능 최적화)

```java
// MissingPetCommentRepository에 추가
@Modifying
@Query("UPDATE MissingPetComment c SET c.isDeleted = true, c.deletedAt = :now WHERE c.board = :board AND c.isDeleted = false")
void softDeleteByBoard(@Param("board") MissingPetBoard board, @Param("now") LocalDateTime now);

// MissingPetBoardService.deleteBoard()에서 사용
@Transactional
public void deleteBoard(Long id) {
    MissingPetBoard board = boardRepository.findById(id)
            .orElseThrow(...);
    
    board.setIsDeleted(true);
    board.setDeletedAt(LocalDateTime.now());
    
    // @Query로 한 번에 업데이트
    commentRepository.softDeleteByBoard(board, LocalDateTime.now());
    
    boardRepository.saveAndFlush(board);
}
```

**장점**:
- 성능 최적화 (한 번의 쿼리로 모든 댓글 삭제)
- 동시성 문제 해결 (최신 상태 조회)
- 명시적이고 예측 가능한 동작

---

## 4. 최종 권장사항

### 4.1 즉시 조치

1. **`orphanRemoval = true` 제거**
   ```java
   // MissingPetBoard.java
   @OneToMany(mappedBy = "board", cascade = CascadeType.ALL)
   private List<MissingPetComment> comments;
   ```

2. **Repository를 통한 명시적 삭제로 변경**
   ```java
   // MissingPetBoardService.deleteBoard()
   List<MissingPetComment> comments = commentRepository
           .findByBoardAndIsDeletedFalse(board);
   ```

### 4.2 장기 개선

1. **@Query를 사용한 일괄 업데이트로 최적화**
2. **다른 엔티티들도 동일한 패턴으로 통일**

---

## 5. 참고: 다른 엔티티들의 패턴

### 5.1 Board → Comment

```java
// Board.java
@OneToMany(mappedBy = "board", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
private List<Comment> comments;  // orphanRemoval 없음

// BoardService.deleteBoard()에서 수동으로 댓글 삭제 처리
```

### 5.2 CareRequest → CareRequestComment

```java
// CareRequest.java
@OneToMany(mappedBy = "careRequest", cascade = CascadeType.ALL)
private List<CareRequestComment> comments;  // orphanRemoval 없음

// CareRequestService.deleteCareRequest()에서 수동으로 댓글 삭제 처리
```

**결론**: 다른 엔티티들도 모두 `orphanRemoval`을 사용하지 않고 수동으로 Soft Delete를 처리하고 있습니다. `MissingPetBoard`만 예외적으로 `orphanRemoval = true`를 사용하고 있어 일관성이 부족합니다.

---

## 6. 요약

| 항목 | 내용 |
|------|------|
| **현재 상태** | `MissingPetBoard`만 `orphanRemoval = true` 사용 |
| **문제점** | Soft Delete와 충돌, 의미 없는 설정, 예상치 못한 동작 가능 |
| **권장사항** | `orphanRemoval = true` 제거 + Repository를 통한 명시적 삭제 |
| **일관성** | 다른 엔티티들과 동일한 패턴으로 통일 필요 |

