# Step 1: DB 레이어 — V8 마이그레이션 + LocationSyncLog 엔티티/리포지토리

## 목표
파이프라인 실행 이력을 저장할 `location_sync_log` 테이블을 Flyway V8 마이그레이션으로 추가하고,
대응하는 `LocationSyncLog` JPA 엔티티와 `LocationSyncLogRepository`를 생성한다.

## 배경
- 스펙의 "실행 이력 추적" 요구사항. run 당 1행(시작/종료, 조회/신규/갱신/스킵/실패 건수, 상태, 트리거 종류)을 남긴다.
- CLAUDE.md 규칙: **스키마의 정본은 `db/migration/V*.sql`이고, 새 변경은 반드시 다음 번호(V8)로 추가**한다. 이미 적용된 V1~V7은 수정 금지(체크섬 검사로 기동 실패).
- `ddl-auto=validate`이므로 엔티티와 실제 스키마가 정확히 일치해야 앱이 기동한다.

## 변경 파일

### 1. `backend/main/resources/db/migration/V8__location_sync_log.sql` (신규)

```sql
-- 공공데이터 위치 동기화 파이프라인 실행 이력
CREATE TABLE location_sync_log (
    idx           BIGINT       NOT NULL AUTO_INCREMENT,
    started_at    DATETIME     NOT NULL,
    finished_at   DATETIME     NULL,
    status        VARCHAR(20)  NOT NULL,
    total_fetched INT          NOT NULL DEFAULT 0,
    inserted      INT          NOT NULL DEFAULT 0,
    updated       INT          NOT NULL DEFAULT 0,
    skipped       INT          NOT NULL DEFAULT 0,
    failed        INT          NOT NULL DEFAULT 0,
    trigger_type  VARCHAR(20)  NOT NULL,
    error_message TEXT         NULL,
    PRIMARY KEY (idx),
    KEY idx_location_sync_log_started (started_at DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
```

### 2. `backend/main/java/com/linkup/Petory/domain/location/entity/LocationSyncLog.java` (신규)

```java
package com.linkup.Petory.domain.location.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 공공데이터 위치 동기화 실행 이력 1건.
 */
@Entity
@Table(name = "location_sync_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LocationSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SyncStatus status;

    @Column(name = "total_fetched", nullable = false)
    private int totalFetched;

    @Column(name = "inserted", nullable = false)
    private int inserted;

    @Column(name = "updated", nullable = false)
    private int updated;

    @Column(name = "skipped", nullable = false)
    private int skipped;

    @Column(name = "failed", nullable = false)
    private int failed;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private SyncTriggerType triggerType;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public enum SyncStatus {
        SUCCESS, PARTIAL, FAILED
    }

    public enum SyncTriggerType {
        SCHEDULED, MANUAL
    }

    @Builder
    private LocationSyncLog(LocalDateTime startedAt, LocalDateTime finishedAt, SyncStatus status,
            int totalFetched, int inserted, int updated, int skipped, int failed,
            SyncTriggerType triggerType, String errorMessage) {
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.status = status;
        this.totalFetched = totalFetched;
        this.inserted = inserted;
        this.updated = updated;
        this.skipped = skipped;
        this.failed = failed;
        this.triggerType = triggerType;
        this.errorMessage = errorMessage;
    }
}
```

### 3. `backend/main/java/com/linkup/Petory/domain/location/repository/LocationSyncLogRepository.java` (신규)

```java
package com.linkup.Petory.domain.location.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.location.entity.LocationSyncLog;

public interface LocationSyncLogRepository extends JpaRepository<LocationSyncLog, Long> {
}
```

## Acceptance Criteria

- [ ] `./gradlew compileJava` — 컴파일 성공.
- [ ] MySQL(`petory_test` 또는 로컬 `petory`)이 뜬 상태에서 앱을 dev 프로필로 기동해 Flyway가 V8을 적용하고 `ddl-auto=validate`가 통과하는지 확인:
  - `./gradlew bootRun --args='--spring.profiles.active=dev --petory.scheduling.enabled=false'` 로 기동 → 로그에 `Migrating schema ... to version 8` + `Started PetoryApplication` 확인 후 종료.
  - 검증만 하려면 기동 후 바로 Ctrl-C. 기동에 성공하면 엔티티/스키마 일치가 증명된 것.
- [ ] `SHOW CREATE TABLE location_sync_log;` 로 컬럼/인덱스가 SQL과 일치하는지 확인(선택).

## 커밋

```bash
git add backend/main/resources/db/migration/V8__location_sync_log.sql \
        backend/main/java/com/linkup/Petory/domain/location/entity/LocationSyncLog.java \
        backend/main/java/com/linkup/Petory/domain/location/repository/LocationSyncLogRepository.java
git commit -m "feat(location): 공공데이터 동기화 실행이력 테이블(V8) 및 LocationSyncLog 엔티티 추가"
```
