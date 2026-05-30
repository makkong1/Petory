package com.linkup.Petory.domain.place.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @NoArgsConstructor
public class CandidateApproveRequest {
    private String overrideName;
    private String overrideAddress;
    private String overrideCategory;
    private Double overrideLat;
    private Double overrideLng;
}
