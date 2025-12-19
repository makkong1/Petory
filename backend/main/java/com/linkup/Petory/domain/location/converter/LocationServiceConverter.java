package com.linkup.Petory.domain.location.converter;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.linkup.Petory.domain.location.dto.LocationServiceDTO;
import com.linkup.Petory.domain.location.entity.LocationService;

@Component
public class LocationServiceConverter {

    public LocationServiceDTO toDTO(LocationService service) {
        // category3, category2, category1 순서로 카테고리 결정
        String category = service.getCategory3() != null ? service.getCategory3()
                : service.getCategory2() != null ? service.getCategory2() : service.getCategory1();

        return LocationServiceDTO.builder()
                .idx(service.getIdx())
                .name(service.getName())
                .category(category)
                .category1(service.getCategory1())
                .category2(service.getCategory2())
                .category3(service.getCategory3())
                .address(service.getAddress())
                .sido(service.getSido())
                .sigungu(service.getSigungu())
                .eupmyeondong(service.getEupmyeondong())
                .roadName(service.getRoadName())
                .zipCode(service.getZipCode())
                .latitude(service.getLatitude())
                .longitude(service.getLongitude())
                .rating(service.getRating())
                .description(service.getDescription())
                .phone(service.getPhone())
                .website(service.getWebsite())
                .closedDay(service.getClosedDay())
                .operatingHours(service.getOperatingHours())
                .parkingAvailable(service.getParkingAvailable())
                .priceInfo(service.getPriceInfo())
                .petFriendly(service.getPetFriendly())
                .isPetOnly(service.getIsPetOnly())
                .petSize(service.getPetSize())
                .petRestrictions(service.getPetRestrictions())
                .petPolicy(service.getPetRestrictions()) // 하위 호환성
                .petExtraFee(service.getPetExtraFee())
                .indoor(service.getIndoor())
                .outdoor(service.getOutdoor())
                .lastUpdated(service.getLastUpdated())
                .dataSource(service.getDataSource())
                // 하위 호환성을 위한 deprecated 필드
                .openingTime(null) // operatingHours 문자열로 저장되므로 null
                .closingTime(null) // 필요 시 operatingHours 파싱 필요
                .imageUrl(null) // imageUrl 필드 제거됨
                .build();
    }

    // fromDTO 메서드: DTO를 엔티티로 변환
    public LocationService fromDTO(LocationServiceDTO dto) {
        // operatingHours 우선, 없으면 openingTime/closingTime에서 생성
        String operatingHours = dto.getOperatingHours();
        if (operatingHours == null && dto.getOpeningTime() != null && dto.getClosingTime() != null) {
            operatingHours = String.format("%02d:%02d~%02d:%02d",
                    dto.getOpeningTime().getHour(), dto.getOpeningTime().getMinute(),
                    dto.getClosingTime().getHour(), dto.getClosingTime().getMinute());
        }

        // category3, category2, category1 순서로 카테고리 결정
        String categoryValue = dto.getCategory3() != null ? dto.getCategory3()
                : dto.getCategory2() != null ? dto.getCategory2()
                        : dto.getCategory1() != null ? dto.getCategory1() : dto.getCategory();

        return LocationService.builder()
                .idx(dto.getIdx())
                .name(dto.getName())
                // category 필드 제거됨
                .category1(dto.getCategory1())
                .category2(dto.getCategory2())
                .category3(dto.getCategory3() != null ? dto.getCategory3() : dto.getCategory()) // category를 category3에
                                                                                                // 저장
                .sido(dto.getSido())
                .sigungu(dto.getSigungu())
                .eupmyeondong(dto.getEupmyeondong())
                .roadName(dto.getRoadName())
                .zipCode(cleanZipCode(dto.getZipCode())) // 소수점 제거
                .address(dto.getAddress())
                // detailAddress 필드 제거됨
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .rating(dto.getRating())
                .description(cleanDescription(dto.getDescription(), categoryValue)) // category와 중복 제거
                .phone(dto.getPhone())
                .website(dto.getWebsite())
                .closedDay(dto.getClosedDay())
                .operatingHours(operatingHours)
                .parkingAvailable(dto.getParkingAvailable())
                .priceInfo(dto.getPriceInfo())
                .petFriendly(dto.getPetFriendly())
                .isPetOnly(dto.getIsPetOnly())
                .petSize(dto.getPetSize())
                .petRestrictions(dto.getPetRestrictions() != null ? dto.getPetRestrictions() : dto.getPetPolicy())
                .petExtraFee(dto.getPetExtraFee())
                .indoor(dto.getIndoor())
                .outdoor(dto.getOutdoor())
                .lastUpdated(dto.getLastUpdated())
                .dataSource(dto.getDataSource() != null ? dto.getDataSource() : "PUBLIC")
                .build();
    }

    private Double parseDoubleOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 우편번호 정리 (소수점 제거)
     */
    private String cleanZipCode(String zipCode) {
        if (!StringUtils.hasText(zipCode)) {
            return null;
        }
        // 소수점 제거 (예: "47596.0" -> "47596")
        String cleaned = zipCode.trim().replace(".0", "").replace(".", "");
        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * description 정리 (category와 중복 제거)
     */
    private String cleanDescription(String description, String category) {
        if (!StringUtils.hasText(description)) {
            return null;
        }
        String desc = description.trim();
        // category와 같거나 간단한 값이면 null
        if (category != null && desc.equals(category)) {
            return null;
        }
        // 너무 짧은 설명도 제거 (2글자 이하)
        if (desc.length() <= 2) {
            return null;
        }
        return desc;
    }
}
