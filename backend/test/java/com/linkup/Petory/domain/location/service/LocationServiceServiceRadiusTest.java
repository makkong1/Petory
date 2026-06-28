package com.linkup.Petory.domain.location.service;

import com.linkup.Petory.domain.location.converter.LocationServiceConverter;
import com.linkup.Petory.domain.location.dto.LocationServiceDTO;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LocationServiceServiceRadiusTest {

    @Mock private LocationServiceConverter locationServiceConverter;
    @Mock private LocationServiceRepository locationServiceRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private UsersRepository usersRepository;

    @InjectMocks
    private LocationServiceService service;

    private LocationService dummyService(int i) {
        return LocationService.builder()
                .idx((long) i)
                .name("시설" + i)
                .latitude(37.5 + i * 0.001)
                .longitude(127.0 + i * 0.001)
                .isDeleted(false)
                .build();
    }

    @Test
    @DisplayName("반경검색 maxResults=null: 기본 LIMIT 100을 SQL에 전달한다")
    void 반경검색_maxResultsNull_기본Limit100전달() {
        List<LocationService> dbResults = IntStream.range(0, 100)
                .mapToObj(this::dummyService)
                .toList();
        when(locationServiceRepository.findByRadius(anyDouble(), anyDouble(), anyDouble(),
                any(), any(), any(), eq(100)))
                .thenReturn(dbResults);
        when(locationServiceConverter.toDTO(any())).thenReturn(new LocationServiceDTO());

        List<LocationServiceDTO> result = service.searchLocationServicesByLocation(
                37.5, 127.0, 3000, null, null, null, null);

        verify(locationServiceRepository).findByRadius(eq(37.5), eq(127.0), eq(3000.0),
                isNull(), isNull(), eq("distance"), eq(100));
        assertThat(result).hasSize(100);
    }

    @Test
    @DisplayName("반경검색 maxResults=5: LIMIT 5를 SQL에 전달한다")
    void 반경검색_maxResults5_SQLLimit5전달() {
        List<LocationService> dbResults = IntStream.range(0, 5)
                .mapToObj(this::dummyService)
                .toList();
        when(locationServiceRepository.findByRadius(anyDouble(), anyDouble(), anyDouble(),
                any(), any(), any(), eq(5)))
                .thenReturn(dbResults);
        when(locationServiceConverter.toDTO(any())).thenReturn(new LocationServiceDTO());

        List<LocationServiceDTO> result = service.searchLocationServicesByLocation(
                37.5, 127.0, 3000, null, null, null, 5);

        verify(locationServiceRepository).findByRadius(
                anyDouble(), anyDouble(), anyDouble(), any(), any(), any(), eq(5));
        assertThat(result).hasSize(5);
    }

    @Test
    @DisplayName("반경검색 sort=stable: stable 정렬값을 repository에 전달한다")
    void 반경검색_stableSort_전달() {
        List<LocationService> dbResults = IntStream.range(0, 3)
                .mapToObj(this::dummyService)
                .toList();
        when(locationServiceRepository.findByRadius(anyDouble(), anyDouble(), anyDouble(),
                any(), any(), eq("stable"), eq(300)))
                .thenReturn(dbResults);
        when(locationServiceConverter.toDTO(any())).thenReturn(new LocationServiceDTO());

        List<LocationServiceDTO> result = service.searchLocationServicesByLocation(
                37.5, 127.0, 3000, null, null, "stable", 300);

        verify(locationServiceRepository).findByRadius(eq(37.5), eq(127.0), eq(3000.0),
                isNull(), isNull(), eq("stable"), eq(300));
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("지역검색 maxResults=null: dbLimit=50 SQL 전달 — 반경검색과 동작 다름")
    void 지역검색_maxResultsNull_dbLimit50적용() {
        List<LocationService> dbResults = IntStream.range(0, 30)
                .mapToObj(this::dummyService)
                .toList();
        when(locationServiceRepository.findBySido(any(), any(), any(), eq(50)))
                .thenReturn(dbResults);
        when(locationServiceConverter.toDTO(any())).thenReturn(new LocationServiceDTO());

        List<LocationServiceDTO> result = service.searchLocationServicesByRegion(
                "서울특별시", null, null, null, null);

        verify(locationServiceRepository).findBySido(any(), any(), any(), eq(50));
        assertThat(result).hasSize(30);
    }
}
