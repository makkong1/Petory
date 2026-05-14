# Step 1 — Backend: 주변서비스 score 컬럼 + 배치 계산

## 목표
`locationservice` 테이블에 `score` 컬럼을 추가하고 복합 스코어 공식으로 매일 자정 배치 계산한다.
홈 화면 `/search?sort=score` 호출 시 score DESC 정렬로 상위 6개를 반환한다.

## 변경 파일
- `backend/main/java/com/linkup/Petory/domain/location/entity/LocationService.java` — score 필드 추가
- `backend/main/java/com/linkup/Petory/domain/location/service/LocationServiceService.java` — computeScore + sort=score 분기
- `backend/main/java/com/linkup/Petory/domain/location/service/LocationServiceScoreScheduler.java` — 신규: 배치 스케줄러
- `backend/main/java/com/linkup/Petory/domain/location/repository/SpringDataJpaLocationServiceRepository.java` — 정렬 메서드 추가

---

## 변경 상세

### 1. `LocationService.java` — score 필드 추가

`reviewCount` 필드 바로 아래에 삽입:

```java
@Builder.Default
@Column(name = "score")
private Double score = 0.0;
```

### 2. `SpringDataJpaLocationServiceRepository.java` — score 정렬 메서드 추가

기존 `findByOrderByRatingDesc` 선언 옆에 추가:

```java
List<LocationService> findTop6ByIsDeletedFalseOrderByScoreDesc();
```

### 3. `LocationServiceService.java` — computeScore 메서드 추가

클래스 내부 어디든 추가:

```java
private double computeScore(LocationService ls) {
    double ratingScore = (ls.getRating() != null ? ls.getRating() : 0.0)
            * Math.log10((ls.getReviewCount() != null ? ls.getReviewCount() : 0) + 1);
    double petBonus = Boolean.TRUE.equals(ls.getPetFriendly()) ? 1.0 : 0.0;
    return 0.5 * ratingScore + 0.2 * petBonus;
}
```

### 4. `LocationServiceService.java` — searchLocationServices의 sort 분기에 score 추가

`searchLocationServices` 메서드에서 `sort` 파라미터를 처리하는 부분을 찾아
`"rating"` 케이스 옆에 `"score"` 케이스 추가:

현재 sort 처리 로직이 어떻게 돼있는지 확인 후,
결과 리스트를 반환하기 직전에 다음 분기를 추가한다:

```java
if ("score".equalsIgnoreCase(sort)) {
    services.sort(Comparator.comparingDouble(
            (LocationService ls) -> ls.getScore() != null ? ls.getScore() : 0.0
    ).reversed());
}
```

> **참고**: 현재 구조상 `sort` 파라미터는 메서드 인자로 전달됨.
> 메서드 시그니처와 호출부를 먼저 확인한 뒤, 가장 자연스러운 위치에 삽입.

### 5. `LocationServiceScoreScheduler.java` — 신규 파일 생성

`backend/main/java/com/linkup/Petory/domain/location/service/` 에 생성:

```java
package com.linkup.Petory.domain.location.service;

import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.SpringDataJpaLocationServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LocationServiceScoreScheduler {

    private final SpringDataJpaLocationServiceRepository locationServiceRepository;
    private final LocationServiceService locationServiceService;

    /**
     * 매일 자정 전체 score 재계산.
     * score = 0.5 * rating * log10(reviewCount+1) + 0.2 * petFriendly
     */
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    public void recalculateAllScores() {
        log.info("[ScoreScheduler] 전체 location service score 재계산 시작");
        List<LocationService> all = locationServiceRepository.findAll();
        for (LocationService ls : all) {
            ls.setScore(computeScore(ls));
        }
        locationServiceRepository.saveAll(all);
        log.info("[ScoreScheduler] score 재계산 완료: {}건", all.size());
    }

    private double computeScore(LocationService ls) {
        double ratingScore = (ls.getRating() != null ? ls.getRating() : 0.0)
                * Math.log10((ls.getReviewCount() != null ? ls.getReviewCount() : 0) + 1);
        double petBonus = Boolean.TRUE.equals(ls.getPetFriendly()) ? 1.0 : 0.0;
        return 0.5 * ratingScore + 0.2 * petBonus;
    }
}
```

> **주의**: `LocationServiceService`에 `computeScore`가 private이면 스케줄러에서 직접 중복 구현.
> 두 메서드가 동일한 공식을 사용함 — 나중에 공통 유틸로 추출 가능.

> **@Scheduled 동작 조건**: 루트 Application 클래스에 `@EnableScheduling` 없으면 추가 필요.
> `PetoryApplication.java` 에 `@EnableScheduling` 있는지 확인 후 없으면 추가.

---

## AC (검증)

```bash
cd /Users/maknkkong/project/Petory && ./gradlew compileJava
# BUILD SUCCESSFUL
```

런타임: 앱 시작 → 자정 배치 실행 → locationservice.score 컬럼 갱신됨.
홈화면 주변서비스 탭: `sort=score`로 호출 시 score DESC 정렬된 결과 반환.
