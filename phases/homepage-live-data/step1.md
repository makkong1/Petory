# Step 1 — Backend: BoardPopularityService 최신 게시글 폴백

## 목표
`getPopularBoards`가 스냅샷도, PRIDE 게시글도 없을 때(DB 초기 상태) 최신 게시글 10개를 임시 DTO로 반환한다.

## 배경
현재 흐름: 정확한 날짜 → 기간 겹침 → 최근 스냅샷 → generateSnapshots → **빈 리스트 반환**.
`generateSnapshots`는 "자랑"/"PRIDE" 카테고리만 대상으로 하므로, 일반 게시글이 있어도 빈 리스트를 반환한다.

## 변경 파일
`backend/main/java/com/linkup/Petory/domain/board/service/BoardPopularityService.java`

---

## 변경 상세

### 1. import 추가 (파일 상단 import 블록)

```java
import org.springframework.data.domain.PageRequest;
```

### 2. `getPopularBoards` 메서드 — step 4 이후 폴백 추가

**위치**: `:48` ~ `:77`

현재 코드:
```java
// 4. 모든 시도가 실패하면 새로 생성
if (snapshots.isEmpty()) {
    snapshots = generateSnapshots(periodType, range);
}

return snapshotConverter.toDTOList(snapshots);
```

변경 후:
```java
// 4. 모든 시도가 실패하면 새로 생성
if (snapshots.isEmpty()) {
    snapshots = generateSnapshots(periodType, range);
}

// 5. 스냅샷 생성도 실패(게시글 없음)하면 최신 게시글로 대체
if (snapshots.isEmpty()) {
    return buildRecentBoardFallback(periodType, range);
}

return snapshotConverter.toDTOList(snapshots);
```

### 3. `buildRecentBoardFallback` 메서드 추가 (클래스 하단, `generateSnapshots` 아래)

```java
private List<BoardPopularitySnapshotDTO> buildRecentBoardFallback(
        PopularityPeriodType periodType, PeriodRange range) {
    log.info("인기 스냅샷 없음 — 최신 게시글 10개로 대체");
    List<Board> recent = boardRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc(
            PageRequest.of(0, 10)).getContent();
    int[] rank = {1};
    return recent.stream()
            .map(b -> new BoardPopularitySnapshotDTO(
                    null,
                    b.getIdx(),
                    periodType,
                    range.periodStart(),
                    range.periodEnd(),
                    rank[0]++,
                    0,
                    0,
                    0,
                    b.getViews() != null ? b.getViews() : 0,
                    b.getTitle(),
                    b.getCategory(),
                    b.getBoardFilePath(),
                    b.getCreatedAt()))
            .collect(Collectors.toList());
}
```

## AC (검증)
```bash
./gradlew compileJava
# 성공 시: BUILD SUCCESSFUL
```

런타임 검증: 백엔드 실행 후 `GET /api/boards/popular?period=WEEKLY` 호출 → 게시글이 있으면 리스트 반환, 없으면 빈 배열.
