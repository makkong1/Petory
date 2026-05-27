package com.linkup.Petory.domain.location.converter;

import com.linkup.Petory.domain.location.dto.LocationImportDto;
import com.linkup.Petory.domain.location.entity.LocationService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Component
public class LocationImportConverter {

    // LocationImportDto → LocationService 엔티티 변환 (신규 삽입 전용, dataSource=BATCH_IMPORT 고정)
    public LocationService toEntity(LocationImportDto dto) {
        return LocationService.builder()
                .name(dto.getName())
                .category1("반려동물 서비스")
                .category2("반려동물")
                .category3(categoryLabel(dto.getCategory()))
                .sido(dto.getSido())
                .sigungu(dto.getSigungu())
                .address(dto.getAddress())
                .phone(dto.getPhone())
                .latitude(dto.getLat())
                .longitude(dto.getLng())
                .petFriendly(true)
                .dataSource("BATCH_IMPORT")
                .lastUpdated(LocalDate.now())
                .build();
    }

    // pet-data-api context 문자열 → 한국어 카테고리 레이블 변환
    public String categoryLabel(String category) {
        if (!StringUtils.hasText(category)) return "반려동물 시설";
        return switch (category) {
            case "grooming"   -> "미용";
            case "hospital"   -> "동물병원";
            case "pharmacy"   -> "동물약국";
            case "cafe"       -> "카페";
            case "restaurant" -> "식당";
            case "pension"    -> "펜션";
            case "boarding"   -> "위탁관리";
            case "hotel"      -> "호텔";
            case "supplies"   -> "반려동물용품";
            default           -> category;
        };
    }
}
