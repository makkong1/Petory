package com.linkup.Petory.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PetFacilityDto {
    private Long id;
    @JsonProperty("source_id")
    private String sourceId;
    private String type;
    private String category;
    private String name;
    private String status;
    private String address;
    @JsonProperty("region_city")
    private String regionCity;
    @JsonProperty("region_district")
    private String regionDistrict;
    private String phone;
    private Double lat;
    private Double lng;
}
