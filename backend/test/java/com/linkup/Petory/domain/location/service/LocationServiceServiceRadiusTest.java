package com.linkup.Petory.domain.location.service;

import com.linkup.Petory.domain.location.converter.LocationServiceConverter;
import com.linkup.Petory.domain.location.dto.LocationServiceDTO;
import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LocationServiceService 반경 검색 버그 회귀 테스트
 *
 * 버그: findByRadius 호출 시 SQL LIMIT 파라미터 없음 → DB 전체 조회 후 Java stream.limit()
 * 지역 검색(searchLocationServicesByRegion)은 dbLimit을 SQL에 전달하지만,
 * 반경 검색(searchLocationServicesByLocation)은 SQL에 LIMIT이 없음.
 */
@ExtendWith(MockitoExtension.class)
class LocationServiceServiceRadiusTest {

    @Mock
    private LocationServiceConverter locationServiceConverter;

    @Mock
    private LocationServiceRepository locationServiceRepository;

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
    @DisplayName("반경검색 maxResults=null: findByRadius에 LIMIT 없이 호출 — DB가 전체 레코드 반환")
    void 반경검색_maxResultsNull_DB전체조회() {
        // given: DB에 200개 레코드 존재
        List<LocationService> dbResults = IntStream.range(0, 200)
                .mapToObj(this::dummyService)
                .toList();
        when(locationServiceRepository.findByRadius(anyDouble(), anyDouble(), anyDouble(),
                any(), any(), any()))
                .thenReturn(dbResults);
        when(locationServiceConverter.toDTO(any())).thenReturn(new LocationServiceDTO());

        // when: maxResults = null (size 미전달 시나리오)
        var result = service.searchLocationServicesByLocation(
                37.5, 127.0, 3000, null, null, null, null);

        // then: ★ 버그 — findByRadius는 SQL LIMIT 없이 200건 전부 반환
        // (지역 검색이었다면 dbLimit=50으로 SQL에서 잘렸을 것)
        // sort=null → normalizeSort → "distance" 기본값
        verify(locationServiceRepository).findByRadius(eq(37.5), eq(127.0), eq(3000.0),
                isNull(), isNull(), eq("distance"));

        // Java stream.limit도 미적용 (maxResults=null이므로) → 200건 그대로 서비스까지 올라옴
        assertThat(result).hasSize(200);
    }

    @Test
    @DisplayName("반경검색 maxResults=5: DB는 여전히 전체 조회, Java에서만 5개로 자름")
    void 반경검색_maxResults5_DB는전체조회_Java에서자름() {
        // given: DB에 100개 레코드
        List<LocationService> dbResults = IntStream.range(0, 100)
                .mapToObj(this::dummyService)
                .toList();
        when(locationServiceRepository.findByRadius(anyDouble(), anyDouble(), anyDouble(),
                any(), any(), any()))
                .thenReturn(dbResults); // DB는 LIMIT 없이 100건 반환
        when(locationServiceConverter.toDTO(any())).thenReturn(new LocationServiceDTO());

        // when: maxResults = 5
        var result = service.searchLocationServicesByLocation(
                37.5, 127.0, 3000, null, null, null, 5);

        // then: findByRadius는 LIMIT 파라미터 없이 호출됨 (SQL에서 안 잘림)
        verify(locationServiceRepository).findByRadius(
                anyDouble(), anyDouble(), anyDouble(), any(), any(), any());

        // Java stream.limit(5)로 잘려서 결과는 5개
        assertThat(result).hasSize(5);
        // ★ 핵심: DB는 100건을 조회했지만 5개만 사용 → 95건 낭비
    }

    @Test
    @DisplayName("지역검색 maxResults=null: dbLimit=50 SQL 전달 — 반경검색과 동작 다름")
    void 지역검색_maxResultsNull_dbLimit50적용() {
        // given: searchLocationServicesByRegion 호출 시 sido 전달
        List<LocationService> dbResults = IntStream.range(0, 30)
                .mapToObj(this::dummyService)
                .toList();
        when(locationServiceRepository.findBySido(any(), any(), any(), eq(50)))
                .thenReturn(dbResults);
        when(locationServiceConverter.toDTO(any())).thenReturn(new LocationServiceDTO());

        // when: maxResults = null
        var result = service.searchLocationServicesByRegion(
                "서울특별시", null, null, null, null, null, null);

        // then: SQL에 LIMIT 50 전달됨
        verify(locationServiceRepository).findBySido(any(), any(), any(), eq(50));
        assertThat(result).hasSize(30);
    }
}
