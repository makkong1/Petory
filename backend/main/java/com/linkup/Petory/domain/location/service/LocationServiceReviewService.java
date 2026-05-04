package com.linkup.Petory.domain.location.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.location.converter.LocationServiceReviewConverter;
import com.linkup.Petory.domain.location.dto.LocationServiceReviewDTO;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.entity.LocationServiceReview;
import com.linkup.Petory.domain.location.exception.LocationReviewAlreadyDeletedException;
import com.linkup.Petory.domain.location.exception.LocationReviewDuplicateException;
import com.linkup.Petory.domain.location.exception.LocationServiceNotFoundException;
import com.linkup.Petory.domain.location.exception.LocationServiceReviewForbiddenException;
import com.linkup.Petory.domain.location.exception.LocationServiceReviewNotFoundException;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import com.linkup.Petory.domain.location.repository.LocationServiceReviewRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException;
import com.linkup.Petory.domain.user.exception.UserNotFoundException;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceReviewService {

    private final LocationServiceReviewRepository reviewRepository;
    private final LocationServiceRepository serviceRepository;
    private final UsersRepository usersRepository;
    private final LocationServiceReviewConverter converter;

    // 리뷰 생성 (작성자는 JWT 기준 로그인 사용자만 허용 — 요청 본문의 userIdx는 무시)
    @Transactional
    public LocationServiceReviewDTO createReview(LocationServiceReviewDTO reviewDTO, String currentUserLoginId) {
        Users user = usersRepository.findByIdString(currentUserLoginId)
                .orElseThrow(UserNotFoundException::new);

        // 중복 리뷰 체크
        if (reviewRepository.existsByServiceIdxAndUserIdx(reviewDTO.getServiceIdx(), user.getIdx())) {
            throw new LocationReviewDuplicateException();
        }

        LocationService service = serviceRepository.findById(reviewDTO.getServiceIdx())
                .orElseThrow(LocationServiceNotFoundException::new);

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
        updateServiceReviewStats(service.getIdx());

        return converter.toDTO(savedReview);
    }

    // 리뷰 수정
    @Transactional
    public LocationServiceReviewDTO updateReview(Long reviewIdx, LocationServiceReviewDTO reviewDTO,
            String currentUserLoginId) {
        Users actor = usersRepository.findByIdString(currentUserLoginId)
                .orElseThrow(UserNotFoundException::new);

        LocationServiceReview review = reviewRepository.findByIdWithUserAndService(reviewIdx)
                .orElseThrow(LocationServiceReviewNotFoundException::new);

        if (!review.getUser().getIdx().equals(actor.getIdx())) {
            throw LocationServiceReviewForbiddenException.notOwner();
        }

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
        updateServiceReviewStats(review.getService().getIdx());

        return converter.toDTO(savedReview);
    }

    // 리뷰 삭제 (Soft Delete)
    @Transactional
    public void deleteReview(Long reviewIdx, String currentUserLoginId) {
        Users actor = usersRepository.findByIdString(currentUserLoginId)
                .orElseThrow(UserNotFoundException::new);

        LocationServiceReview review = reviewRepository.findByIdWithUserAndService(reviewIdx)
                .orElseThrow(LocationServiceReviewNotFoundException::new);

        if (!review.getUser().getIdx().equals(actor.getIdx())) {
            throw LocationServiceReviewForbiddenException.notOwner();
        }

        // 이미 삭제된 리뷰인지 확인
        if (review.getIsDeleted() != null && review.getIsDeleted()) {
            throw new LocationReviewAlreadyDeletedException();
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
        updateServiceReviewStats(serviceIdx);
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

    // [FIX] 서비스 평점·리뷰수 원자적 갱신 — DB 단일 UPDATE로 Lost Update 제거.
    // 기존: findAverageRatingByServiceIdx → findById → setRating → save (비원자적 read-modify-write)
    // 변경: UPDATE locationservice SET rating = (SELECT AVG ...), review_count = (SELECT COUNT ...)
    @Transactional
    public void updateServiceReviewStats(Long serviceIdx) {
        serviceRepository.updateReviewStats(serviceIdx);
    }
}
