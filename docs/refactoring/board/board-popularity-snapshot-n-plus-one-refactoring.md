# BoardPopularitySnapshotConverter N+1 리팩토링

## 문제

인기글 목록 API 호출 시 서버 로그에서 `[Repository] 첨부파일: 타겟별 조회` + `SELECT ... FROM file` 쿼리가 인기글 수만큼 반복 발생.

```
[Repository] 첨부파일: 타겟별 조회
Hibernate: select af1_0.idx,... from file af1_0 where af1_0.target_type=? and af1_0.target_idx=?
[Repository] 첨부파일: 타겟별 조회
Hibernate: select af1_0.idx,... from file af1_0 where af1_0.target_type=? and af1_0.target_idx=?
... (인기글 스냅샷 수 N만큼 반복)
```

---

## 원인

### 호출 흐름

```
BoardPopularityService.getPopularBoards()
  └─ converter.toDTOList(snapshots)
       └─ stream().map(this::toDTO)               ← N번 반복
            └─ resolvePrimaryFileUrl(board)
                 └─ attachmentFileService
                      .getAttachments(BOARD, boardId)  ← DB 쿼리 N번
```

`BoardPopularitySnapshotConverter.toDTOList()`가 스냅샷 목록을 순회하면서 각 Board마다 `getAttachments()`를 1회씩 호출했다. 스냅샷 30개면 파일 조회 쿼리 30번 추가 발생.

`AttachmentFileService.getAttachmentsBatch()`가 이미 존재했으나 이 converter에서 사용하지 않았다.

### 문제 코드 (변경 전)

```java
// BoardPopularitySnapshotConverter.java
public List<BoardPopularitySnapshotDTO> toDTOList(List<BoardPopularitySnapshot> snapshots) {
    return snapshots.stream()
            .map(this::toDTO)          // toDTO 내부에서 N번 쿼리
            .collect(Collectors.toList());
}

private String resolvePrimaryFileUrl(Board board) {
    if (board == null) return null;
    // ← 스냅샷마다 1번씩 SELECT FROM file 실행
    List<FileDTO> attachments = attachmentFileService.getAttachments(FileTargetType.BOARD, board.getIdx());
    ...
}
```

---

## 해결

`toDTOList()`에서 모든 boardId를 수집해 `getAttachmentsBatch()`로 한 번에 조회한 뒤, 결과 Map을 각 DTO 변환에 전달한다.

### 변경 내용

**파일**: `domain/board/converter/BoardPopularitySnapshotConverter.java`

```java
// 변경 후
public List<BoardPopularitySnapshotDTO> toDTOList(List<BoardPopularitySnapshot> snapshots) {
    if (snapshots == null || snapshots.isEmpty()) {
        return List.of();
    }

    List<Long> boardIds = snapshots.stream()
            .map(BoardPopularitySnapshot::getBoard)
            .filter(Objects::nonNull)
            .map(Board::getIdx)
            .collect(Collectors.toList());

    // 파일 조회 1번 (IN절 배치)
    Map<Long, List<FileDTO>> attachmentsMap =
            attachmentFileService.getAttachmentsBatch(FileTargetType.BOARD, boardIds);

    return snapshots.stream()
            .map(s -> toDTO(s, attachmentsMap))   // Map 전달
            .collect(Collectors.toList());
}

private BoardPopularitySnapshotDTO toDTO(BoardPopularitySnapshot snapshot,
                                          Map<Long, List<FileDTO>> attachmentsMap) {
    Board board = snapshot.getBoard();
    return new BoardPopularitySnapshotDTO(
            ...,
            resolvePrimaryFileUrl(board, attachmentsMap),
            ...);
}

private String resolvePrimaryFileUrl(Board board, Map<Long, List<FileDTO>> attachmentsMap) {
    if (board == null) return null;
    // DB 조회 없음 — Map에서 O(1) 조회
    List<FileDTO> attachments = attachmentsMap.getOrDefault(board.getIdx(), List.of());
    if (attachments.isEmpty()) return null;
    FileDTO primary = attachments.get(0);
    if (StringUtils.hasText(primary.getDownloadUrl())) return primary.getDownloadUrl();
    return attachmentFileService.buildDownloadUrl(primary.getFilePath());
}
```

### 쿼리 비교

| | 변경 전 | 변경 후 |
|--|---------|---------|
| 파일 조회 쿼리 수 | N번 (`WHERE target_idx = ?` × N) | 1번 (`WHERE target_idx IN (?, ?, ...)`) |
| 스냅샷 30개 기준 | 31번 쿼리 | 2번 쿼리 |

---

## 테스트

**파일**: `test/.../domain/board/converter/BoardPopularitySnapshotConverterTest.java`

### N+1 회귀 방지 (핵심)

```java
@Test
@DisplayName("[N+1 방지] toDTOList는 getAttachments를 단 한 번도 호출하지 않는다")
void toDTOList_neverCallsSingleGetAttachments() {
    List<BoardPopularitySnapshot> snapshots = List.of(
            snapshot(1L, board(1L)), snapshot(2L, board(2L)), snapshot(3L, board(3L)));
    when(attachmentFileService.getAttachmentsBatch(eq(FileTargetType.BOARD), anyList()))
            .thenReturn(Map.of());

    converter.toDTOList(snapshots);

    // 단건 조회 0번. 이 검증이 깨지면 N+1 재발.
    verify(attachmentFileService, never()).getAttachments(any(), any());
}

@Test
@DisplayName("[배치 조회] toDTOList는 getAttachmentsBatch를 정확히 1번 호출한다")
void toDTOList_callsGetAttachmentsBatchExactlyOnce() {
    List<BoardPopularitySnapshot> snapshots = List.of(
            snapshot(1L, board(1L)), snapshot(2L, board(2L)), snapshot(3L, board(3L)));
    when(attachmentFileService.getAttachmentsBatch(eq(FileTargetType.BOARD), anyList()))
            .thenReturn(Map.of());

    converter.toDTOList(snapshots);

    verify(attachmentFileService, times(1))
            .getAttachmentsBatch(eq(FileTargetType.BOARD), anyList());
}
```

### 전체 테스트 결과 (8/8 PASS)

```
[N+1 방지] toDTOList는 getAttachments를 단 한 번도 호출하지 않는다       PASSED
[배치 조회] toDTOList는 getAttachmentsBatch를 정확히 1번 호출한다         PASSED
[boardId 전달] 배치 조회에 모든 boardId가 포함된다                        PASSED
[URL] 배치 결과에서 첫 번째 파일의 downloadUrl이 boardFilePath에 세팅된다  PASSED
[URL fallback] downloadUrl이 없으면 buildDownloadUrl로 fallback된다        PASSED
[빈 리스트] 빈 목록 입력 시 getAttachmentsBatch를 호출하지 않는다         PASSED
[null board] board가 null인 스냅샷은 boardFilePath=null로 정상 변환된다    PASSED
[첨부파일 없음] 배치 결과에 해당 boardId 없으면 boardFilePath=null         PASSED
```

---

## 관련 문서

- [board-popularity-snapshot-batch-refactoring.md](./board-popularity-snapshot-batch-refactoring.md) — 인기글 집계 배치 병렬화
