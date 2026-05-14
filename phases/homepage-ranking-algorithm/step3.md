# Step 3 — Backend: 실종신고 홈 전용 엔드포인트

## 목표
`GET /api/missing-pets/home?lat=&lng=&size=6` 엔드포인트를 추가한다.
`최신성(0.6) + 거리(0.4)` 스코어로 실종 게시글을 정렬해 반환한다.
좌표 없으면 `lostDate DESC LIMIT size`로 폴백한다.

## 변경 파일
- `backend/main/java/com/linkup/Petory/domain/board/controller/MissingPetBoardController.java`
- `backend/main/java/com/linkup/Petory/domain/board/service/MissingPetBoardService.java`

---

## 변경 상세

### 1. `MissingPetBoardService.java` — getHomeMissing 메서드 추가

기존 `getBoardsWithPaging` 메서드 아래에 삽입:

```java
/**
 * 홈 화면 실종신고 추천.
 * score = 0.6 * recencyScore + 0.4 * distScore
 * recencyScore = max(0, 1 - daysSinceLost / 14)  → 2주 이내 1→0
 * distScore    = max(0, 1 - distKm / 20)          → 20km 이내 1→0
 * 좌표 없으면: lostDate DESC LIMIT size
 */
public List<MissingPetBoardDTO> getHomeMissing(Double lat, Double lng, int size) {
    // 좌표 없으면 단순 최신순
    if (lat == null || lng == null) {
        Pageable p = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "lostDate"));
        return missingPetBoardRepository
                .findByStatusAndIsDeletedFalse(MissingPetStatus.MISSING, p)
                .stream()
                .map(this::toDTO)
                .collect(java.util.stream.Collectors.toList());
    }

    // 충분한 후보 가져오기 (최근 30일 또는 전체 상위 50개)
    Pageable bigPage = PageRequest.of(0, 50, Sort.by(Sort.Direction.DESC, "lostDate"));
    List<MissingPetBoard> candidates = missingPetBoardRepository
            .findByStatusAndIsDeletedFalse(MissingPetStatus.MISSING, bigPage)
            .getContent();

    LocalDate today = LocalDate.now();

    return candidates.stream()
            .map(board -> {
                // 최신성: 실종 후 14일이 지나면 0
                long daysSinceLost = board.getLostDate() != null
                        ? java.time.temporal.ChronoUnit.DAYS.between(board.getLostDate(), today)
                        : 14;
                double recencyScore = Math.max(0, 1.0 - daysSinceLost / 14.0);

                // 거리: 20km 이내
                double distScore = 0.0;
                if (board.getLatitude() != null && board.getLongitude() != null) {
                    double distKm = haversineKm(lat, lng,
                            board.getLatitude().doubleValue(),
                            board.getLongitude().doubleValue());
                    distScore = Math.max(0, 1.0 - distKm / 20.0);
                }

                double score = 0.6 * recencyScore + 0.4 * distScore;
                return new AbstractMap.SimpleEntry<>(board, score);
            })
            .sorted(Map.Entry.<MissingPetBoard, Double>comparingByValue().reversed())
            .limit(size)
            .map(entry -> toDTO(entry.getKey()))
            .collect(java.util.stream.Collectors.toList());
}

private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
    final int R = 6371;
    double dLat = Math.toRadians(lat2 - lat1);
    double dLng = Math.toRadians(lng2 - lng1);
    double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}
```

> **사전 확인 필수:**
> 1. `missingPetBoardRepository` 필드명 — 서비스 내 주입된 실제 필드명 확인
> 2. `findByStatusAndIsDeletedFalse(MissingPetStatus, Pageable)` 메서드 존재 여부 확인
>    없으면 Repository에 추가: `Slice<MissingPetBoard> findByStatusAndIsDeletedFalse(MissingPetStatus status, Pageable pageable);`
> 3. `toDTO(MissingPetBoard)` 또는 `buildDTO` 같은 변환 메서드명 — 실제 메서드명으로 교체
> 4. import 추가:
>    ```java
>    import java.util.AbstractMap;
>    import java.time.LocalDate;
>    import java.time.temporal.ChronoUnit;
>    import org.springframework.data.domain.PageRequest;
>    import org.springframework.data.domain.Sort;
>    ```

### 2. `MissingPetBoardController.java` — 엔드포인트 추가

기존 `@GetMapping` 목록 중 적절한 위치에 삽입:

```java
@GetMapping("/home")
public ResponseEntity<List<MissingPetBoardDTO>> getHomeMissing(
        @RequestParam(value = "lat", required = false) Double lat,
        @RequestParam(value = "lng", required = false) Double lng,
        @RequestParam(value = "size", defaultValue = "6") int size) {
    return ResponseEntity.ok(missingPetBoardService.getHomeMissing(lat, lng, size));
}
```

> **컨트롤러 기본 경로 확인**: `@RequestMapping` 값이 `/api/missing-pets`인지 확인.

---

## AC (검증)

```bash
cd /Users/maknkkong/project/Petory && ./gradlew compileJava
# BUILD SUCCESSFUL
```

런타임:
- `GET /api/missing-pets/home?lat=37.5665&lng=126.9780&size=6` → 스코어 정렬된 실종신고 최대 6개
- `GET /api/missing-pets/home?size=6` → lostDate 최신순 6개
