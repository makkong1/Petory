# Step 3: Controller + Scheduler — POST /api/admin/location/sync + @Scheduled 자정 배치

## 목표
- 어드민 전용 `POST /api/admin/location/sync` 엔드포인트 추가 (수동 트리거)
- `@Scheduled("0 0 0 * * *")` 매일 자정 자동 동기화
- 동기화 결과 통계 반환

## 배경
Step 2에서 `FacilitySyncService` 완성.
Petory 기존 패턴: ADMIN 권한은 `@PreAuthorize("hasRole('ADMIN') or hasRole('MASTER')")`.
스케줄: `statistics` 도메인의 `StatisticsScheduler`와 동일한 `@Scheduled` 패턴 사용.

## 변경 파일

### 1. `backend/main/java/com/linkup/Petory/domain/location/controller/LocationServiceAdminController.java` (신규)

```java
package com.linkup.Petory.domain.location.controller;

import com.linkup.Petory.domain.location.service.FacilitySyncService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/location")
@RequiredArgsConstructor
public class LocationServiceAdminController {

    private final FacilitySyncService facilitySyncService;

    @PostMapping("/sync")
    @PreAuthorize("hasRole('ADMIN') or hasRole('MASTER')")
    public ResponseEntity<Map<String, Object>> syncFacilities() {
        FacilitySyncService.SyncResult result = facilitySyncService.syncFromPetDataApi();
        return ResponseEntity.ok(Map.of(
                "total", result.getTotal(),
                "saved", result.getSaved(),
                "duplicate", result.getDuplicate(),
                "skipped", result.getSkipped()
        ));
    }
}
```

### 2. `backend/main/java/com/linkup/Petory/domain/location/service/FacilitySyncScheduler.java` (신규)

```java
package com.linkup.Petory.domain.location.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FacilitySyncScheduler {

    private final FacilitySyncService facilitySyncService;

    // 매일 자정 실행 (statistics 도메인 배치와 동일 패턴)
    @Scheduled(cron = "0 0 0 * * *")
    public void scheduledSync() {
        log.info("[FacilitySyncScheduler] 자동 동기화 시작");
        try {
            FacilitySyncService.SyncResult result = facilitySyncService.syncFromPetDataApi();
            log.info("[FacilitySyncScheduler] 완료 total={} saved={} duplicate={} skipped={}",
                    result.getTotal(), result.getSaved(), result.getDuplicate(), result.getSkipped());
        } catch (Exception e) {
            log.error("[FacilitySyncScheduler] 실패: {}", e.getMessage(), e);
        }
    }
}
```

### 3. `backend/main/resources/application.properties` 추가 설정 (참고)

pet-data-api 연결 설정이 없으면 추가 필요:
```properties
app.pet-data-api.base-url=http://localhost:8000
app.pet-data-api.api-key=<your-api-key>
app.pet-data-api.timeout-ms=3000
app.pet-data-api.copy-timeout-ms=35000
```

## AC (Acceptance Criteria)

```bash
cd /Users/maknkkong/project/Petory

# 컴파일
./gradlew compileJava

# 전체 테스트
./gradlew test

# 통합 확인 (MySQL + Redis + pet-data-api 모두 기동 상태):
# 1. 서버 기동
./gradlew bootRun &

# 2. ADMIN 토큰으로 sync 엔드포인트 호출
# curl -X POST http://localhost:8080/api/admin/location/sync \
#   -H "Authorization: Bearer <admin-token>"
# 응답 예: {"total": 1500, "saved": 1200, "duplicate": 300, "skipped": 0}

# 3. locationservice 테이블에서 PET_DATA_API 소스 데이터 확인
# SELECT count(*) FROM locationservice WHERE data_source = 'PET_DATA_API';
```

## 주의사항
- `FacilitySyncService.syncFromPetDataApi()`는 pet-data-api가 다운되면 예외 발생.
  스케줄러에서 `try-catch`로 감싸 다운이 있어도 서버에 영향 없음 (Step 2 코드에 이미 적용).
- 중복 체크는 `name + address` 기준.
  pet-data-api와 CSV 업로드 데이터가 동일 시설을 가리키면 중복으로 처리됨.
