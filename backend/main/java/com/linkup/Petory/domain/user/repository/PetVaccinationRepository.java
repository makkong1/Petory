package com.linkup.Petory.domain.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.user.entity.PetVaccination;

/**
 * 예방접종 엔티티용 Spring Data JPA 리포지토리.
 * (도메인 서비스에서 아직 주입·호출되지 않음 — 필요 시 쿼리 메서드 추가)
 */
@Repository
public interface PetVaccinationRepository extends JpaRepository<PetVaccination, Long> {
}
