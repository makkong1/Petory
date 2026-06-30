package com.linkup.Petory.domain.statistics.entity;

import java.math.BigDecimal;

import com.linkup.Petory.domain.common.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "monthly_statistics",
        uniqueConstraints = @UniqueConstraint(columnNames = {"year", "month"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * 월 단위로 집계한 플랫폼 통계. (year, month) 조합으로 유니크하다.
 */
public class MonthlyStatistics extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year", nullable = false)
    private Integer year;
    @Column(name = "month", nullable = false)
    private Integer month;

    @Builder.Default
    @Column(name = "new_users")
    private Long newUsers = 0L;
    @Builder.Default
    @Column(name = "active_users")
    private Long activeUsers = 0L;
    @Builder.Default
    @Column(name = "new_providers")
    private Long newProviders = 0L;
    @Builder.Default
    @Column(name = "monthly_retention_rate", precision = 5, scale = 2)
    private BigDecimal monthlyRetentionRate = BigDecimal.ZERO;
    @Builder.Default
    @Column(name = "churn_rate", precision = 5, scale = 2)
    private BigDecimal churnRate = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "new_care_requests")
    private Long newCareRequests = 0L;
    @Builder.Default
    @Column(name = "completed_cares")
    private Long completedCares = 0L;
    @Builder.Default
    @Column(name = "cancelled_cares")
    private Long cancelledCares = 0L;
    @Builder.Default
    @Column(name = "care_completion_rate", precision = 5, scale = 2)
    private BigDecimal careCompletionRate = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "total_revenue", precision = 15, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    @Builder.Default
    @Column(name = "transaction_count")
    private Long transactionCount = 0L;
    @Builder.Default
    @Column(name = "avg_transaction", precision = 15, scale = 2)
    private BigDecimal avgTransaction = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "new_posts")
    private Long newPosts = 0L;
    @Builder.Default
    @Column(name = "new_meetups")
    private Long newMeetups = 0L;
    @Builder.Default
    @Column(name = "meetup_participants")
    private Long meetupParticipants = 0L;

    @Builder.Default
    @Column(name = "new_reports")
    private Long newReports = 0L;
    @Builder.Default
    @Column(name = "resolved_reports")
    private Long resolvedReports = 0L;

}
