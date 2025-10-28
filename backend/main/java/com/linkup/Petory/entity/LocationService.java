package com.linkup.Petory.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "location_service")
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
}
