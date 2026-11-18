package com.linkup.Petory.domain.location.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.linkup.Petory.domain.location.entity.LocationSyncLog;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncTriggerType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 공공데이터 시설 동기화 스케줄러. 매일 새벽 3시에 전체 upsert 를 실행한다.
 * 스케줄링 on/off 는 SchedulingConfig(petory.scheduling.enabled)가 중앙 제어.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicDataSyncScheduler {

    private final PublicDataSyncService publicDataSyncService;
    private final PetTourSyncService petTourSyncService;
    private final SeoulPetNewsSyncService seoulPetNewsSyncService;

    /**
     * 기본 매일 18:10 실행. 스케줄러 스레드가 1개뿐이라 자정(4개)·정각(2개)·17:10·18:30 슬롯을 피한다.
     * cron 을 속성으로 뺀 이유는 {@link #runDailyPetTourSync} 와 같다 — 적재는 운영 작업이고,
     * 실행 시점을 기동 인자로 정할 수 있어야 한다.
     */
    @Scheduled(cron = "${petory.location.public-data-sync-cron:0 10 18 * * *}")
    public void runDailySync() {
        try {
            LocationSyncLog result = publicDataSyncService.syncFromApi(SyncTriggerType.SCHEDULED);
            log.info("공공데이터 정기 동기화 종료: status={}, 신규={}, 갱신={}, 스킵={}, 실패={}",
                    result.getStatus(), result.getInserted(), result.getUpdated(),
                    result.getSkipped(), result.getFailed());
        } catch (Exception e) {
            // syncFromApi 내부에서 대부분 처리되지만, 최후 방어로 스케줄러 스레드가 죽지 않게 감싼다.
            log.error("공공데이터 정기 동기화 중 예외: {}", e.getMessage(), e);
        }
    }

    /**
     * TourAPI 반려동물 동반여행 정기 동기화. 기본 매일 19:10.
     * 18:10(문화시설)·18:30(게시글 인기도)과 겹치지 않게 뒀다 — 스케줄러 스레드가 1개뿐이고,
     * 적재 자체는 목록 조회 11회로 끝나고, 이어지는 동반구역 백필이 행마다 상세를 1회씩 부른다.
     *
     * <p>
     * cron 을 속성으로 뺀 이유: 최초 적재를 기다리지 않고 돌려야 한다. 관리자 엔드포인트
     * ({@code POST /api/admin/locations/sync-pet-tour})는 MASTER 토큰이 필요한데, 적재는
     * 운영 작업이라 실행 시점을 기동 인자로 정할 수 있어야 편하다.
     */
    /**
     * 서울시 보도자료 「반려동물」 소식 정기 수집. 기본 매일 20:10.
     *
     * <p>
     * 18:10(문화시설)·18:30(게시글 인기도)·19:10(TourAPI)과 겹치지 않게 뒀다 — 스케줄러 스레드가
     * 1개뿐이고, 이 작업은 외부 사이트를 긁으면서 페이지마다 1초를 쉰다.
     *
     * <p>
     * <b>1페이지(10건)만 본다.</b> 신규는 맨 앞에 쌓이고, 이미 있는 행은 적재 단계에서
     * {@code skippedUnchanged} 로 빠진다. 전량 적재는 관리자 엔드포인트
     * ({@code POST /api/admin/location-services/sync-pet-news?maxPages=22})로 한 번만 한다.
     */
    @Scheduled(cron = "${petory.location.pet-news-sync-cron:0 10 20 * * *}")
    public void runDailyPetNewsSync() {
        try {
            LocationSyncLog result = seoulPetNewsSyncService.sync(SyncTriggerType.SCHEDULED, 1);
            log.info("서울시 보도자료 정기 수집 종료: status={}, 신규={}, 스킵={}, 실패={}",
                    result.getStatus(), result.getInserted(), result.getSkipped(), result.getFailed());
        } catch (Exception e) {
            // 서비스 안에서 대부분 처리되지만, 최후 방어로 스케줄러 스레드가 죽지 않게 감싼다.
            log.error("서울시 보도자료 정기 수집 중 예외: {}", e.getMessage(), e);
        }
    }

    @Scheduled(cron = "${petory.location.pet-tour-sync-cron:0 10 19 * * *}")
    public void runDailyPetTourSync() {
        try {
            LocationSyncLog result = petTourSyncService.sync(SyncTriggerType.SCHEDULED);
            log.info("TourAPI 정기 동기화 종료: status={}, 신규={}, 갱신={}, 스킵={}, 실패={}",
                    result.getStatus(), result.getInserted(), result.getUpdated(),
                    result.getSkipped(), result.getFailed());
            // 동반구역은 장소당 상세 1회라 일일 한도에 묶인다. 빈 행만 골라 나눠 채운다.
            log.info("TourAPI 동반구역 백필: 채움={}", petTourSyncService.backfillPetZoneType());
        } catch (Exception e) {
            log.error("TourAPI 정기 동기화 중 예외: {}", e.getMessage(), e);
        }
    }
}
