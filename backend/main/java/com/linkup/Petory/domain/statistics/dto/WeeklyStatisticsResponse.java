package com.linkup.Petory.domain.statistics.dto;

import com.linkup.Petory.domain.statistics.entity.WeeklyStatistics;
import lombok.Builder;
import lombok.Getter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Getter @Builder
/** 주간 통계 API 응답 DTO. DailyStatisticsResponse 의 중첩 Stats 클래스를 재사용한다. */
public class WeeklyStatisticsResponse {

    private Integer year;
    private Integer weekNumber;
    private LocalDate startDate;
    private LocalDate endDate;
    private DailyStatisticsResponse.UserStats users;
    private BigDecimal weeklyRetentionRate;
    private DailyStatisticsResponse.CareStats care;
    private DailyStatisticsResponse.RevenueStats revenue;
    private DailyStatisticsResponse.CommunityStats community;
    private DailyStatisticsResponse.ModerationStats moderation;

    public static WeeklyStatisticsResponse from(WeeklyStatistics s) {
        return WeeklyStatisticsResponse.builder()
                .year(s.getYear())
                .weekNumber(s.getWeekNumber())
                .startDate(s.getStartDate())
                .endDate(s.getEndDate())
                .users(DailyStatisticsResponse.UserStats.builder()
                        .newUsers(s.getNewUsers())
                        .activeUsers(s.getActiveUsers())
                        .newProviders(s.getNewProviders())
                        .build())
                .weeklyRetentionRate(s.getWeeklyRetentionRate())
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
