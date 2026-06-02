package com.linkup.Petory.domain.admin.service;

import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.admin.entity.SystemConfig;
import com.linkup.Petory.domain.admin.repository.SystemConfigRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
/** 시스템 설정(SystemConfig) 조회·변경을 담당하는 퍼사드. MASTER 권한 전용. */
public class AdminSystemFacade {

    private final SystemConfigRepository configRepository;
    private final AdminAuditService auditService;

    public Map<String, String> getAllConfigs() {
        return configRepository.findAll().stream()
                .collect(Collectors.toMap(SystemConfig::getConfigKey, SystemConfig::getConfigValue, (a, b) -> b));
    }

    public String getConfig(String key, String defaultValue) {
        return configRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElse(defaultValue);
    }

    @Transactional
    public void upsertConfig(String key, String value, String description, Long adminIdx) {
        SystemConfig config = configRepository.findByConfigKey(key)
                .orElseGet(() -> SystemConfig.builder()
                        .configKey(key)
                        .description(description)
                        .build());
        config.setConfigValue(value);
        if (description != null) {
            config.setDescription(description);
        }
        configRepository.save(config);
        log.info("시스템 설정 변경: key={}, adminIdx={}", key, adminIdx);
        auditService.log(adminIdx, "SYSTEM_CONFIG_UPDATE", "SYSTEM", null, key + "=" + value);
    }

    @Transactional
    public void upsertConfigs(Map<String, String> settings, Long adminIdx) {
        settings.forEach((key, value) -> upsertConfig(key, value, null, adminIdx));
    }
}
