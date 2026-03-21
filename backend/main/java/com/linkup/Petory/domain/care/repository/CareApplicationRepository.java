package com.linkup.Petory.domain.care.repository;

import java.util.Optional;

import com.linkup.Petory.domain.care.entity.CareApplication;

/**
 * CareApplication 도메인 Repository 인터페이스입니다.
 */
public interface CareApplicationRepository {

    CareApplication saveAndFlush(CareApplication careApplication);

    Optional<CareApplication> findById(Long id);
}
