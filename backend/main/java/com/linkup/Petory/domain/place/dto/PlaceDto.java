package com.linkup.Petory.domain.place.dto;

import com.linkup.Petory.domain.place.entity.Place;
import com.linkup.Petory.domain.place.entity.PlaceStatus;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class PlaceDto {
    private Long id;
    private String name, address, category;
    private Double lat, lng, confidence;
    private PlaceStatus status;
    private String primarySource;
    private Long legacyLocationserviceId;
    private String createdAt;

    public static PlaceDto from(Place p) {
        return PlaceDto.builder()
            .id(p.getId()).name(p.getName()).address(p.getAddress())
            .category(p.getCategory()).lat(p.getLat()).lng(p.getLng())
            .confidence(p.getConfidence()).status(p.getStatus())
            .primarySource(p.getPrimarySource())
            .legacyLocationserviceId(p.getLegacyLocationserviceId())
            .createdAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : null)
            .build();
    }
}
