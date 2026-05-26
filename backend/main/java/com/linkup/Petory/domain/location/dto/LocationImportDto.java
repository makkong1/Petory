package com.linkup.Petory.domain.location.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocationImportDto {
    private String name;      // 필수
    private String category;  // "grooming"|"hospital"|"pharmacy"|"cafe"|"restaurant"|"pension"|"boarding"|"hotel"|"supplies"
    private String address;   // 필수
    private String sido;
    private String sigungu;
    private Double lat;       // 필수
    private Double lng;       // 필수
    private String phone;
    private String status;    // "폐업" 이면 skip
}
