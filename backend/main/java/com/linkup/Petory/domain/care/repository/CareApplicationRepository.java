package com.linkup.Petory.domain.care.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.care.entity.CareApplication;

/**
 * CareApplication 도메인 Repository 인터페이스입니다.
 */
public interface CareApplicationRepository {

    CareApplication saveAndFlush(CareApplication careApplication);

    Optional<CareApplication> findById(Long id);

    long countCompletedByProviderId(Long providerId);

    List<CareApplication> findLiveOffersBetween(Long userA, Long userB);

    List<Object[]> countCompletedByProviderIdxs(List<Long> providerIdxs);

    List<CareApplication> findPendingOffersBefore(java.time.LocalDateTime cutoff);
}
