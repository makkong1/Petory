package com.linkup.Petory.domain.care.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.entity.CareRequest;

/**
 * CareApplication 도메인 Repository 인터페이스입니다.
 */
public interface CareApplicationRepository {

    CareApplication save(CareApplication careApplication);

    CareApplication saveAndFlush(CareApplication careApplication);

    Optional<CareApplication> findById(Long id);

    void delete(CareApplication careApplication);

    void deleteById(Long id);

    /**
     * 특정 CareRequest에 대한 지원 목록 조회
     */
    List<CareApplication> findByCareRequest(CareRequest careRequest);

    /**
     * 특정 CareRequest와 Provider로 지원 조회
     */
    Optional<CareApplication> findByCareRequestIdxAndProviderIdx(
            Long careRequestIdx,
            Long providerIdx);

    /**
     * 특정 CareRequest의 특정 상태 지원 조회
     */
    Optional<CareApplication> findByCareRequestIdxAndStatus(
            Long careRequestIdx,
            CareApplicationStatus status);
}
