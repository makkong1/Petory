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

    /** 아래 세 사유의 합계. 사유별 칸은 V22 이전 행에서 null 이다. */
    @Column(name = "skipped", nullable = false)
    private int skipped;

    /** 시설명·주소가 없어 쓸 수 없던 행. 높으면 원본·매핑이 깨진 것이다. */
    @Column(name = "skipped_invalid")
    private Integer skippedInvalid;

    /** 같은 실행 안에서 이미 본 행. 높으면 원본 품질 문제다. */
    @Column(name = "skipped_duplicate")
    private Integer skippedDuplicate;

    /** 이미 있고 내용도 같던 행. 높으면 멱등하게 정상 동작한 것이다. */
    @Column(name = "skipped_unchanged")
    private Integer skippedUnchanged;

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
            Integer skippedInvalid, Integer skippedDuplicate, Integer skippedUnchanged,
            SyncTriggerType triggerType, String errorMessage) {
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.status = status;
        this.totalFetched = totalFetched;
        this.inserted = inserted;
        this.updated = updated;
        this.skipped = skipped;
        this.skippedInvalid = skippedInvalid;
        this.skippedDuplicate = skippedDuplicate;
        this.skippedUnchanged = skippedUnchanged;
        this.failed = failed;
        this.triggerType = triggerType;
        this.errorMessage = errorMessage;
    }
}
