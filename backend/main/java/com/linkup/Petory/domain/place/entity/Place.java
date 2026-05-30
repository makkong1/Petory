package com.linkup.Petory.domain.place.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "places", indexes = {
    @Index(name = "idx_places_status_confidence", columnList = "status, confidence DESC"),
    @Index(name = "idx_places_legacy_ls_id", columnList = "legacy_locationservice_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Place {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 255)
    private String address;

    private Double lat;
    private Double lng;

    @Column(length = 100)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PlaceStatus status = PlaceStatus.PENDING;

    @Column(name = "primary_source", length = 50)
    private String primarySource;

    private Double confidence;

    @Column(name = "legacy_locationservice_id")
    private Long legacyLocationserviceId;

    @Column(name = "activated_by", length = 100)
    private String activatedBy;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
