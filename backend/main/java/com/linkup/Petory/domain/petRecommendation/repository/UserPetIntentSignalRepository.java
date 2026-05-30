package com.linkup.Petory.domain.petRecommendation.repository;

import com.linkup.Petory.domain.petRecommendation.entity.UserPetIntentSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface UserPetIntentSignalRepository extends JpaRepository<UserPetIntentSignal, Long> {

    @Query("""
        SELECT s FROM UserPetIntentSignal s
        WHERE s.userIdx = :userIdx
          AND s.expiresAt > :now
        ORDER BY s.createdAt DESC
        """)
    List<UserPetIntentSignal> findActiveByUser(
            @Param("userIdx") Long userIdx,
            @Param("now") LocalDateTime now);
}
