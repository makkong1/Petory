package com.linkup.Petory.domain.statistics.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
/**
 * 일별 통계 API 응답 DTO. 사용자·케어·결제·커뮤니티·운영 섹션으로 그룹화된다.
 */
public class DailyStatisticsResponse {

    private LocalDate statDate;
    private UserStats users;
    private CareStats care;
    private RevenueStats revenue;
    private CommunityStats community;
    private ModerationStats moderation;

    @Getter
    @Builder
    public static class UserStats {

        private Long newUsers;
        private Long activeUsers;
        private Long newProviders;
    }

    @Getter
    @Builder
    public static class CareStats {

        private Long newRequests;
        private Long completed;
        private Long cancelled;
        private BigDecimal completionRate;
    }

    @Getter
    @Builder
    public static class RevenueStats {

        private BigDecimal totalRevenue;
        private Long transactionCount;
        private BigDecimal avgTransaction;
    }

    @Getter
    @Builder
    public static class CommunityStats {

        private Long newPosts;
        private Long newMeetups;
        private Long meetupParticipants;
    }

    @Getter
    @Builder
    public static class ModerationStats {

        private Long newReports;
        private Long resolvedReports;
    }

    public static DailyStatisticsResponse from(DailyStatistics s) {
        return DailyStatisticsResponse.builder()
                .statDate(s.getStatDate())
                .users(UserStats.builder()
                        .newUsers(s.getNewUsers())
                        .activeUsers(s.getActiveUsers())
                        .newProviders(s.getNewProviders())
                        .build())
                .care(CareStats.builder()
                        .newRequests(s.getNewCareRequests())
                        .completed(s.getCompletedCares())
                        .cancelled(s.getCancelledCares())
                        .completionRate(s.getCareCompletionRate())
                        .build())
                .revenue(RevenueStats.builder()
                        .totalRevenue(s.getTotalRevenue())
                        .transactionCount(s.getTransactionCount())
                        .avgTransaction(s.getAvgTransaction())
                        .build())
                .community(CommunityStats.builder()
                        .newPosts(s.getNewPosts())
                        .newMeetups(s.getNewMeetups())
                        .meetupParticipants(s.getMeetupParticipants())
                        .build())
                .moderation(ModerationStats.builder()
                        .newReports(s.getNewReports())
                        .resolvedReports(s.getResolvedReports())
                        .build())
                .build();
    }
}
