package com.linkup.Petory.domain.location.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.linkup.Petory.domain.location.converter.LocationServiceConverter;
import com.linkup.Petory.domain.location.repository.LocationServiceRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;

/**
 * 지역 검색이 시도명을 현행 법정명으로 통과시키는지 본다.
 *
 * <p>
 * {@code SidoNormalizerTest} 만으로는 부족하다 — 정규화기가 올바르더라도 서비스가 그것을
 * 호출하지 않으면 결함은 그대로 남고 테스트는 초록불이다. 그래서 <b>리포지토리에 무엇이
 * 전달되는지</b>를 확인한다.
 */
@ExtendWith(MockitoExtension.class)
class LocationServiceServiceSidoNormalizeTest {

    @Mock private LocationServiceConverter locationServiceConverter;
    @Mock private LocationServiceRepository locationServiceRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private UsersRepository usersRepository;

    @InjectMocks private LocationServiceService service;

    @Test
    @DisplayName("「강원도」로 검색하면 리포지토리에는 「강원특별자치도」가 간다")
    void 구_법정명으로_검색해도_현행명으로_조회한다() {
        when(locationServiceRepository.findBySido(any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        service.searchLocationServicesByRegion("강원도", null, null, null, 50);

        // 정확일치 쿼리라 「강원도」가 그대로 가면 886건 중 884건만 잡힌다.
        verify(locationServiceRepository)
                .findBySido(eq("강원특별자치도"), any(), any(), anyInt());
    }

    @Test
    @DisplayName("「전라북도」로 검색하면 「전북특별자치도」가 간다 — 전에는 0건이었다")
    void 전라북도는_전북특별자치도로() {
        when(locationServiceRepository.findBySido(any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        service.searchLocationServicesByRegion("전라북도", null, null, null, 50);

        verify(locationServiceRepository)
                .findBySido(eq("전북특별자치도"), any(), any(), anyInt());
    }

    @Test
    @DisplayName("이미 현행명이면 그대로 간다")
    void 현행명은_그대로_간다() {
        when(locationServiceRepository.findBySido(any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        service.searchLocationServicesByRegion("서울특별시", null, null, null, 50);

        verify(locationServiceRepository)
                .findBySido(eq("서울특별시"), any(), any(), anyInt());
    }

    @Test
    @DisplayName("sigungu 가 있으면 sido 경로를 타지 않는다 — 기존 분기를 바꾸지 않았다")
    void sigungu_가_있으면_그_경로다() {
        when(locationServiceRepository.findBySigungu(any(), any(), any(), anyInt()))
                .thenReturn(List.of());

        service.searchLocationServicesByRegion("강원도", "춘천시", null, null, 50);

        verify(locationServiceRepository)
                .findBySigungu(eq("춘천시"), any(), any(), anyInt());
    }
}
