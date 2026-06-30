package com.linkup.Petory.domain.statistics.dto;

import java.math.BigDecimal;

import com.linkup.Petory.domain.statistics.entity.MonthlyStatistics;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
/**
 * 월간 통계 API 응답 DTO. 리텐션율(monthlyRetentionRate)과 이탈율(churnRate)을 포함한다.
 */
public class MonthlyStatisticsResponse {

    private Integer year;
    private Integer month;
    private DailyStatisticsResponse.UserStats users;
    private BigDecimal monthlyRetentionRate;
    private BigDecimal churnRate;
    private DailyStatisticsResponse.CareStats care;
    private DailyStatisticsResponse.RevenueStats revenue;
    private DailyStatisticsResponse.CommunityStats community;
    private DailyStatisticsResponse.ModerationStats moderation;

    public static MonthlyStatisticsResponse from(MonthlyStatistics s) {
        return MonthlyStatisticsResponse.builder()
                .year(s.getYear())
                .month(s.getMonth())
                .users(DailyStatisticsResponse.UserStats.builder()
                        .newUsers(s.getNewUsers())
                        .activeUsers(s.getActiveUsers())
                        .newProviders(s.getNewProviders())
                        .build())
                .monthlyRetentionRate(s.getMonthlyRetentionRate())
                .churnRate(s.getChurnRate())
                .care(DailyStatisticsResponse.CareStats.builder()
                        .newRequests(s.getNewCareRequests())
                        .completed(s.getCompletedCares())
                        .cancelled(s.getCancelledCares())
                        .completionRate(s.getCareCompletionRate())
                        .build())
                .revenue(DailyStatisticsResponse.RevenueStats.builder()
                        .totalRevenue(s.getTotalRevenue())
                        .transactionCount(s.getTransactionCount())
                        .avgTransaction(s.getAvgTransaction())
                        .build())
                .community(DailyStatisticsResponse.CommunityStats.builder()
                        .newPosts(s.getNewPosts())
                        .newMeetups(s.getNewMeetups())
                        .meetupParticipants(s.getMeetupParticipants())
                        .build())
                .moderation(DailyStatisticsResponse.ModerationStats.builder()
                        .newReports(s.getNewReports())
                        .resolvedReports(s.getResolvedReports())
                        .build())
                .build();
    }
}
