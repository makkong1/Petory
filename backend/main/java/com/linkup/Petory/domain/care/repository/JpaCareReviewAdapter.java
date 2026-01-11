package com.linkup.Petory.domain.care.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.care.entity.CareReview;

import lombok.RequiredArgsConstructor;

/**
 * CareReviewRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaCareReviewAdapter implements CareReviewRepository {

    private final SpringDataJpaCareReviewRepository jpaRepository;

    @Override
    public CareReview save(CareReview careReview) {
        return jpaRepository.save(careReview);
    }

    @Override
    public Optional<CareReview> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(CareReview careReview) {
        jpaRepository.delete(careReview);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<CareReview> findByRevieweeIdxOrderByCreatedAtDesc(Long revieweeIdx) {
        return jpaRepository.findByRevieweeIdxOrderByCreatedAtDesc(revieweeIdx);
    }

    @Override
    public List<CareReview> findByReviewerIdxOrderByCreatedAtDesc(Long reviewerIdx) {
        return jpaRepository.findByReviewerIdxOrderByCreatedAtDesc(reviewerIdx);
    }

    @Override
    public List<CareReview> findByCareApplicationIdx(Long careApplicationIdx) {
        return jpaRepository.findByCareApplicationIdx(careApplicationIdx);
    }

    @Override
    public boolean existsByCareApplicationIdxAndReviewerIdx(Long careApplicationIdx, Long reviewerIdx) {
        return jpaRepository.existsByCareApplicationIdxAndReviewerIdx(careApplicationIdx, reviewerIdx);
    }
}

