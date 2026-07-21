package com.linkup.Petory.domain.location.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataAccessException;
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
 * 배치 저장 전용 빈. {@code @Transactional(REQUIRES_NEW)}가 AOP 프록시를 통해 실제 적용되도록
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
     * 배치 저장 (각 배치는 별도 트랜잭션으로 처리) 일부 실패해도 다른 배치는 저장됨.
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
            return batch.size();
        } catch (DataAccessException e) {
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
                    // 배치 실패 전 IDENTITY가 이미 배정된 엔티티가 섞여 있으면
                    // save()가 update로 오인해 실패한 행을 대상으로 UPDATE를 시도하다 실패함.
                    // 신규 임포트는 전부 insert이므로 idx를 초기화해 항상 insert로 처리되게 함.
                    entity.setIdx(null);
                    locationServiceRepository.save(entity);
                    saved++;
                } catch (DataAccessException ex) {
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

    /**
     * 업데이트 배치 저장 (각 배치 별도 트랜잭션). idx가 세팅된 detached 엔티티를 merge 한다.
     * {@link #saveBatch}와 달리 idx를 초기화하지 않는다(업데이트라서). 일부 실패해도 나머지는 저장됨.
     *
     * @param batch 업데이트할(기존 idx 보유) 엔티티 목록
     * @return 실제 저장된 개수
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int updateBatch(List<LocationService> batch) {
        if (batch.isEmpty()) {
            return 0;
        }
        try {
            locationServiceRepository.saveAll(batch);
            return batch.size();
        } catch (DataAccessException e) {
            log.error("업데이트 배치 실패: {}개 중 일부 실패 - {}", batch.size(), e.getMessage(), e);
            entityManager.clear();
            int saved = 0;
            for (LocationService entity : batch) {
                try {
                    locationServiceRepository.save(entity);
                    saved++;
                } catch (DataAccessException ex) {
                    log.warn("개별 업데이트 실패: idx={}, {}", entity.getIdx(), ex.getMessage());
                    entityManager.clear();
                }
            }
            return saved;
        }
    }
}
