package com.linkup.Petory.domain.location.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.location.entity.LocationSyncLog;
import com.linkup.Petory.domain.location.entity.LocationSyncLog.SyncTriggerType;

@ExtendWith(MockitoExtension.class)
class PublicDataSyncSchedulerTest {

    @Mock PublicDataSyncService syncService;
    @InjectMocks PublicDataSyncScheduler scheduler;

    @Test
    void 매일_동기화는_SCHEDULED_트리거로_위임한다() {
        when(syncService.syncFromApi(eq(SyncTriggerType.SCHEDULED)))
                .thenReturn(LocationSyncLog.builder()
                        .status(LocationSyncLog.SyncStatus.SUCCESS).build());

        scheduler.runDailySync();

        verify(syncService).syncFromApi(SyncTriggerType.SCHEDULED);
    }

    @Test
    void 서비스_예외는_스케줄러_밖으로_전파되지_않는다() {
        when(syncService.syncFromApi(eq(SyncTriggerType.SCHEDULED)))
                .thenThrow(new RuntimeException("boom"));

        assertThatCode(() -> scheduler.runDailySync()).doesNotThrowAnyException();
    }
}
