package com.linkup.Petory.converter;

import org.springframework.stereotype.Component;

import com.linkup.Petory.dto.LocationServiceDTO;
import com.linkup.Petory.entity.LocationService;

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
}
