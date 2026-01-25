package com.linkup.Petory.domain.payment.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.payment.entity.EscrowStatus;
import com.linkup.Petory.domain.payment.entity.PetCoinEscrow;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaPetCoinEscrowRepository
        extends JpaRepository<PetCoinEscrow, Long> {

    Optional<PetCoinEscrow> findByCareRequest(CareRequest careRequest);

    @Query("SELECT e FROM PetCoinEscrow e WHERE e.requester = :user OR e.provider = :user ORDER BY e.createdAt DESC")
    List<PetCoinEscrow> findByRequesterOrProvider(@Param("user") Users user);

    List<PetCoinEscrow> findByStatus(EscrowStatus status);
}
