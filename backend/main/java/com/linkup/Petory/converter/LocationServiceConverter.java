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
                .latitude(service.getLatitude())
                .longitude(service.getLongitude())
                .rating(service.getRating())
                .build();
    }
}
