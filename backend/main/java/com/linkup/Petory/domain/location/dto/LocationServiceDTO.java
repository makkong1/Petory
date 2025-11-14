package com.linkup.Petory.domain.location.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationServiceDTO {

    private Long idx;
    private String externalId;
    private String name;
    private String category;
    private String address;
    private String detailAddress;
    private Double latitude;
    private Double longitude;
    private Double rating;
    private String phone;
    private java.time.LocalTime openingTime;
    private java.time.LocalTime closingTime;
    private String imageUrl;
    private String website;
    private String placeUrl;
    private String description;
    private Boolean petFriendly;
    private String petPolicy;
    private Integer reviewCount; // 리뷰 개수
    private List<LocationServiceReviewDTO> reviews;
}