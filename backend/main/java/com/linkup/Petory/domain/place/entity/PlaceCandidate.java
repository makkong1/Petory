package com.linkup.Petory.domain.place.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "place_candidates", indexes = {
    @Index(name = "idx_candidates_status_score", columnList = "decision_status, confidence_score DESC"),
    @Index(name = "idx_candidates_dedup", columnList = "raw_name, raw_address"),
    @Index(name = "idx_candidates_collected", columnList = "collected_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlaceCandidate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "raw_name", nullable = false)
    private String rawName;

    @Column(name = "raw_address")
    private String rawAddress;

    private Double lat;
    private Double lng;

    @Column(name = "collected_from", length = 100)
    private String collectedFrom;

    @Column(name = "evidence_text", columnDefinition = "TEXT")
    private String evidenceText;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_status", nullable = false)
    @Builder.Default
    private CandidateDecisionStatus decisionStatus = CandidateDecisionStatus.PENDING;

    @Column(name = "decision_reason", columnDefinition = "TEXT")
    private String decisionReason;

    @Column(name = "score_breakdown", columnDefinition = "JSON")
    private String scoreBreakdown;

    @Column(name = "matched_place_id")
    private Long matchedPlaceId;

    @Column(name = "matched_locationservice_id")
    private Long matchedLocationserviceId;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onPersist() {
        if (this.collectedAt == null) {
            this.collectedAt = LocalDateTime.now();
        }
    }
}
