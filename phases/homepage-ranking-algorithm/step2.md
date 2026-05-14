# Step 2 — Backend: 모임 홈 전용 엔드포인트

## 목표
`GET /api/meetups/home?lat=&lng=&size=6` 엔드포인트를 추가한다.
반경 50km 내 모집중 모임을 `거리(0.4) + 긴급도(0.4) + 인원여유(0.2)` 스코어로 정렬해 반환한다.
좌표 없거나 결과가 없으면 `/available` 위임으로 폴백한다.

## 변경 파일
- `backend/main/java/com/linkup/Petory/domain/meetup/controller/MeetupController.java`
- `backend/main/java/com/linkup/Petory/domain/meetup/service/MeetupService.java`

---

## 변경 상세

### 1. `MeetupService.java` — getHomeMeetups 메서드 추가

기존 `getNearbyMeetups` 메서드 아래에 삽입:

```java
/**
 * 홈 화면 모임 추천.
 * score = 0.4 * distScore + 0.4 * urgencyScore + 0.2 * capacityScore
 */
public List<MeetupDTO> getHomeMeetups(Double lat, Double lng, int size) {
    // 폴백: 좌표 없으면 참여 가능 모임 날짜순
    if (lat == null || lng == null) {
        Pageable p = PageRequest.of(0, size, Sort.by(Sort.Direction.ASC, "date"));
        return getAvailableMeetups(p).getContent();
    }

    // 반경 50km 후보 (size*3개 충분히 가져온 뒤 재정렬)
    List<MeetupDTO> candidates = getNearbyMeetups(lat, lng, 50.0, size * 3);

    if (candidates.isEmpty()) {
        Pageable p = PageRequest.of(0, size, Sort.by(Sort.Direction.ASC, "date"));
        return getAvailableMeetups(p).getContent();
    }

    long now = System.currentTimeMillis();

    List<MeetupDTO> scored = candidates.stream()
            .filter(m -> m.getStatus() == MeetupStatus.RECRUITING)
            .map(m -> {
                // 거리 점수: 50km 이내일수록 1에 가까움
                double distKm = haversineKm(lat, lng,
                        m.getLatitude() != null ? m.getLatitude() : lat,
                        m.getLongitude() != null ? m.getLongitude() : lng);
                double distScore = Math.max(0, 1.0 - distKm / 50.0);

                // 긴급도: 30일 이내 모임일수록 1에 가까움
                double daysUntil = m.getDate() != null
                        ? (m.getDate().getTime() - now) / 86_400_000.0
                        : 30;
                double urgencyScore = Math.max(0, 1.0 - daysUntil / 30.0);

                // 인원 여유: 빈 자리가 많을수록 1에 가까움
                int max = m.getMaxParticipants() != null && m.getMaxParticipants() > 0
                        ? m.getMaxParticipants() : 1;
                int cur = m.getCurrentParticipants() != null ? m.getCurrentParticipants() : 0;
                double capacityScore = 1.0 - (double) cur / max;

                double score = 0.4 * distScore + 0.4 * urgencyScore + 0.2 * capacityScore;
                m.setHomeScore(score); // DTO에 homeScore 필드 추가 필요 (아래 참고)
                return m;
            })
            .sorted(Comparator.comparingDouble(MeetupDTO::getHomeScore).reversed())
            .limit(size)
            .collect(java.util.stream.Collectors.toList());

    return scored;
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

> **MeetupDTO homeScore 필드**: `MeetupDTO`가 record인지 class인지 확인.
> - **record**: `homeScore` 파라미터를 생성자에 추가하거나, 별도 래퍼 record 사용.
> - **class(@Getter/@Setter)**: `private double homeScore;` 필드 추가.
> - 가장 단순한 방법: score 기준 정렬에만 사용하므로 stream 내에서 Comparator에 직접 람다로 계산. homeScore 필드 추가 없이 처리 가능:
>
> ```java
> .sorted(Comparator.comparingDouble((MeetupDTO m) -> {
>     // 위의 distScore + urgencyScore + capacityScore 재계산
>     ...
> }).reversed())
> ```

> **MeetupDTO 타입 확인 필수**: `MeetupDTO.java`를 먼저 읽어 record/class 여부와
> `latitude`, `longitude`, `date`, `status`, `maxParticipants`, `currentParticipants` getter 이름 확인.

### 2. `MeetupController.java` — 엔드포인트 추가

기존 `@GetMapping("/nearby")` 바로 아래에 삽입:

```java
@GetMapping("/home")
public ResponseEntity<Map<String, Object>> getHomeMeetups(
        @RequestParam(value = "lat", required = false) Double lat,
        @RequestParam(value = "lng", required = false) Double lng,
        @RequestParam(value = "size", defaultValue = "6") int size) {

    List<MeetupDTO> meetups = meetupService.getHomeMeetups(lat, lng, size);

    Map<String, Object> response = new HashMap<>();
    response.put("meetups", meetups);
    response.put("count", meetups.size());

    return ResponseEntity.ok(response);
}
```

---

## AC (검증)

```bash
cd /Users/maknkkong/project/Petory && ./gradlew compileJava
# BUILD SUCCESSFUL
```

런타임: `GET /api/meetups/home?lat=37.5665&lng=126.9780&size=6` → 스코어 정렬된 모임 최대 6개 반환.
좌표 없을 때: `GET /api/meetups/home?size=6` → 날짜순 참여 가능 모임 반환.
