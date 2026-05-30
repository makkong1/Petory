package com.linkup.Petory.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.linkup.Petory.domain.location.dto.LocationServiceDTO;
import com.linkup.Petory.domain.location.service.LocationServiceService;
import com.linkup.Petory.domain.recommendation.client.PetDataApiClient;
import com.linkup.Petory.domain.recommendation.dto.RecommendRequest;
import com.linkup.Petory.domain.recommendation.dto.RecommendResponse;
import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.PetType;
import com.linkup.Petory.domain.user.repository.PetRepository;

@ExtendWith(MockitoExtension.class)
class RecommendServiceTest {

    @Mock
    private PetRepository petRepository;

    @Mock
    private PetDataApiClient petDataApiClient;

    @Mock
    private LocationServiceService locationServiceService;

    @InjectMocks
    private RecommendService recommendService;

    @Test
    @DisplayName("Track A 컨텍스트는 Petory nearby 후보와 popularity 시그널을 조합한다")
    void recommend_mergesNearbyCandidatesForPetoryOwnedContext() {
        when(petRepository.findByUserIdAndNotDeleted("user-1")).thenReturn(List.of(
                Pet.builder()
                        .petType(PetType.DOG)
                        .breed("말티즈")
                        .birthDate(LocalDate.now().minusYears(2))
                        .build()));

        when(locationServiceService.searchLocationServicesByLocation(
                37.5, 127.0, 10_000, null, "미용", "distance", 20))
                .thenReturn(List.of(
                        LocationServiceDTO.builder()
                                .idx(1L)
                                .name("해피독 애견미용")
                                .address("서울시 어딘가")
                                .latitude(37.5001)
                                .longitude(127.0001)
                                .distance(220.0)
                                .rating(4.8)
                                .reviewCount(35)
                                .build(),
                        LocationServiceDTO.builder()
                                .idx(2L)
                                .name("멀리있는 미용실")
                                .address("서울시 저 멀리")
                                .latitude(37.55)
                                .longitude(127.05)
                                .distance(1800.0)
                                .rating(4.9)
                                .reviewCount(120)
                                .build()));

        when(petDataApiClient.fetchPopular(eq("grooming"), eq(20), any()))
                .thenReturn(List.of(
                        new RecommendResponse.FacilityItem(
                                null, null, "해피독", 0, null, null, null,
                                18, 91.5, "popular_blog", 91.5, List.of("popular_blog"))));
        when(petDataApiClient.fetchTrends(eq("grooming"), eq(15), any()))
                .thenReturn(List.of(
                        new RecommendResponse.TrendItem("예약", 88.0),
                        new RecommendResponse.TrendItem("가위컷", 77.0)));

        RecommendResponse response = recommendService.recommend("user-1", 37.5, 127.0, "grooming");

        assertThat(response.recommendVersion()).isEqualTo("petory-nearby-v1");
        assertThat(response.facilities()).hasSize(2);
        assertThat(response.facilities().get(0).id()).isEqualTo(1L);
        assertThat(response.facilities().get(0).distanceM()).isEqualTo(220);
        assertThat(response.facilities().get(0).mentionCount()).isEqualTo(18);
        assertThat(response.facilities().get(0).reasons()).contains("nearby", "popular_signal");
        assertThat(response.trends()).extracting(RecommendResponse.TrendItem::keyword)
                .containsExactly("예약", "가위컷");
        assertThat(response.recommendation()).contains("현재 위치", "해피독 애견미용", "예약");

        verify(locationServiceService).searchLocationServicesByLocation(
                37.5, 127.0, 10_000, null, "미용", "distance", 20);
        verify(petDataApiClient, never()).recommend(any(RecommendRequest.class));
    }

    @Test
    @DisplayName("Track B 컨텍스트는 기존 pet-data-api recommend 경로를 유지한다")
    void recommend_keepsLegacyProxyForNonPetoryOwnedContext() {
        // "vet" is not in PETORY_OWNED_CONTEXTS — legacy proxy path
        RecommendResponse legacyResponse = new RecommendResponse(
                "vet",
                "popular-intelligence-v1",
                "req-legacy",
                List.of(),
                List.of(),
                "legacy",
                "2026-05-24T00:00:00Z");

        when(petRepository.findByUserIdAndNotDeleted("user-2")).thenReturn(List.of());
        when(petDataApiClient.recommend(any(RecommendRequest.class))).thenReturn(legacyResponse);

        RecommendResponse response = recommendService.recommend("user-2", 37.4, 127.1, "vet");

        assertThat(response).isSameAs(legacyResponse);
        verify(locationServiceService, never()).searchLocationServicesByLocation(
                any(), any(), any(), any(), any(), any(), any());
        verify(petDataApiClient).recommend(any(RecommendRequest.class));
    }
}
