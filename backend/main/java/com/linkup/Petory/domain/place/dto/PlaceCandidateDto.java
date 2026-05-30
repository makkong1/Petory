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
    private String reviewedBy;
    private String reviewedAt;
    private String rejectionReason;
    private String scoreBreakdown;

    public static PlaceCandidateDto from(PlaceCandidate c) {
        return PlaceCandidateDto.builder()
            .id(c.getId()).rawName(c.getRawName()).rawAddress(c.getRawAddress())
            .lat(c.getLat()).lng(c.getLng()).collectedFrom(c.getCollectedFrom())
            .evidenceText(c.getEvidenceText()).decisionReason(c.getDecisionReason())
            .confidenceScore(c.getConfidenceScore()).decisionStatus(c.getDecisionStatus())
            .matchedPlaceId(c.getMatchedPlaceId())
            .matchedLocationserviceId(c.getMatchedLocationserviceId())
            .collectedAt(c.getCollectedAt() != null ? c.getCollectedAt().toString() : null)
            .reviewedBy(c.getReviewedBy())
            .reviewedAt(c.getReviewedAt() != null ? c.getReviewedAt().toString() : null)
            .rejectionReason(c.getRejectionReason())
            .scoreBreakdown(c.getScoreBreakdown())
            .build();
    }
}
