package com.linkup.Petory.domain.location.service;

import java.util.Collections;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.linkup.Petory.domain.location.dto.LocationServiceLoadResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 위치 서비스 관리자 서비스
 * 주의: 카카오맵 API 의존성 제거로 인해 장소 데이터 수집 기능은 비활성화되었습니다.
 * 현재는 공공데이터 CSV 배치 업로드만 지원합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceAdminService {

    // private final LocationServiceRepository locationServiceRepository;

    /**
     * 초기 데이터 로드 (카카오맵 API 의존성 제거로 인해 비활성화)
     * 현재는 공공데이터 CSV 배치 업로드만 지원합니다.
     * 
     * @param region               지역명
     * @param maxResultsPerKeyword 키워드당 최대 결과 수
     * @param customKeywordsRaw    커스텀 키워드 (쉼표 구분)
     * @return 로드 결과 응답
     */
    @Transactional
    public LocationServiceLoadResponse loadInitialData(String region,
            Integer maxResultsPerKeyword,
            String customKeywordsRaw) {

        log.warn("카카오맵 API 의존성 제거로 인해 장소 데이터 수집 기능이 비활성화되었습니다. 공공데이터 CSV 배치 업로드를 사용해주세요.");

        // 현재는 레파지토리를 사용하지 않지만, 향후 기능 활성화 시 사용 예정
        // locationServiceRepository는 도메인 레파지토리 패턴에 따라 유지

        return LocationServiceLoadResponse.builder()
                .message("카카오맵 API 의존성 제거로 인해 장소 데이터 수집 기능이 비활성화되었습니다. 공공데이터 CSV 배치 업로드를 사용해주세요.")
                .region(StringUtils.hasText(region) ? region.trim() : "서울특별시")
                .keywords(Collections.emptyList())
                .keywordCount(0)
                .maxResultsPerKeyword(0)
                .totalLimit(0)
                .fetchedCount(0)
                .savedCount(0)
                .duplicateCount(0)
                .skippedCount(0)
                .build();
    }
}
