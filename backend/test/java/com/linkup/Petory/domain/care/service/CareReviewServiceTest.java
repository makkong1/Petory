package com.linkup.Petory.domain.care.service;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.linkup.Petory.domain.care.converter.CareReviewConverter;
import com.linkup.Petory.domain.care.dto.CareReviewDTO;
import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareReview;
import com.linkup.Petory.domain.care.exception.CareApplicationNotFoundException;
import com.linkup.Petory.domain.care.exception.CareConflictException;
import com.linkup.Petory.domain.care.exception.CareForbiddenException;
import com.linkup.Petory.domain.care.repository.CareApplicationRepository;
import com.linkup.Petory.domain.care.repository.CareReviewRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

@ExtendWith(MockitoExtension.class)
class CareReviewServiceTest {

        @InjectMocks
        private CareReviewService careReviewService;

        @Mock
        private CareReviewRepository reviewRepository;
        @Mock
        private CareReviewConverter reviewConverter;
        @Mock
        private CareApplicationRepository careApplicationRepository;
        @Mock
        private UsersRepository usersRepository;

        private Users createUser(Long idx) {
                return Users.builder().idx(idx).id("user_" + idx).build();
        }

        private CareApplication createAcceptedApplication(Long idx, Users requester, Users provider) {
                CareRequest careRequest = CareRequest.builder().user(requester).build();
                careRequest.setIdx(100L);

                CareApplication app = CareApplication.builder()
                                .careRequest(careRequest)
                                .provider(provider)
                                .status(CareApplicationStatus.ACCEPTED)
                                .build();
                app.setIdx(idx);
                return app;
        }

        private CareReviewDTO createReviewDTO(Long applicationId, Long reviewerId, Long revieweeId) {
                return CareReviewDTO.builder()
                                .careApplicationId(applicationId)
                                .reviewerId(reviewerId)
                                .revieweeId(revieweeId)
                                .rating(5)
                                .comment("좋은 서비스였습니다")
                                .build();
        }

        // ===== createReview 정상 케이스 =====

        @Test
        @DisplayName("정상: 리뷰 생성 성공")
        void 정상_리뷰_생성() {
                Users requester = createUser(1L);
                Users provider = createUser(2L);
                CareApplication app = createAcceptedApplication(10L, requester, provider);
                CareReviewDTO dto = createReviewDTO(10L, 1L, 2L);

                when(careApplicationRepository.findById(10L)).thenReturn(Optional.of(app));
                when(reviewRepository.existsByCareApplicationIdxAndReviewerIdx(10L, 1L)).thenReturn(false);
                when(usersRepository.findById(1L)).thenReturn(Optional.of(requester));
                when(usersRepository.findById(2L)).thenReturn(Optional.of(provider));

                CareReview savedReview = CareReview.builder()
                                .careApplication(app).reviewer(requester).reviewee(provider)
                                .rating(5).comment("좋은 서비스였습니다").build();
                savedReview.setIdx(1L);

                when(reviewRepository.save(any(CareReview.class))).thenReturn(savedReview);
                when(reviewConverter.toDTO(any(CareReview.class)))
                                .thenReturn(CareReviewDTO.builder().idx(1L).rating(5).build());

                CareReviewDTO result = careReviewService.createReview(dto);

                assertThat(result).isNotNull();
                assertThat(result.getRating()).isEqualTo(5);
        }

        // ===== createReview 예외 케이스 =====

        @Test
        @DisplayName("예외: 이미 리뷰 작성한 경우 CareConflictException")
        void 예외_중복_리뷰() {
                Users requester = createUser(1L);
                Users provider = createUser(2L);
                CareApplication app = createAcceptedApplication(10L, requester, provider);
                CareReviewDTO dto = createReviewDTO(10L, 1L, 2L);

                when(careApplicationRepository.findById(10L)).thenReturn(Optional.of(app));
                when(reviewRepository.existsByCareApplicationIdxAndReviewerIdx(10L, 1L)).thenReturn(true);

                assertThatThrownBy(() -> careReviewService.createReview(dto))
                                .isInstanceOf(CareConflictException.class);
        }

