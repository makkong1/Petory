package com.linkup.Petory.domain.location.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 배치 저장 전용 빈.
 * {@code @Transactional(REQUIRES_NEW)}가 AOP 프록시를 통해 실제 적용되도록
 * PublicDataLocationService에서 분리되었습니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceBatchWriter {

    private final LocationServiceRepository locationServiceRepository;

    @PersistenceContext
    private EntityManager entityManager;

    /**
     * 배치 저장 (각 배치는 별도 트랜잭션으로 처리)
     * 일부 실패해도 다른 배치는 저장됨.
     *
     * @param batch 저장할 엔티티 목록
     * @return 실제 저장된 개수
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int saveBatch(List<LocationService> batch) {
        if (batch.isEmpty()) {
            return 0;
        }

        try {
            locationServiceRepository.saveAll(batch);
            entityManager.flush();
            updateLocationPoints(batch);
            return batch.size();
        } catch (Exception e) {
            log.error("배치 저장 실패: {}개 중 일부 저장 실패 - {}", batch.size(), e.getMessage(), e);
            entityManager.clear();

            // 개별 저장 시도
            int saved = 0;
            List<LocationService> failedEntities = new ArrayList<>();

            for (LocationService entity : batch) {
                try {
                    if (entityManager.contains(entity)) {
                        entityManager.detach(entity);
                    }
                    locationServiceRepository.save(entity);
                    entityManager.flush();
                    updateLocationPoints(List.of(entity));
                    saved++;
                } catch (Exception ex) {
                    log.warn("개별 저장 실패: {}", ex.getMessage());
                    failedEntities.add(entity);
                    entityManager.clear();
                }
            }

            if (!failedEntities.isEmpty()) {
                entityManager.clear();
            }

            return saved;
        }
    }

    private void updateLocationPoints(List<LocationService> entities) {
        List<Long> ids = entities.stream()
                .filter(e -> e.getIdx() != null && e.getLatitude() != null && e.getLongitude() != null)
                .map(LocationService::getIdx)
                .toList();
        if (ids.isEmpty()) return;

        entityManager.createNativeQuery(
                "UPDATE locationservice " +
                "SET location = ST_GeomFromText(CONCAT('POINT(', longitude, ' ', latitude, ')'), 4326) " +
                "WHERE idx IN :ids")
                .setParameter("ids", ids)
                .executeUpdate();
    }
}
