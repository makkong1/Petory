package com.linkup.Petory.domain.petRecommendation.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_pet_intent_signal", indexes = {
        @Index(name = "idx_user_signal_active", columnList = "user_idx, expires_at, created_at"),
        @Index(name = "idx_signal_source",      columnList = "source_type, source_id")
})
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserPetIntentSignal {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_idx", nullable = false)
    private Long userIdx;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;   // COMMUNITY | CARE | LOCATION_SEARCH

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "intent_domain", nullable = false, length = 50)
    private String intentDomain;

    @Column(name = "intent", nullable = false, length = 50)
    private String intent;

    @Column(name = "recommended_categories", columnDefinition = "JSON")
    private String recommendedCategories;

    @Column(name = "confidence", nullable = false)
    private Double confidence;

    @Column(name = "intent_tags", columnDefinition = "JSON")
    private String intentTags;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
