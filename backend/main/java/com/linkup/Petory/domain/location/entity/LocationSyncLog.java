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
