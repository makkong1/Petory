package com.linkup.Petory.domain.statistics.entity;

import com.linkup.Petory.domain.common.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "weekly_statistics",
       uniqueConstraints = @UniqueConstraint(columnNames = {"year", "week_number"}))
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
/** 주 단위로 집계한 플랫폼 통계. ISO 주차(year + weekNumber) 기준으로 유니크하다. */
public class WeeklyStatistics extends BaseTimeEntity {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "year", nullable = false) private Integer year;
    @Column(name = "week_number", nullable = false) private Integer weekNumber;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;

    @Builder.Default @Column(name = "new_users") private Long newUsers = 0L;
    @Builder.Default @Column(name = "active_users") private Long activeUsers = 0L;
    @Builder.Default @Column(name = "new_providers") private Long newProviders = 0L;
    @Builder.Default @Column(name = "weekly_retention_rate", precision = 5, scale = 2)
    private BigDecimal weeklyRetentionRate = BigDecimal.ZERO;

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

}
