package com.linkup.Petory.domain.location.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.location.dto.LocationServiceReviewDTO;
import com.linkup.Petory.domain.location.entity.LocationServiceReview;

import lombok.RequiredArgsConstructor;

/**
 * LocationServiceReviewRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaLocationServiceReviewAdapter implements LocationServiceReviewRepository {

    private final SpringDataJpaLocationServiceReviewRepository jpaRepository;

    @SuppressWarnings("null")
    @Override
    public LocationServiceReview save(LocationServiceReview review) {
        return jpaRepository.save(review);
    }

    @Override
    public Optional<LocationServiceReview> findByIdWithUserAndService(Long idx) {
        return jpaRepository.findByIdWithUserAndService(idx);
    }

    @Override
    public Page<LocationServiceReviewDTO> findReviewListItems(Long serviceIdx, Pageable pageable) {
        return jpaRepository.findReviewListItems(serviceIdx, pageable);
    }

    @Override
    public List<LocationServiceReview> findByUserIdxOrderByCreatedAtDesc(Long userIdx) {
        return jpaRepository.findByUserIdxOrderByCreatedAtDesc(userIdx);
    }

    @Override
    public Optional<Double> findAverageRatingByServiceIdx(Long serviceIdx) {
        return jpaRepository.findAverageRatingByServiceIdx(serviceIdx);
    }

    @Override
    public boolean existsByServiceIdxAndUserIdx(Long serviceIdx, Long userIdx) {
        return jpaRepository.existsByServiceIdxAndUserIdx(serviceIdx, userIdx);
    }
}
