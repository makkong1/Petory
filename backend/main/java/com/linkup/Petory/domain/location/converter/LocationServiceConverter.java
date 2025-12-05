package com.linkup.Petory.domain.location.converter;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.linkup.Petory.domain.location.dto.KakaoPlaceDTO;
import com.linkup.Petory.domain.location.dto.LocationServiceDTO;
import com.linkup.Petory.domain.location.entity.LocationService;

@Component
public class LocationServiceConverter {

    public LocationServiceDTO toDTO(LocationService service) {
        // category 필드 우선, 없으면 category3, category2, category1 순서
        String category = service.getCategory() != null ? service.getCategory() :
                         service.getCategory3() != null ? service.getCategory3() :
                         service.getCategory2() != null ? service.getCategory2() :
                         service.getCategory1();
        
        return LocationServiceDTO.builder()
                .idx(service.getIdx())
                .name(service.getName())
                .category(category)
                .category1(service.getCategory1())
                .category2(service.getCategory2())
                .category3(service.getCategory3())
                .address(service.getAddress())
                .detailAddress(service.getDetailAddress())
                .sido(service.getSido())
                .sigungu(service.getSigungu())
                .eupmyeondong(service.getEupmyeondong())
                .ri(service.getRi())
                .bunji(service.getBunji())
                .roadName(service.getRoadName())
                .buildingNumber(service.getBuildingNumber())
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
        
        // category 필드 설정 (category3 우선, 없으면 category)
        String categoryValue = dto.getCategory3() != null ? dto.getCategory3() : 
                               dto.getCategory2() != null ? dto.getCategory2() :
                               dto.getCategory1() != null ? dto.getCategory1() : dto.getCategory();
        
        return LocationService.builder()
                .idx(dto.getIdx())
                .name(dto.getName())
                .category(categoryValue) // category 필드도 설정
                .category1(dto.getCategory1())
                .category2(dto.getCategory2())
                .category3(dto.getCategory3() != null ? dto.getCategory3() : dto.getCategory()) // category를 category3에 저장
                .sido(dto.getSido())
                .sigungu(dto.getSigungu())
                .eupmyeondong(dto.getEupmyeondong())
                .ri(dto.getRi())
                .bunji(dto.getBunji())
                .roadName(dto.getRoadName())
                .buildingNumber(dto.getBuildingNumber())
                .zipCode(dto.getZipCode())
                .address(dto.getAddress())
                .detailAddress(dto.getDetailAddress())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .rating(dto.getRating())
                .description(dto.getDescription())
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
                .dataSource(dto.getDataSource() != null ? dto.getDataSource() : "KAKAO")
                .build();
    }

    /**
     * KakaoPlaceDTO.Document를 LocationServiceDTO로 변환
     */
    public LocationServiceDTO fromKakaoDocument(KakaoPlaceDTO.Document document) {
        Double latitude = parseDoubleOrNull(document.getY());
        Double longitude = parseDoubleOrNull(document.getX());

        return LocationServiceDTO.builder()
                .idx(null)
                .externalId(document.getId())
                .name(document.getPlaceName())
                .category(document.getCategoryName())
                .address(document.getAddressName())
                .detailAddress(document.getRoadAddressName())
                .latitude(latitude)
                .longitude(longitude)
                .rating(null)
                .phone(document.getPhone())
                .openingTime(null)
                .closingTime(null)
                .imageUrl(null)
                .website(document.getPlace_url())
                .placeUrl(document.getPlace_url())
                .description(document.getCategoryName())
                .petFriendly(true)
                .petPolicy(null)
                .reviewCount(null)
                .reviews(null)
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
}
