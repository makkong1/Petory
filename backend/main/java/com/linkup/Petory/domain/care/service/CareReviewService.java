package com.linkup.Petory.domain.care.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.care.converter.CareReviewConverter;
import com.linkup.Petory.domain.care.dto.CareReviewDTO;
import com.linkup.Petory.domain.care.repository.CareReviewRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CareReviewService {

    private final CareReviewRepository reviewRepository;
    private final CareReviewConverter reviewConverter;

    /**
     * 특정 사용자(reviewee)에 대한 리뷰 목록 조회
     */
    public List<CareReviewDTO> getReviewsByReviewee(Long revieweeIdx) {
        List<com.linkup.Petory.domain.care.entity.CareReview> reviews = 
            reviewRepository.findByRevieweeIdxOrderByCreatedAtDesc(revieweeIdx);
        return reviews.stream()
            .map(reviewConverter::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * 특정 사용자(reviewer)가 작성한 리뷰 목록 조회
     */
    public List<CareReviewDTO> getReviewsByReviewer(Long reviewerIdx) {
        List<com.linkup.Petory.domain.care.entity.CareReview> reviews = 
            reviewRepository.findByReviewerIdxOrderByCreatedAtDesc(reviewerIdx);
        return reviews.stream()
            .map(reviewConverter::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * 특정 사용자의 평균 평점 계산
     */
    public Double getAverageRating(Long revieweeIdx) {
        List<com.linkup.Petory.domain.care.entity.CareReview> reviews = 
            reviewRepository.findByRevieweeIdxOrderByCreatedAtDesc(revieweeIdx);
        
        if (reviews.isEmpty()) {
            return null;
        }
        
        double sum = reviews.stream()
            .mapToInt(com.linkup.Petory.domain.care.entity.CareReview::getRating)
            .sum();
        
        return sum / reviews.size();
    }
}

