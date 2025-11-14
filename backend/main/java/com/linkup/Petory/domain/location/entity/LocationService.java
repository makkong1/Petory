package com.linkup.Petory.domain.location.entity;

import jakarta.persistence.*;
import lombok.*;
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

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL)
    private List<LocationServiceReview> reviews;
}
