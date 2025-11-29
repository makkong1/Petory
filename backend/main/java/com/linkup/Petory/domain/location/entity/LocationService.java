package com.linkup.Petory.domain.location.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "locationservice")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    private String name;

    private String category;

    private String address;

    private Double latitude;

    private Double longitude;

    // 공간 데이터 타입 (POINT) - JPA에서는 일반적으로 사용하지 않으므로 주석 처리
    // 실제 사용 시 Hibernate Spatial 등 추가 라이브러리 필요
    // @Column(name = "coordinates", nullable = false, columnDefinition = "POINT SRID 4326")
    // private Point coordinates;

    private Double rating;

    private String phone;

    @Column(name = "opening_time")
    private java.time.LocalTime openingTime;

    @Column(name = "closing_time")
    private java.time.LocalTime closingTime;

    private String imageUrl;
    private String website;

    private String description; // 서비스 설명

    @Column(name = "detail_address")
    private String detailAddress;

    @Column(name = "pet_friendly")
    @Builder.Default
    private Boolean petFriendly = false;

    @Column(name = "pet_policy", length = 255)
    private String petPolicy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL)
    private List<LocationServiceReview> reviews;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
