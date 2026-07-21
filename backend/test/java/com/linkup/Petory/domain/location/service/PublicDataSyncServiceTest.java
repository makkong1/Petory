package com.linkup.Petory.domain.location.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.location.dto.PublicDataLocationDTO;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.entity.LocationSyncLog;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncStatus;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncTriggerType;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import com.linkup.Petory.domain.location.repository.LocationSyncLogRepository;

@ExtendWith(MockitoExtension.class)
class PublicDataSyncServiceTest {

    @Mock PublicDataApiClient apiClient;
    @Mock PublicDataLocationService conversion;
    @Mock LocationServiceRepository locationServiceRepository;
    @Mock LocationServiceBatchWriter batchWriter;
    @Mock LocationSyncLogRepository syncLogRepository;
    @InjectMocks PublicDataSyncService service;

    private PublicDataLocationDTO dto(String name, String addr) {
        PublicDataLocationDTO d = new PublicDataLocationDTO();
        d.setFacilityName(name);
        d.setRoadAddress(addr);
        return d;
    }

    private LocationService entity(String name, String addr, String phone) {
        return LocationService.builder().name(name).address(addr).phone(phone).build();
    }

    private void stubValidConversion(PublicDataLocationDTO dto, LocationService entity) {
        when(conversion.isValid(dto)).thenReturn(true);
        when(conversion.buildDedupKey(dto)).thenReturn(dto.getFacilityName() + "|" + dto.getRoadAddress());
        when(conversion.convertToEntity(dto)).thenReturn(entity);
    }

    @Test
    void 신규는_insertBatch로_저장된다() {
        PublicDataLocationDTO d = dto("병원A", "서울 강남");
        LocationService e = entity("병원A", "서울 강남", "010");
        when(apiClient.fetchAll()).thenReturn(List.of(d));
        stubValidConversion(d, e);
        when(locationServiceRepository.findFirstByNameAndAddress("병원A", "서울 강남")).thenReturn(Optional.empty());
        when(batchWriter.saveBatch(any())).thenReturn(1);
        when(syncLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LocationSyncLog log = service.syncFromApi(SyncTriggerType.MANUAL);

        assertThat(log.getInserted()).isEqualTo(1);
        assertThat(log.getUpdated()).isZero();
        assertThat(log.getStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    void 내용이_바뀌면_updateBatch로_갱신된다() {
        PublicDataLocationDTO d = dto("병원A", "서울 강남");
        LocationService incoming = entity("병원A", "서울 강남", "010-NEW");
        LocationService existing = entity("병원A", "서울 강남", "010-OLD");
        when(apiClient.fetchAll()).thenReturn(List.of(d));
        stubValidConversion(d, incoming);
        when(locationServiceRepository.findFirstByNameAndAddress("병원A", "서울 강남")).thenReturn(Optional.of(existing));
        when(batchWriter.updateBatch(any())).thenReturn(1);
        when(syncLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LocationSyncLog log = service.syncFromApi(SyncTriggerType.MANUAL);

        assertThat(log.getUpdated()).isEqualTo(1);
        assertThat(existing.getPhone()).isEqualTo("010-NEW"); // 공공필드 복사됨
    }

    @Test
    void 내용이_동일하면_skip된다() {
        PublicDataLocationDTO d = dto("병원A", "서울 강남");
        LocationService incoming = entity("병원A", "서울 강남", "010");
        LocationService existing = entity("병원A", "서울 강남", "010");
        when(apiClient.fetchAll()).thenReturn(List.of(d));
        stubValidConversion(d, incoming);
        when(locationServiceRepository.findFirstByNameAndAddress("병원A", "서울 강남")).thenReturn(Optional.of(existing));
        when(syncLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LocationSyncLog log = service.syncFromApi(SyncTriggerType.MANUAL);

        assertThat(log.getSkipped()).isEqualTo(1);
        assertThat(log.getInserted()).isZero();
        assertThat(log.getUpdated()).isZero();
    }

    @Test
    void API_조회_실패시_FAILED로_기록한다() {
        when(apiClient.fetchAll()).thenThrow(new RuntimeException("timeout"));
        when(syncLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LocationSyncLog log = service.syncFromApi(SyncTriggerType.SCHEDULED);

        assertThat(log.getStatus()).isEqualTo(SyncStatus.FAILED);
        assertThat(log.getErrorMessage()).contains("timeout");
    }

    @Test
    void 일부_배치_실패시_PARTIAL로_기록한다() {
        PublicDataLocationDTO d = dto("병원A", "서울 강남");
        LocationService e = entity("병원A", "서울 강남", "010");
        when(apiClient.fetchAll()).thenReturn(List.of(d));
        stubValidConversion(d, e);
        when(locationServiceRepository.findFirstByNameAndAddress(anyString(), anyString())).thenReturn(Optional.empty());
        when(batchWriter.saveBatch(any())).thenReturn(0); // 1건 중 0건 저장 → 1 실패
        when(syncLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        LocationSyncLog log = service.syncFromApi(SyncTriggerType.MANUAL);

        assertThat(log.getFailed()).isEqualTo(1);
        assertThat(log.getStatus()).isEqualTo(SyncStatus.PARTIAL);
    }
}
