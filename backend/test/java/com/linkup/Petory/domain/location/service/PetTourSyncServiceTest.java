package com.linkup.Petory.domain.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.location.dto.PublicDataLocationDTO;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncSource;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncTriggerType;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;

/**
 * 적재 경로와 동반구역 백필 경로를 따로 본다 — 2026-10-24 에 둘을 한 경로로 묶었다가 두 번 깨졌다
 * (한 행의 Data too long 으로 960건 전부 롤백 / 일일 한도 초과로 영구 null).
 */
@ExtendWith(MockitoExtension.class)
class PetTourSyncServiceTest {

    @Mock PetTourApiClient apiClient;
    @Mock PublicDataSyncService syncService;
    @Mock LocationServiceRepository locationServiceRepository;
    @InjectMocks PetTourSyncService service;

    private LocationService place(String contentId) {
        return LocationService.builder().name("가평카페109").tourContentId(contentId).build();
    }

    @SuppressWarnings("unchecked")
    private BiConsumer<PublicDataLocationDTO, LocationService> captureOnInsert() {
        service.sync(SyncTriggerType.MANUAL);
        ArgumentCaptor<BiConsumer<PublicDataLocationDTO, LocationService>> captor =
                ArgumentCaptor.forClass(BiConsumer.class);
        // updateExisting=false — 덮어쓰면 더 빈약한 값이 더 나은 행을 지운다.
        verify(syncService).sync(eq(SyncSource.PET_TOUR), eq(SyncTriggerType.MANUAL), any(), captor.capture(),
                eq(false));
        return captor.getValue();
    }

    @Test
    void 적재는_출처와_contentId만_남기고_외부호출을_하지_않는다() {
        PublicDataLocationDTO dto = new PublicDataLocationDTO();
        dto.setContentId("3083766");
        LocationService entity = LocationService.builder().name("가평카페109").build();

        captureOnInsert().accept(dto, entity);

        assertThat(entity.getDataSource()).isEqualTo("TOUR_API");
        // 상세를 적재 중에 부르면 1,022건이 일일 한도를 넘고, 한 행이 터질 때 전부 롤백된다.
        assertThat(entity.getTourContentId()).isEqualTo("3083766");
        verify(apiClient, never()).fetchPetDetail(anyString());
    }

    @Test
    void 백필은_빈_행의_동반구역을_채운다() {
        LocationService target = place("3083766");
        when(locationServiceRepository.findMissingPetZoneType("TOUR_API", 800)).thenReturn(List.of(target));
        when(apiClient.fetchPetDetail("3083766"))
                .thenReturn(new PetTourApiClient.PetTourDetail("일부구역 동반가능", "야외 테라스존만 허용"));

        assertThat(service.backfillPetZoneType()).isEqualTo(1);

        assertThat(target.getPetZoneType()).isEqualTo("일부구역 동반가능");
        assertThat(target.getPetRestrictions()).isEqualTo("야외 테라스존만 허용");
        verify(locationServiceRepository).save(target);
    }

    /** 🔴 2026-10-24 전량 적재(960건)가 통째로 롤백된 원인. pet_restrictions 는 VARCHAR(255) 다. */
    @Test
    void 긴_안내문은_컬럼_길이로_자른다() {
        LocationService target = place("3083766");
        when(locationServiceRepository.findMissingPetZoneType(anyString(), anyInt())).thenReturn(List.of(target));
        when(apiClient.fetchPetDetail("3083766"))
                .thenReturn(new PetTourApiClient.PetTourDetail("전구역 동반가능", "가".repeat(400)));

        service.backfillPetZoneType();

        assertThat(target.getPetRestrictions()).hasSize(255).endsWith("…");
    }

    /**
     * 한도가 소진되면 남은 행 전부에서 똑같이 실패한다. 계속 돌면 아무것도 못 채우면서
     * 수백 번을 더 부른다 — 실제로 2026-10-24 에 그렇게 수십 분을 태웠다.
     */
    @Test
    void 연속_실패가_쌓이면_백필을_멈춘다() {
        List<LocationService> targets = IntStream.range(0, 50).mapToObj(i -> place("id" + i)).toList();
        when(locationServiceRepository.findMissingPetZoneType(anyString(), anyInt())).thenReturn(targets);
        when(apiClient.fetchPetDetail(anyString())).thenReturn(null); // 429 등

        assertThat(service.backfillPetZoneType()).isZero();

        verify(apiClient, never()).fetchPetDetail("id5"); // 5회 연속 실패에서 중단
        verify(locationServiceRepository, never()).save(any());
    }
}
