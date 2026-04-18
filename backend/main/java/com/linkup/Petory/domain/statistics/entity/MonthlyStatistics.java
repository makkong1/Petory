package com.linkup.Petory.domain.statistics.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "monthly_statistics",
       uniqueConstraints = @UniqueConstraint(columnNames = {"year", "month"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class MonthlyStatistics {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year", nullable = false) private Integer year;
    @Column(name = "month", nullable = false) private Integer month;

    @Builder.Default @Column(name = "new_users") private Long newUsers = 0L;
    @Builder.Default @Column(name = "active_users") private Long activeUsers = 0L;
    @Builder.Default @Column(name = "new_providers") private Long newProviders = 0L;
    @Builder.Default @Column(name = "monthly_retention_rate", precision = 5, scale = 2)
    private BigDecimal monthlyRetentionRate = BigDecimal.ZERO;
    @Builder.Default @Column(name = "churn_rate", precision = 5, scale = 2)
    private BigDecimal churnRate = BigDecimal.ZERO;

    @Builder.Default @Column(name = "new_care_requests") private Long newCareRequests = 0L;
    @Builder.Default @Column(name = "completed_cares") private Long completedCares = 0L;
    @Builder.Default @Column(name = "cancelled_cares") private Long cancelledCares = 0L;
    @Builder.Default @Column(name = "care_completion_rate", precision = 5, scale = 2)
    private BigDecimal careCompletionRate = BigDecimal.ZERO;

    @Builder.Default @Column(name = "total_revenue", precision = 15, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    @Builder.Default @Column(name = "transaction_count") private Long transactionCount = 0L;
    @Builder.Default @Column(name = "avg_transaction", precision = 15, scale = 2)
    private BigDecimal avgTransaction = BigDecimal.ZERO;

    @Builder.Default @Column(name = "new_posts") private Long newPosts = 0L;
    @Builder.Default @Column(name = "new_meetups") private Long newMeetups = 0L;
    @Builder.Default @Column(name = "meetup_participants") private Long meetupParticipants = 0L;

    @Builder.Default @Column(name = "new_reports") private Long newReports = 0L;
    @Builder.Default @Column(name = "resolved_reports") private Long resolvedReports = 0L;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
