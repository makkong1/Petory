# Step 4: PublicDataSyncScheduler(@Scheduled) + 관리자 수동 트리거 엔드포인트

## 목표
- 매일 새벽 자동으로 `PublicDataSyncService.syncFromApi(SCHEDULED)`를 실행하는 스케줄러 추가.
- 관리자가 즉시 실행할 수 있는 `POST /api/admin/location-services/sync-public-data` 엔드포인트 추가.

## 배경
- 스케줄링은 `global/config/SchedulingConfig`의 `@EnableScheduling`이 중앙에서 켠다
  (`petory.scheduling.enabled=true` 기본, false면 전체 스케줄러 off). 따라서 개별 스케줄러에 조건 애노테이션 불필요 —
  `@Scheduled`만 붙이면 된다(기존 `LocationServiceScoreScheduler` 등과 동일).
- 자동 실행 시각은 기존 `LocationServiceScoreScheduler`(`0 0 0 * * *`)와 겹치지 않게 새벽 3시로 둔다.
- 수동 트리거는 기존 관리자 임포트 엔드포인트들과 같은 컨트롤러(`AdminLocationController`)에 두고 `MASTER` 권한으로 제한한다.

## 변경 파일

### 1. `backend/main/java/com/linkup/Petory/domain/location/service/PublicDataSyncScheduler.java` (신규)

```java
package com.linkup.Petory.domain.location.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.linkup.Petory.domain.location.entity.LocationSyncLog;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncTriggerType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 공공데이터 시설 동기화 스케줄러. 매일 새벽 3시에 전체 upsert 를 실행한다.
 * 스케줄링 on/off 는 SchedulingConfig(petory.scheduling.enabled)가 중앙 제어.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicDataSyncScheduler {

    private final PublicDataSyncService publicDataSyncService;

    /** 매일 03:00 실행. */
    @Scheduled(cron = "0 0 3 * * *")
    public void runDailySync() {
        try {
            LocationSyncLog result = publicDataSyncService.syncFromApi(SyncTriggerType.SCHEDULED);
            log.info("공공데이터 정기 동기화 종료: status={}, 신규={}, 갱신={}, 스킵={}, 실패={}",
                    result.getStatus(), result.getInserted(), result.getUpdated(),
                    result.getSkipped(), result.getFailed());
        } catch (Exception e) {
            // syncFromApi 내부에서 대부분 처리되지만, 최후 방어로 스케줄러 스레드가 죽지 않게 감싼다.
            log.error("공공데이터 정기 동기화 중 예외: {}", e.getMessage(), e);
        }
    }
}
```

### 2. `AdminLocationController.java` (기존, 필드 + 엔드포인트 추가)

**필드 추가** — 기존 `private final PublicDataLocationService publicDataLocationService;` 아래에:

```java
    private final PublicDataSyncService publicDataSyncService;
```

**import 추가** — 기존 location.service import 묶음에:

```java
import com.linkup.Petory.domain.location.entity.LocationSyncLog;
import com.linkup.Petory.domain.location.service.PublicDataSyncService;
```

**엔드포인트 추가** — 기존 `importPublicData`(멀티파트) 계열 아래에:

```java
    /**
     * 공공데이터 오픈API를 즉시 호출해 시설 데이터를 upsert 한다. [MASTER]
     * 실행 결과 요약(상태·신규·갱신·스킵·실패 건수)을 반환한다.
     */
    @PostMapping("/sync-public-data")
    @PreAuthorize("hasRole('MASTER')")
    public ResponseEntity<Map<String, Object>> syncPublicData() {
        LocationSyncLog result = publicDataSyncService.syncFromApi(LocationSyncLog.SyncTriggerType.MANUAL);
        Map<String, Object> body = new HashMap<>();
        body.put("status", result.getStatus().name());
        body.put("totalFetched", result.getTotalFetched());
        body.put("inserted", result.getInserted());
        body.put("updated", result.getUpdated());
        body.put("skipped", result.getSkipped());
        body.put("failed", result.getFailed());
        body.put("startedAt", result.getStartedAt());
        body.put("finishedAt", result.getFinishedAt());
        if (result.getErrorMessage() != null) {
            body.put("errorMessage", result.getErrorMessage());
        }
        return ResponseEntity.ok(body);
    }
```

## 테스트

### `backend/test/java/com/linkup/Petory/domain/location/service/PublicDataSyncSchedulerTest.java` (신규)
스케줄러가 서비스에 SCHEDULED 트리거로 위임하고, 서비스 예외가 스케줄러 밖으로 전파되지 않는지 검증.

```java
package com.linkup.Petory.domain.location.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.location.entity.LocationSyncLog;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncTriggerType;

@ExtendWith(MockitoExtension.class)
class PublicDataSyncSchedulerTest {

    @Mock PublicDataSyncService syncService;
    @InjectMocks PublicDataSyncScheduler scheduler;

    @Test
    void 매일_동기화는_SCHEDULED_트리거로_위임한다() {
        when(syncService.syncFromApi(eq(SyncTriggerType.SCHEDULED)))
                .thenReturn(LocationSyncLog.builder()
                        .status(LocationSyncLog.SyncStatus.SUCCESS).build());

        scheduler.runDailySync();

        verify(syncService).syncFromApi(SyncTriggerType.SCHEDULED);
    }

    @Test
    void 서비스_예외는_스케줄러_밖으로_전파되지_않는다() {
        when(syncService.syncFromApi(eq(SyncTriggerType.SCHEDULED)))
                .thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> scheduler.runDailySync()).doesNotThrowAnyException();
    }
}
```

## Acceptance Criteria

- [ ] `./gradlew compileJava` 성공.
- [ ] `./gradlew test --tests "*PublicDataSyncSchedulerTest"` PASS.
- [ ] 전체 회귀: `./gradlew test --tests "*PublicData*"` — 클라이언트/서비스/스케줄러 테스트 전부 PASS.
- [ ] (선택, 서비스키 발급 후 수동) 앱 기동 후 MASTER 토큰으로:
  `curl -X POST -H "Authorization: Bearer <MASTER토큰>" http://localhost:8080/api/admin/location-services/sync-public-data`
  → 200 + `{"status":"SUCCESS"|"PARTIAL", "inserted":N, "updated":M, ...}` 확인. `location_sync_log`에 1행 적재 확인.

## 커밋

```bash
git add backend/main/java/com/linkup/Petory/domain/location/service/PublicDataSyncScheduler.java \
        backend/main/java/com/linkup/Petory/domain/admin/controller/AdminLocationController.java \
        backend/test/java/com/linkup/Petory/domain/location/service/PublicDataSyncSchedulerTest.java
git commit -m "feat(location): 공공데이터 동기화 스케줄러 및 관리자 수동 트리거 엔드포인트 추가"
```
