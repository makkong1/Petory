package com.linkup.Petory.domain.location.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.location.dto.LocationServiceReviewDTO;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.entity.LocationServiceReview;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import com.linkup.Petory.domain.location.repository.LocationServiceReviewRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * [오버페칭 제거] 장소 리뷰 목록 projection 페이징 검증.
 *
 * user를 통째로(JOIN FETCH) 로딩하던 것을 idx/username만 SELECT하는 생성자 표현식 projection +
 * DB 페이징으로 전환한 뒤에도 (1) JPQL(생성자 표현식·CAST(NULL AS string))이 런타임에 유효하고,
 * (2) 화면 필드(username/rating/comment 등)가 매핑되며, (3) size=1로 페이징 시 hasNext/총개수/평균이
 * 페이지와 무관하게 전체 기준으로 정확한지 확인한다.
 */
@SpringBootTest
@Transactional
class LocationReviewListProjectionTest {

    @Autowired
    private LocationServiceReviewService reviewService;
    @Autowired
    private LocationServiceReviewRepository reviewRepository;
    @Autowired
    private LocationServiceRepository serviceRepository;
    @Autowired
    private UsersRepository usersRepository;
    @PersistenceContext
    private EntityManager em;

    private String tag;
    private LocationService service;
    private Users userA;
    private LocationServiceReview review1;

    @BeforeEach
    void setUp() {
        tag = "rv" + UUID.randomUUID().toString().substring(0, 8);

        service = serviceRepository.save(LocationService.builder()
                .name(tag + " 동물병원")
                .latitude(37.5)
                .longitude(127.0)
                .isDeleted(false)
                .build());

        userA = usersRepository.save(Users.builder()
                .id(tag + "-a").username(tag + "-철수").email(tag + "-a@test.com")
                .nickname(tag + "-철수닉").password("password").role(Role.USER).build());
        Users userB = usersRepository.save(Users.builder()
                .id(tag + "-b").username(tag + "-영희").email(tag + "-b@test.com")
                .nickname(tag + "-영희닉").password("password").role(Role.USER).build());

        reviewRepository.save(LocationServiceReview.builder()
                .service(service).user(userB).rating(2).comment(tag + " 별로").isDeleted(false).build());
        review1 = reviewRepository.save(LocationServiceReview.builder()
                .service(service).user(userA).rating(4).comment(tag + " 좋아요").isDeleted(false).build());

        em.flush();
    }

    @Test
    @DisplayName("정상: projection 필드 매핑 + serviceName은 null(CAST) + 평균/총개수는 페이지 무관 전체 집계")
    void 정상_리뷰목록_projection_및_전체집계() {
        // 정렬(createdAt) 동률에 의존하지 않도록 전체를 받아 idx로 review1을 특정해 필드 매핑을 검증한다.
        Page<LocationServiceReviewDTO> page = reviewService.getReviewsByService(service.getIdx(), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(2);

        LocationServiceReviewDTO r1 = page.getContent().stream()
                .filter(x -> x.getIdx().equals(review1.getIdx()))
                .findFirst().orElseThrow();
        assertThat(r1.getUserIdx()).isEqualTo(userA.getIdx());     // JOIN user
        assertThat(r1.getUsername()).isEqualTo(tag + "-철수");
        assertThat(r1.getRating()).isEqualTo(4);
        assertThat(r1.getComment()).isEqualTo(tag + " 좋아요");
        assertThat(r1.getServiceIdx()).isEqualTo(service.getIdx());
        assertThat(r1.getServiceName()).isNull();                  // CAST(NULL AS string)

        // 평균은 (4 + 2) / 2 = 3.0, 페이지 크기와 무관
        assertThat(reviewService.getAverageRatingByService(service.getIdx())).isEqualTo(3.0);
    }

    @Test
    @DisplayName("경계: size=1이면 첫 페이지 1건·hasNext=true, 두 번째 페이지 1건·hasNext=false로 누적된다")
    void 경계_페이징_누적() {
        Page<LocationServiceReviewDTO> page0 = reviewService.getReviewsByService(service.getIdx(), PageRequest.of(0, 1));
        assertThat(page0.getContent()).hasSize(1);
        assertThat(page0.getTotalElements()).isEqualTo(2);  // 총개수는 페이지와 무관
        assertThat(page0.hasNext()).isTrue();

        Page<LocationServiceReviewDTO> page1 = reviewService.getReviewsByService(service.getIdx(), PageRequest.of(1, 1));
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.hasNext()).isFalse();

        // 두 페이지를 합치면 서로 다른 2건(= '더보기' 누적)
        assertThat(page0.getContent().get(0).getIdx())
                .isNotEqualTo(page1.getContent().get(0).getIdx());
    }
}
