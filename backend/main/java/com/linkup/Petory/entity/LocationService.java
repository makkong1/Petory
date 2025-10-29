package com.linkup.Petory.entity;

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

    private String phoneNumber; // 전화번호

    private String operatingHours; // 운영시간

    private String description; // 서비스 설명

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL)
    private List<LocationServiceReview> reviews;
}
