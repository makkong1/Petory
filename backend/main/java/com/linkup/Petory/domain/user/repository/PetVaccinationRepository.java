package com.linkup.Petory.domain.user.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.user.entity.PetVaccination;

@Repository
public interface PetVaccinationRepository extends JpaRepository<PetVaccination, Long> {

    /**
     * 펫 ID로 예방접종 목록 조회 (삭제되지 않은 것만)
     */
    @Query("SELECT pv FROM PetVaccination pv WHERE pv.pet.idx = :petIdx AND pv.isDeleted = false ORDER BY pv.vaccinatedAt DESC")
    List<PetVaccination> findByPetIdxAndNotDeleted(@Param("petIdx") Long petIdx);

    /**
     * 펫 ID로 예방접종 목록 조회 (모든 것)
     */
    List<PetVaccination> findByPetIdx(Long petIdx);

    /**
     * 다음 접종 예정일이 다가오는 예방접종 조회 (삭제되지 않은 것만)
     */
    @Query("SELECT pv FROM PetVaccination pv WHERE pv.nextDue <= :date AND pv.isDeleted = false ORDER BY pv.nextDue ASC")
    List<PetVaccination> findUpcomingVaccinations(@Param("date") LocalDate date);
}

