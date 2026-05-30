package com.linkup.Petory.domain.petRecommendation.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "place_interaction_log", indexes = {
        @Index(name = "idx_place_interaction", columnList = "location_idx, created_at")
})
@Getter @Builder @NoArgsConstructor @AllArgsConstructor
public class PlaceInteractionLog {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_idx")
    private Long userIdx;

    @Column(name = "location_idx", nullable = false)
    private Long locationIdx;

    @Column(name = "interaction_type", nullable = false, length = 20)
    private String interactionType;   // VIEW | NAVIGATE | FAVORITE

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
