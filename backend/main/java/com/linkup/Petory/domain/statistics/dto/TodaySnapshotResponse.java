package com.linkup.Petory.domain.statistics.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.linkup.Petory.domain.statistics.entity.DailyStatistics;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
/**
 * 오늘 실시간 통계 스냅샷 응답 DTO. asOf 필드로 조회 시각을 함께 반환한다.
 */
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
