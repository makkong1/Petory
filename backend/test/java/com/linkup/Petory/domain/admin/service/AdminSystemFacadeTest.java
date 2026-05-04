package com.linkup.Petory.domain.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.admin.entity.SystemConfig;
import com.linkup.Petory.domain.admin.repository.SystemConfigRepository;

@ExtendWith(MockitoExtension.class)
class AdminSystemFacadeTest {

    @InjectMocks
    private AdminSystemFacade facade;

    @Mock
    private SystemConfigRepository configRepository;
    @Mock
    private AdminAuditService auditService;

    @Test
    @DisplayName("정상: 기존 설정 수정 시 description도 함께 갱신한다")
    void 정상_기존설정_description갱신() {
        SystemConfig config = SystemConfig.builder()
                .idx(1L)
                .configKey("site.name")
                .configValue("Petory")
                .description("old")
                .build();
        when(configRepository.findByConfigKey("site.name")).thenReturn(Optional.of(config));
        when(configRepository.save(any(SystemConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        facade.upsertConfig("site.name", "Petory 2", "new description", 1L);

        assertThat(config.getConfigValue()).isEqualTo("Petory 2");
        assertThat(config.getDescription()).isEqualTo("new description");
    }
}
