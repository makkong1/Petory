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

    @Column(nullable = false, length = 150)
    private String name;

    // 카테고리 필드 제거됨 - category3, category2, category1 사용

    // 카테고리 계층 구조
    @Column(name = "category1", length = 100)
    private String category1; // 카테고리1 (대분류)

    @Column(name = "category2", length = 100)
    private String category2; // 카테고리2 (중분류)

    @Column(name = "category3", length = 100)
    private String category3; // 카테고리3 (소분류) - 기본 카테고리로 사용

    // 주소 구성 요소
    @Column(name = "sido", length = 50)
    private String sido; // 시도

    @Column(name = "sigungu", length = 50)
    private String sigungu; // 시군구

    @Column(name = "eupmyeondong", length = 50)
    private String eupmyeondong; // 읍면동

    @Column(name = "road_name", length = 100)
    private String roadName; // 도로명

    // 주소 전체 (도로명주소 우선, 없으면 지번주소)
    @Column(name = "address", length = 255)
    private String address; // 기본 주소

    @Column(name = "zip_code", length = 10)
    private String zipCode; // 우편번호

    // 위치 정보
    private Double latitude;
    private Double longitude;
    // 공간 데이터 타입 (POINT) - 필요 시 Hibernate Spatial 사용
    // @Column(name = "coordinates", columnDefinition = "POINT SRID 4326")
    // private Point coordinates;

    private String phone;
    private String website;

    // 운영 정보 (openingTime, closingTime 제거, operating_hours 문자열로 통합)
    @Column(name = "closed_day", length = 255)
    private String closedDay; // 휴무일

    @Column(name = "operating_hours", length = 255)
    private String operatingHours; // 운영시간 (예: "월~금 09:00~18:00")

    @Column(name = "parking_available")
    private Boolean parkingAvailable; // 주차 가능여부 (Boolean으로 변경)

    // 가격 정보 (entranceFee -> price_info로 통합)
    @Column(name = "price_info", length = 255)
    private String priceInfo; // 가격 정보 (입장료, 이용료 등)

    // 반려동물 정책
    @Column(name = "pet_friendly")
    @Builder.Default
    private Boolean petFriendly = false; // 반려동물 동반 가능

    @Column(name = "is_pet_only")
    private Boolean isPetOnly; // 반려동물 전용 (Boolean으로 변경)

    @Column(name = "pet_size", length = 100)
    private String petSize; // 입장 가능 동물 크기

    @Column(name = "pet_restrictions", length = 255)
    private String petRestrictions; // 반려동물 제한사항

    @Column(name = "pet_extra_fee", length = 255)
    private String petExtraFee; // 애견 동반 추가 요금

    // 장소 정보 (Boolean으로 변경)
    @Column(name = "indoor")
    private Boolean indoor; // 실내 여부

    @Column(name = "outdoor")
    private Boolean outdoor; // 실외 여부

    @Column(columnDefinition = "TEXT")
    private String description; // 서비스 설명

    private Double rating; // 평균 평점

    @Column(name = "last_updated")
    private java.time.LocalDate lastUpdated; // 최종작성일

    @Column(name = "data_source", length = 50)
    @Builder.Default
    private String dataSource = "PUBLIC"; // 데이터 출처 (PUBLIC: 공공데이터)

    // Soft Delete 필드
    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    // deleted_at 컬럼 추가 필요
    // 마이그레이션 SQL 실행: docs/migration/db/add_locationservice_soft_delete_columns.sql
    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;

    // created_at, updated_at는 DB에 없으므로 주석 처리
    // 필요시 DB에 컬럼 추가 후 활성화
    // @Column(name = "created_at", nullable = false, updatable = false)
    // private LocalDateTime createdAt;

    // @Column(name = "updated_at", nullable = false)
    // private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "service", cascade = CascadeType.ALL)
    private List<LocationServiceReview> reviews;

    // @PrePersist, @PreUpdate는 created_at, updated_at가 있을 때만 사용
    // @PrePersist
    // protected void onCreate() {
    // LocalDateTime now = LocalDateTime.now();
    // this.createdAt = now;
    // this.updatedAt = now;
    // }

    // @PreUpdate
    // protected void onUpdate() {
    // this.updatedAt = LocalDateTime.now();
    // }
}
