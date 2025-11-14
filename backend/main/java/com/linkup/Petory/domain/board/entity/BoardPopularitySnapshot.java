package com.linkup.Petory.domain.board.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "board_popularity_snapshot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardPopularitySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "board_id", nullable = false)
    private Board board;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false)
    private PopularityPeriodType periodType;

    @Column(name = "period_start_date", nullable = false)
    private LocalDate periodStartDate;

    @Column(name = "period_end_date", nullable = false)
    private LocalDate periodEndDate;

    @Column(name = "ranking", nullable = false)
    private Integer ranking;

    @Column(name = "popularity_score", nullable = false)
    private Integer popularityScore;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount;

    @Column(name = "comment_count", nullable = false)
    private Integer commentCount;

    @Column(name = "view_count", nullable = false)
    private Integer viewCount;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
