package com.linkup.Petory.domain.care.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.care.dto.CareRequestListView;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.entity.CareScheduleMode;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.PetType;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.PetRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * [오버페칭 제거] 지도 getNearby native projection 검증.
 *
 * SELECT cr.* + 컨버터(작성자 전체·중첩 pet·applications @BatchSize) → 14컬럼 interface projection 단일 쿼리로
 * 전환한 뒤에도 (1) native + 예약어 별칭(`date`) + enum(String) 컬럼 + JOIN 매핑이 런타임에 유효하고,
 * (2) 지도 레이어가 쓰는 필드(작성자 userId/username, petName 평면, 본문/좌표/상태)가 정확히 채워지는지 확인한다.
 *
 * 반경 필터(haversine)를 통과하도록 요청 좌표와 동일 좌표에 데이터를 심는다.
 */
@SpringBootTest
@Transactional
class CareRequestNearbyProjectionTest {

    private static final double LAT = 37.5000;
    private static final double LNG = 127.0000;

    @Autowired
    private CareRequestService careRequestService;
    @Autowired
    private CareRequestRepository careRequestRepository;
    @Autowired
    private UsersRepository usersRepository;
    @Autowired
    private PetRepository petRepository;
    @PersistenceContext
    private EntityManager em;

    private String tag;
    private Users writer;
    private Pet pet;
    private CareRequest open;

    @BeforeEach
    void setUp() {
        tag = "tag" + UUID.randomUUID().toString().substring(0, 8);

        writer = usersRepository.save(Users.builder()
                .id(tag + "-writer")
                .username(tag + "-writer")
                .email(tag + "-writer@test.com")
                .nickname(tag + "-작성자")
                .password("password")
                .role(Role.USER)
                .location("서울시 강남구")
                .build());

        pet = petRepository.save(Pet.builder()
                .user(writer)
                .petName(tag + "-몽이")
                .petType(PetType.DOG)
                .build());

        open = careRequestRepository.save(CareRequest.builder()
                .user(writer)
                .pet(pet)
                .title(tag + " 산책 도와주세요")
                .description("오후에 30분 산책")
                .date(LocalDateTime.now().plusDays(1))
                .scheduleMode(CareScheduleMode.FIXED)
                .estimatedDurationMinutes(30)
                .offeredCoins(100)
                .status(CareRequestStatus.OPEN)
                .isDeleted(false)
                .latitude(LAT)
                .longitude(LNG)
                .build());

        // native 쿼리는 자동 flush 대상이 아닐 수 있으므로 명시적으로 flush 하여 반영시킨다.
        em.flush();
    }

    @Test
    @DisplayName("정상: 반경 내 요청이 projection 필드(작성자·petName 평면·본문/좌표/상태)로 정확히 매핑된다")
    void 정상_근처요청_projection_필드매핑() {
        List<CareRequestListView> views = careRequestService.getNearby(LAT, LNG, 5.0, 200);

        CareRequestListView v = views.stream()
                .filter(x -> x.getIdx().equals(open.getIdx()))
                .findFirst().orElseThrow();

        assertThat(v.getTitle()).isEqualTo(tag + " 산책 도와주세요");
        assertThat(v.getDescription()).isEqualTo("오후에 30분 산책");
        assertThat(v.getStatus()).isEqualTo("OPEN");                 // enum → String 컬럼
        assertThat(v.getScheduleMode()).isEqualTo("FIXED");
        assertThat(v.getEstimatedDurationMinutes()).isEqualTo(30);
        assertThat(v.getOfferedCoins()).isEqualTo(100);
        assertThat(v.getLatitude()).isEqualTo(LAT);
        assertThat(v.getLongitude()).isEqualTo(LNG);
        assertThat(v.getDate()).isNotNull();                        // 예약어 별칭 `date` 매핑
        assertThat(v.getUserId()).isEqualTo(writer.getIdx());       // JOIN users
        assertThat(v.getUsername()).isEqualTo(writer.getUsername());
        assertThat(v.getPetName()).isEqualTo(tag + "-몽이");         // LEFT JOIN pets, 평면 필드
    }

    @Test
    @DisplayName("경계: 반경 밖(먼 좌표)이면 결과에 포함되지 않는다")
    void 경계_반경밖은_제외() {
        // 제주도 부근 — 서울 좌표 5km 반경 밖
        List<CareRequestListView> views = careRequestService.getNearby(33.5, 126.5, 5.0, 200);

        assertThat(views).extracting(CareRequestListView::getIdx)
                .doesNotContain(open.getIdx());
    }
}
