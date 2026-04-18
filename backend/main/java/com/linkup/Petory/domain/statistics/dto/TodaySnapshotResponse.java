package com.linkup.Petory.domain.statistics.dto;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;
import lombok.Builder;
import lombok.Getter;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter @Builder
public class TodaySnapshotResponse {
    private LocalDate statDate;
    private LocalDateTime asOf;
    private DailyStatisticsResponse.UserStats users;
    private DailyStatisticsResponse.CareStats care;
    private DailyStatisticsResponse.RevenueStats revenue;
    private DailyStatisticsResponse.CommunityStats community;
    private DailyStatisticsResponse.ModerationStats moderation;

    public static TodaySnapshotResponse from(DailyStatistics s) {
        DailyStatisticsResponse base = DailyStatisticsResponse.from(s);
        return TodaySnapshotResponse.builder()
                .statDate(s.getStatDate())
                .asOf(LocalDateTime.now())
                .users(base.getUsers())
                .care(base.getCare())
                .revenue(base.getRevenue())
                .community(base.getCommunity())
                .moderation(base.getModeration())
                .build();
    }
}
