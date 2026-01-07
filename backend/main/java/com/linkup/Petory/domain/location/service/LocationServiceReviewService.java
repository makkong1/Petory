package com.linkup.Petory.domain.location.service;

import com.linkup.Petory.domain.location.converter.LocationServiceReviewConverter;
import com.linkup.Petory.domain.location.dto.LocationServiceReviewDTO;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.entity.LocationServiceReview;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import com.linkup.Petory.domain.location.repository.LocationServiceReviewRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceReviewService {

    private final LocationServiceReviewRepository reviewRepository;
    private final LocationServiceRepository serviceRepository;
    private final UsersRepository usersRepository;
    private final LocationServiceReviewConverter converter;

    // 리뷰 생성
    @Transactional
    public LocationServiceReviewDTO createReview(LocationServiceReviewDTO reviewDTO) {
        // 중복 리뷰 체크
        if (reviewRepository.existsByServiceIdxAndUserIdx(reviewDTO.getServiceIdx(), reviewDTO.getUserIdx())) {
            throw new RuntimeException("이미 해당 서비스에 리뷰를 작성하셨습니다.");
        }

        LocationService service = serviceRepository.findById(reviewDTO.getServiceIdx())
                .orElseThrow(() -> new RuntimeException("서비스를 찾을 수 없습니다."));

        Users user = usersRepository.findById(reviewDTO.getUserIdx())
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        // 이메일 인증 확인
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            throw new EmailVerificationRequiredException(
                    "리뷰 작성을 위해 이메일 인증이 필요합니다.",
                    com.linkup.Petory.domain.user.entity.EmailVerificationPurpose.LOCATION_REVIEW);
        }

        LocationServiceReview review = LocationServiceReview.builder()
                .service(service)
                .user(user)
                .rating(reviewDTO.getRating())
                .comment(reviewDTO.getComment())
                .build();

        LocationServiceReview savedReview = reviewRepository.save(review);

        // 서비스 평점 업데이트
        updateServiceRating(service.getIdx());

        return converter.toDTO(savedReview);
    }

    // 리뷰 수정
    @Transactional
    public LocationServiceReviewDTO updateReview(Long reviewIdx, LocationServiceReviewDTO reviewDTO) {
        LocationServiceReview review = reviewRepository.findById(reviewIdx)
                .orElseThrow(() -> new RuntimeException("리뷰를 찾을 수 없습니다."));

        // 이메일 인증 확인
        Users user = review.getUser();
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            throw new EmailVerificationRequiredException(
                    "리뷰 수정을 위해 이메일 인증이 필요합니다.",
                    com.linkup.Petory.domain.user.entity.EmailVerificationPurpose.LOCATION_REVIEW);
        }

        review.setRating(reviewDTO.getRating());
        review.setComment(reviewDTO.getComment());

        LocationServiceReview savedReview = reviewRepository.save(review);

        // 서비스 평점 업데이트
        updateServiceRating(review.getService().getIdx());

        return converter.toDTO(savedReview);
    }

    // 리뷰 삭제 (Soft Delete)
    @Transactional
    public void deleteReview(Long reviewIdx) {
        LocationServiceReview review = reviewRepository.findById(reviewIdx)
                .orElseThrow(() -> new RuntimeException("리뷰를 찾을 수 없습니다."));

        // 이미 삭제된 리뷰인지 확인
        if (review.getIsDeleted() != null && review.getIsDeleted()) {
            throw new RuntimeException("이미 삭제된 리뷰입니다.");
        }

        // 이메일 인증 확인
        Users user = review.getUser();
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            throw new EmailVerificationRequiredException(
                    "리뷰 삭제를 위해 이메일 인증이 필요합니다.",
                    com.linkup.Petory.domain.user.entity.EmailVerificationPurpose.LOCATION_REVIEW);
        }

        // Soft Delete 처리
        review.setIsDeleted(true);
        review.setDeletedAt(java.time.LocalDateTime.now());
        reviewRepository.save(review);

        // 서비스 평점 업데이트
        Long serviceIdx = review.getService().getIdx();
        updateServiceRating(serviceIdx);
    }

    // 특정 서비스의 리뷰 목록 조회
    public List<LocationServiceReviewDTO> getReviewsByService(Long serviceIdx) {
        return reviewRepository.findByServiceIdxOrderByCreatedAtDesc(serviceIdx)
                .stream()
                .map(converter::toDTO)
                .collect(Collectors.toList());
    }

    // 특정 사용자의 리뷰 목록 조회
    public List<LocationServiceReviewDTO> getReviewsByUser(Long userIdx) {
        return reviewRepository.findByUserIdxOrderByCreatedAtDesc(userIdx)
                .stream()
                .map(converter::toDTO)
                .collect(Collectors.toList());
    }

    // 서비스 평점 업데이트
    @Transactional
    public void updateServiceRating(Long serviceIdx) {
        Optional<Double> averageRating = reviewRepository.findAverageRatingByServiceIdx(serviceIdx);

        if (averageRating.isPresent()) {
            LocationService service = serviceRepository.findById(serviceIdx)
                    .orElseThrow(() -> new RuntimeException("서비스를 찾을 수 없습니다."));

            service.setRating(averageRating.get());
            serviceRepository.save(service);
        }
    }
}
