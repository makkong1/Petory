package com.linkup.Petory.domain.location.converter;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.linkup.Petory.domain.location.dto.KakaoPlaceDTO;
import com.linkup.Petory.domain.location.dto.LocationServiceDTO;
import com.linkup.Petory.domain.location.entity.LocationService;

@Component
public class LocationServiceConverter {

    public LocationServiceDTO toDTO(LocationService service) {
        return LocationServiceDTO.builder()
                .idx(service.getIdx())
                .name(service.getName())
                .category(service.getCategory())
                .address(service.getAddress())
                .detailAddress(service.getDetailAddress())
                .latitude(service.getLatitude())
                .longitude(service.getLongitude())
                .rating(service.getRating())
                .description(service.getDescription())
                .phone(service.getPhone())
                .openingTime(service.getOpeningTime())
                .closingTime(service.getClosingTime())
                .imageUrl(service.getImageUrl())
                .website(service.getWebsite())
                .petFriendly(service.getPetFriendly())
                .petPolicy(service.getPetPolicy())
                .build();
    }

    // fromDTO 메서드도 detailAddress 옮김
    public LocationService fromDTO(LocationServiceDTO dto) {
        return LocationService.builder()
                .idx(dto.getIdx())
                .name(dto.getName())
                .category(dto.getCategory())
                .address(dto.getAddress())
                .detailAddress(dto.getDetailAddress())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .rating(dto.getRating())
                .description(dto.getDescription())
                .phone(dto.getPhone())
                .openingTime(dto.getOpeningTime())
                .closingTime(dto.getClosingTime())
                .imageUrl(dto.getImageUrl())
                .website(dto.getWebsite())
                .petFriendly(dto.getPetFriendly())
                .petPolicy(dto.getPetPolicy())
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
