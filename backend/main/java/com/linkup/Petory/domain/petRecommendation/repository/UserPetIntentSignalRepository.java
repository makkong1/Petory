package com.linkup.Petory.domain.petRecommendation.repository;

import com.linkup.Petory.domain.petRecommendation.entity.UserPetIntentSignal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;

public interface UserPetIntentSignalRepository extends JpaRepository<UserPetIntentSignal, Long> {

    // R2: Pageable로 최대 10건 제한 — 호출 시 PageRequest.of(0, 10)
    @Query("""
        SELECT s FROM UserPetIntentSignal s
        WHERE s.userIdx = :userIdx
          AND s.expiresAt > :now
        ORDER BY s.createdAt DESC
        """)
    List<UserPetIntentSignal> findActiveByUser(
            @Param("userIdx") Long userIdx,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    // R3: 같은 도메인 유효 signal 중복 방지
    boolean existsByUserIdxAndIntentDomainAndExpiresAtAfter(
            Long userIdx, String intentDomain, LocalDateTime expiresAt);
}
