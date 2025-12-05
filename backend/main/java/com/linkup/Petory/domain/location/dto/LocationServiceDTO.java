package com.linkup.Petory.domain.location.dto;

import lombok.*;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LocationServiceDTO {

    // 기본 필드
    private Long idx;
    private String externalId;
    private String name;
    private String category; // category3 우선, 없으면 category2, category1
    private String address;
    private String detailAddress;
    private Double latitude;
    private Double longitude;
    private Double rating;
    private String phone;
    private String website;
    private String placeUrl;
    private String description;
    private Boolean petFriendly;
    private String petPolicy; // petRestrictions 매핑
    
    // 카테고리 계층 구조
    private String category1;
    private String category2;
    private String category3;
    
    // 주소 구성 요소
    private String sido;
    private String sigungu;
    private String eupmyeondong;
    private String ri;
    private String bunji;
    private String roadName;
    private String buildingNumber;
    private String zipCode;
    
    // 운영 정보
    private String closedDay;
    private String operatingHours; // 운영시간 문자열 (예: "월~금 09:00~18:00")
    private Boolean parkingAvailable;
    private String priceInfo;
    
    // 반려동물 정보
    private Boolean isPetOnly;
    private String petSize;
    private String petRestrictions;
    private String petExtraFee;
    
    // 장소 정보
    private Boolean indoor;
    private Boolean outdoor;
    
    // 메타데이터
    private java.time.LocalDate lastUpdated;
    private String dataSource;
    
    // 하위 호환성을 위한 필드 (deprecated)
    @Deprecated
    private java.time.LocalTime openingTime; // operatingHours로 통합됨
    @Deprecated
    private java.time.LocalTime closingTime; // operatingHours로 통합됨
    @Deprecated
    private String imageUrl; // 제거됨
    
    // 리뷰 정보
    private Integer reviewCount; // 리뷰 개수
    private List<LocationServiceReviewDTO> reviews;
}