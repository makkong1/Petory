package com.linkup.Petory.domain.place.repository;

import com.linkup.Petory.domain.place.entity.CandidateDecisionStatus;
import com.linkup.Petory.domain.place.entity.PlaceCandidate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlaceCandidateRepository extends JpaRepository<PlaceCandidate, Long> {

    Page<PlaceCandidate> findByDecisionStatus(CandidateDecisionStatus status, Pageable pageable);

    List<PlaceCandidate> findByDecisionStatus(CandidateDecisionStatus status);

    int countByRawNameAndRawAddress(String rawName, String rawAddress);

    @Query("SELECT COUNT(DISTINCT c.collectedFrom) FROM PlaceCandidate c " +
           "WHERE c.rawName = :name AND c.rawAddress = :address")
    int countDistinctSourcesByRawNameAndAddress(
        @Param("name") String rawName, @Param("address") String rawAddress);
}
