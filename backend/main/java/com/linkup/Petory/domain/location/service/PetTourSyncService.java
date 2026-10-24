package com.linkup.Petory.domain.location.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.linkup.Petory.domain.location.dto.PublicDataLocationDTO;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.entity.LocationSyncLog;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncSource;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncTriggerType;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * TourAPI 반려동물 동반여행 장소를 locationservice 에 적재한다.
 *
 * <p>
 * upsert·중복제거·배치·이력은 {@link PublicDataSyncService#sync} 를 그대로 쓴다. 이 클래스가 더하는 것은
 * <b>출처 표시</b>와, 그와 분리된 <b>동반 구역 백필</b>이다.
 *
 * <h2>🔴 왜 적재와 상세 조회를 분리했는가 (2026-10-24 두 번 깨지고 나서)</h2>
 *
 * 처음에는 신규 행을 적재하면서 그 자리에서 {@code detailPetTour2} 를 1회씩 불렀다. 두 번 깨졌다.
 *
 * <ol>
 * <li><b>한 행이 전부를 무너뜨렸다.</b> 긴 안내문 1건이 {@code Data too long} 을 던지자 배치
 * 트랜잭션이 rollback-only 로 표시돼 <b>960건 전부가 롤백되고 상세 호출 960회도 함께 버려졌다.</b></li>
 * <li><b>일일 한도(1,000회)를 넘겼다.</b> 장소 1,022건이면 상세만 960회다. 한 번 실패하면 그날은
 * 재시도할 여유가 없고, 적재가 성공해도 상세가 실패한 행은 <b>영구히 null 로 남는다</b>
 * (이미 있는 행은 다시 보지 않으므로).</li>
 * </ol>
 *
 * 그래서 쪼갰다.
 *
 * <ul>
 * <li>{@link #sync} — 목록 조회 11회뿐이고 외부 호출이 행 수에 비례하지 않는다. 한도와 무관하게 끝난다.</li>
 * <li>{@link #backfillPetZoneType} — <b>아직 빈 행만</b> 골라 limit 만큼 채운다. 중간에 끊겨도
 * 다음 실행이 이어받고, 여러 날에 걸쳐 수렴한다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetTourSyncService {

    static final String DATA_SOURCE = "TOUR_API";

    /**
     * 한 번에 채울 최대 행 수. 일일 한도 1,000회에서 목록 11회와 여유를 뺀 값이다.
     * 1,022건이면 이틀에 수렴한다.
     */
    static final int BACKFILL_LIMIT = 800;

    /**
     * 연속 실패가 이만큼 나오면 멈춘다. 한도 소진은 남은 행 전부에서 똑같이 실패하므로,
     * 계속 돌면 아무것도 못 채우면서 시간만 쓴다.
     */
    private static final int CONSECUTIVE_FAILURE_LIMIT = 5;

    /**
     * {@code locationservice.pet_restrictions} 길이. {@code etcAcmpyInfo} 는 길이 제한이 없는
     * 안내문이라 255 자를 넘는 행이 있다 — 위 1번 사고의 직접 원인이다.
     */
    private static final int PET_RESTRICTIONS_MAX = 255;

    private final PetTourApiClient apiClient;
    private final PublicDataSyncService syncService;
    private final LocationServiceRepository locationServiceRepository;

    /**
     * 장소 적재. 목록 조회만 하므로 한도에 걸리지 않는다.
     * 동반 구역은 {@link #backfillPetZoneType} 이 따로 채운다.
     */
    public LocationSyncLog sync(SyncTriggerType triggerType) {
        // updateExisting=false — TourAPI 는 칸이 적다(설명·홈페이지·운영시간·요금이 아예 없다).
        // 덮어쓰면 기존 행의 그 칸들이 null 로 지워진다. 이 데이터셋의 값은 「새 장소」뿐이다.
        return syncService.sync(SyncSource.PET_TOUR, triggerType, apiClient::fetchAll, this::markSource, false);
    }

    /** 기본 한도({@link #BACKFILL_LIMIT})로 채운다. */
    public int backfillPetZoneType() {
        return backfillPetZoneType(BACKFILL_LIMIT);
    }

    /**
     * 동반 가능 구역이 빈 TOUR_API 행을 limit 개까지 채운다.
     *
     * @return 실제로 채운 행 수
     */
    int backfillPetZoneType(int limit) {
        List<LocationService> targets = locationServiceRepository.findMissingPetZoneType(DATA_SOURCE, limit);
        if (targets.isEmpty()) {
            return 0;
        }

        int filled = 0;
        int consecutiveFailures = 0;
        for (LocationService place : targets) {
            PetTourApiClient.PetTourDetail detail = apiClient.fetchPetDetail(place.getTourContentId());
            if (detail == null || detail.acmpyTypeCd() == null) {
                if (++consecutiveFailures >= CONSECUTIVE_FAILURE_LIMIT) {
                    log.warn("동반구역 백필 중단: 연속 {}회 실패(한도 소진 추정). 채움={}, 남음={}",
                            consecutiveFailures, filled, targets.size() - filled);
                    break;
                }
                continue;
            }
            consecutiveFailures = 0;

            place.setPetZoneType(detail.acmpyTypeCd());
            // 「야외 테라스존만 허용」처럼 제한 서술이 들어온다. 기존 컬럼과 뜻이 같아 그대로 쓴다.
            if (detail.etcAcmpyInfo() != null) {
                place.setPetRestrictions(clip(detail.etcAcmpyInfo()));
            }
            locationServiceRepository.save(place);
            filled++;
        }

        log.info("동반구역 백필 완료: 대상={}, 채움={}", targets.size(), filled);
        return filled;
    }

    /**
     * 「갈 만한 곳」 섹션이 이 장소들을 24,000여 건 속에서 골라내야 한다.
     * 기존 출처는 {@code PUBLIC}·{@code PET_DATA_API}·{@code BATCH_IMPORT} 다.
     */
    private void markSource(PublicDataLocationDTO dto, LocationService entity) {
        entity.setDataSource(DATA_SOURCE);
        entity.setTourContentId(dto.getContentId());
    }

    /** 잘렸다는 걸 보이게 끝에 … 를 남긴다. 말없이 자르면 문장이 끊긴 원문처럼 읽힌다. */
    private static String clip(String text) {
        if (text.length() <= PET_RESTRICTIONS_MAX) {
            return text;
        }
        return text.substring(0, PET_RESTRICTIONS_MAX - 1) + "…";
    }
}
