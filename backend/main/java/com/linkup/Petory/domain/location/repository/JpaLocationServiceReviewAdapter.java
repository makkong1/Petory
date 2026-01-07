package com.linkup.Petory.domain.location.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

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

    @Override
    public LocationServiceReview save(LocationServiceReview review) {
        return jpaRepository.save(review);
    }

    @Override
    public Optional<LocationServiceReview> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(LocationServiceReview review) {
        jpaRepository.delete(review);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<LocationServiceReview> findByServiceIdxOrderByCreatedAtDesc(Long serviceIdx) {
        return jpaRepository.findByServiceIdxOrderByCreatedAtDesc(serviceIdx);
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
    public Long countByServiceIdx(Long serviceIdx) {
        return jpaRepository.countByServiceIdx(serviceIdx);
    }

    @Override
    public boolean existsByServiceIdxAndUserIdx(Long serviceIdx, Long userIdx) {
        return jpaRepository.existsByServiceIdxAndUserIdx(serviceIdx, userIdx);
    }
}

