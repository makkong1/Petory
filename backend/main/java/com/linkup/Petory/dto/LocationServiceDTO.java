package com.linkup.Petory.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationServiceDTO {

    private Long idx;
    private String name;
    private String category;
    private String address;
    private Double latitude;
    private Double longitude;
    private Double rating;
    private String phoneNumber;
    private String operatingHours;
    private String description;
    private Integer reviewCount; // 리뷰 개수
    private List<LocationServiceReviewDTO> reviews;
}