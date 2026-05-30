package com.linkup.Petory.domain.place.dto;

import com.linkup.Petory.domain.place.entity.CandidateDecisionStatus;
import com.linkup.Petory.domain.place.entity.PlaceCandidate;
import lombok.Builder;
import lombok.Getter;

@Getter @Builder
public class PlaceCandidateDto {
    private Long id;
    private String rawName, rawAddress;
    private Double lat, lng;
    private String collectedFrom, evidenceText, decisionReason;
    private Double confidenceScore;
    private CandidateDecisionStatus decisionStatus;
    private Long matchedPlaceId, matchedLocationserviceId;
    private String collectedAt;

    public static PlaceCandidateDto from(PlaceCandidate c) {
        return PlaceCandidateDto.builder()
            .id(c.getId()).rawName(c.getRawName()).rawAddress(c.getRawAddress())
            .lat(c.getLat()).lng(c.getLng()).collectedFrom(c.getCollectedFrom())
            .evidenceText(c.getEvidenceText()).decisionReason(c.getDecisionReason())
            .confidenceScore(c.getConfidenceScore()).decisionStatus(c.getDecisionStatus())
            .matchedPlaceId(c.getMatchedPlaceId())
            .matchedLocationserviceId(c.getMatchedLocationserviceId())
            .collectedAt(c.getCollectedAt() != null ? c.getCollectedAt().toString() : null)
            .build();
    }
}
