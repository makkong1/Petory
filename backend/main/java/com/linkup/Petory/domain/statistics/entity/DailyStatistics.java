package com.linkup.Petory.domain.statistics.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_statistics")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class DailyStatistics {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stat_date", unique = true, nullable = false)
    private LocalDate statDate;

    // 사용자
    @Builder.Default @Column(name = "new_users") private Long newUsers = 0L;
    @Builder.Default @Column(name = "active_users") private Long activeUsers = 0L;
    @Builder.Default @Column(name = "new_providers") private Long newProviders = 0L;

    // 케어
    @Builder.Default @Column(name = "new_care_requests") private Long newCareRequests = 0L;
    @Builder.Default @Column(name = "completed_cares") private Long completedCares = 0L;
    @Builder.Default @Column(name = "cancelled_cares") private Long cancelledCares = 0L;
    @Builder.Default @Column(name = "care_completion_rate", precision = 5, scale = 2)
    private BigDecimal careCompletionRate = BigDecimal.ZERO;

    // 결제 (이벤트 즉시 반영)
    @Builder.Default @Column(name = "total_revenue", precision = 15, scale = 2)
    private BigDecimal totalRevenue = BigDecimal.ZERO;
    @Builder.Default @Column(name = "transaction_count") private Long transactionCount = 0L;
    @Builder.Default @Column(name = "avg_transaction", precision = 15, scale = 2)
    private BigDecimal avgTransaction = BigDecimal.ZERO;

    // 커뮤니티
    @Builder.Default @Column(name = "new_posts") private Long newPosts = 0L;
    @Builder.Default @Column(name = "new_meetups") private Long newMeetups = 0L;
    @Builder.Default @Column(name = "meetup_participants") private Long meetupParticipants = 0L;

    // 운영
    @Builder.Default @Column(name = "new_reports") private Long newReports = 0L;
    @Builder.Default @Column(name = "resolved_reports") private Long resolvedReports = 0L;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    @PreUpdate protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
