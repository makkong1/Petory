package com.linkup.Petory.domain.care.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.care.converter.CareReviewConverter;
import com.linkup.Petory.domain.care.dto.CareReviewDTO;
import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.entity.CareReview;
import com.linkup.Petory.domain.care.repository.CareApplicationRepository;
import com.linkup.Petory.domain.care.repository.CareReviewRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CareReviewService {

    private final CareReviewRepository reviewRepository;
    private final CareReviewConverter reviewConverter;
    private final CareApplicationRepository careApplicationRepository;
    private final UsersRepository usersRepository;

    /**
     * 특정 사용자(reviewee)에 대한 리뷰 목록 조회
     */
    public List<CareReviewDTO> getReviewsByReviewee(Long revieweeIdx) {
        List<com.linkup.Petory.domain.care.entity.CareReview> reviews = reviewRepository
                .findByRevieweeIdxOrderByCreatedAtDesc(revieweeIdx);
        return reviews.stream()
                .map(reviewConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 특정 사용자(reviewer)가 작성한 리뷰 목록 조회
     */
    public List<CareReviewDTO> getReviewsByReviewer(Long reviewerIdx) {
        List<com.linkup.Petory.domain.care.entity.CareReview> reviews = reviewRepository
                .findByReviewerIdxOrderByCreatedAtDesc(reviewerIdx);
        return reviews.stream()
                .map(reviewConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 특정 사용자의 평균 평점 계산
     */
    @Transactional(readOnly = true)
    public Double getAverageRating(Long revieweeIdx) {
        List<com.linkup.Petory.domain.care.entity.CareReview> reviews = reviewRepository
                .findByRevieweeIdxOrderByCreatedAtDesc(revieweeIdx);

        if (reviews.isEmpty()) {
            return null;
        }

        double sum = reviews.stream()
                .mapToInt(com.linkup.Petory.domain.care.entity.CareReview::getRating)
                .sum();

        return sum / reviews.size();
    }

    /**
     * 리뷰 작성 (요청자가 제공자에게 리뷰 작성)
     */
    @Transactional
    public CareReviewDTO createReview(CareReviewDTO dto) {
        if (dto.getCareApplicationId() == null) {
            throw new IllegalArgumentException("CareApplication ID가 필요합니다.");
        }

        // CareApplication 조회
        CareApplication careApplication = careApplicationRepository.findById(dto.getCareApplicationId())
                .orElseThrow(() -> new RuntimeException("CareApplication not found"));

        // 상태 확인 (ACCEPTED 상태여야 함)
        if (careApplication.getStatus() != CareApplicationStatus.ACCEPTED) {
            throw new IllegalStateException("승인된 펫케어 서비스에만 리뷰를 작성할 수 있습니다.");
        }

        // 이미 리뷰를 작성했는지 확인
        boolean alreadyReviewed = reviewRepository.existsByCareApplicationIdxAndReviewerIdx(
                dto.getCareApplicationId(), dto.getReviewerId());
        if (alreadyReviewed) {
            throw new IllegalStateException("이미 해당 서비스에 리뷰를 작성하셨습니다.");
        }

        // 요청자와 제공자 확인
        Long requesterId = careApplication.getCareRequest().getUser().getIdx();
        Long providerId = careApplication.getProvider().getIdx();

        if (!dto.getReviewerId().equals(requesterId)) {
            throw new IllegalArgumentException("요청자만 리뷰를 작성할 수 있습니다.");
        }

        if (!dto.getRevieweeId().equals(providerId)) {
            throw new IllegalArgumentException("제공자에게만 리뷰를 작성할 수 있습니다.");
        }

        // 사용자 조회
        Users reviewer = usersRepository.findById(dto.getReviewerId())
                .orElseThrow(() -> new RuntimeException("Reviewer not found"));
        Users reviewee = usersRepository.findById(dto.getRevieweeId())
                .orElseThrow(() -> new RuntimeException("Reviewee not found"));

        // 리뷰 생성
        CareReview review = CareReview.builder()
                .careApplication(careApplication)
                .reviewer(reviewer)
                .reviewee(reviewee)
                .rating(dto.getRating())
                .comment(dto.getComment())
                .build();

        CareReview saved = reviewRepository.save(review);
        return reviewConverter.toDTO(saved);
    }
}