        @Test
        @DisplayName("예외: 동시 중복 리뷰 시 DataIntegrityViolation → CareConflictException 변환")
        void 예외_동시_중복_리뷰_DataIntegrityViolation() {
                Users requester = createUser(1L);
                Users provider = createUser(2L);
                CareApplication app = createAcceptedApplication(10L, requester, provider);
                CareReviewDTO dto = createReviewDTO(10L, 1L, 2L);

                when(careApplicationRepository.findById(10L)).thenReturn(Optional.of(app));
                when(reviewRepository.existsByCareApplicationIdxAndReviewerIdx(10L, 1L)).thenReturn(false);
                when(usersRepository.findById(1L)).thenReturn(Optional.of(requester));
                when(usersRepository.findById(2L)).thenReturn(Optional.of(provider));
                when(reviewRepository.save(any(CareReview.class)))
                                .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

                assertThatThrownBy(() -> careReviewService.createReview(dto))
                                .isInstanceOf(CareConflictException.class);
        }

        @Test
        @DisplayName("예외: 미승인 지원에 리뷰 작성 시 IllegalStateException")
        void 예외_미승인_지원_리뷰() {
                Users requester = createUser(1L);
                Users provider = createUser(2L);

                CareRequest careRequest = CareRequest.builder().user(requester).build();
                careRequest.setIdx(100L);

                CareApplication pendingApp = CareApplication.builder()
                                .careRequest(careRequest)
                                .provider(provider)
                                .status(CareApplicationStatus.PENDING)
                                .build();
                pendingApp.setIdx(10L);

                CareReviewDTO dto = createReviewDTO(10L, 1L, 2L);

                when(careApplicationRepository.findById(10L)).thenReturn(Optional.of(pendingApp));

                assertThatThrownBy(() -> careReviewService.createReview(dto))
                                .isInstanceOf(IllegalStateException.class)
                                .hasMessageContaining("승인된");
        }

        @Test
        @DisplayName("예외: 존재하지 않는 지원에 리뷰 작성 시 CareApplicationNotFoundException")
        void 예외_존재하지않는_지원_리뷰() {
                CareReviewDTO dto = createReviewDTO(999L, 1L, 2L);

                when(careApplicationRepository.findById(999L)).thenReturn(Optional.empty());

                assertThatThrownBy(() -> careReviewService.createReview(dto))
                                .isInstanceOf(CareApplicationNotFoundException.class);
        }

        @Test
        @DisplayName("예외: 요청자가 아닌 사용자가 리뷰 작성 시 CareForbiddenException")
        void 예외_비요청자_리뷰() {
                Users requester = createUser(1L);
                Users provider = createUser(2L);
                CareApplication app = createAcceptedApplication(10L, requester, provider);

                CareReviewDTO dto = createReviewDTO(10L, 3L, 2L);

                when(careApplicationRepository.findById(10L)).thenReturn(Optional.of(app));
                when(reviewRepository.existsByCareApplicationIdxAndReviewerIdx(10L, 3L)).thenReturn(false);

                assertThatThrownBy(() -> careReviewService.createReview(dto))
                                .isInstanceOf(CareForbiddenException.class);
        }

        // ===== 경계값 테스트 =====

        @Test
        @DisplayName("경계: careApplicationId가 null이면 예외")
        void 경계_applicationId_null() {
                CareReviewDTO dto = CareReviewDTO.builder()
                                .careApplicationId(null)
                                .reviewerId(1L)
                                .revieweeId(2L)
                                .rating(5)
                                .build();

                assertThatThrownBy(() -> careReviewService.createReview(dto))
                                .isInstanceOf(RuntimeException.class);
        }

        // ===== 조회 테스트 =====

        @Test
        @DisplayName("정상: 리뷰 대상자별 조회")
        void 정상_리뷰_대상자별_조회() {
                CareReview review = CareReview.builder()
                                .rating(5).comment("좋았습니다").build();
                CareReviewDTO dto = CareReviewDTO.builder().rating(5).build();

                when(reviewRepository.findByRevieweeIdxOrderByCreatedAtDesc(2L))
                                .thenReturn(List.of(review));
                when(reviewConverter.toDTO(any(CareReview.class))).thenReturn(dto);

                List<CareReviewDTO> result = careReviewService.getReviewsByReviewee(2L);

                assertThat(result).hasSize(1);
                assertThat(result.get(0).getRating()).isEqualTo(5);
        }

        @Test
        @DisplayName("경계: 리뷰 없는 대상자 평균 평점 조회 시 null 반환")
        void 경계_리뷰없는_평균평점() {
                when(reviewRepository.findByRevieweeIdxOrderByCreatedAtDesc(999L))
                                .thenReturn(List.of());

                Double avg = careReviewService.getAverageRating(999L);

                assertThat(avg).isNull();
        }
}
