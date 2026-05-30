package com.linkup.Petory.domain.place.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Table(name = "place_facts", indexes = {
    @Index(name = "idx_facts_place_type", columnList = "place_id, fact_type")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PlaceFact {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "place_id", nullable = false)
    private Long placeId;

    @Column(name = "fact_type", length = 100)
    private String factType;

    @Column(name = "value_text", columnDefinition = "TEXT")
    private String valueText;

    @Column(name = "value_json", columnDefinition = "JSON")
    private String valueJson;

    @Column(length = 100)
    private String source;

    private Double confidence;

    @Column(name = "observed_at")
    private LocalDate observedAt;
}
