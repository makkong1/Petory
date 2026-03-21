package com.linkup.Petory.domain.care.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.linkup.Petory.domain.care.entity.CareApplication;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaCareApplicationRepository extends JpaRepository<CareApplication, Long> {
}
