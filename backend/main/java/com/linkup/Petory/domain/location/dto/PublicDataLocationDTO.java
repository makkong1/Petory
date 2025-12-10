package com.linkup.Petory.domain.location.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 공공데이터 CSV 파일의 한 행을 매핑하는 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublicDataLocationDTO {
    
    private String facilityName; // 시설명
    private String category1; // 카테고리1
    private String category2; // 카테고리2
    private String category3; // 카테고리3
    private String sidoName; // 시도 명칭
    private String sigunguName; // 시군구 명칭
    private String eupmyeondongName; // 법정읍면동명칭
    private String riName; // 리 명칭
    private String bunji; // 번지
    private String roadName; // 도로명 이름
    private String buildingNumber; // 건물 번호
    private String latitude; // 위도
    private String longitude; // 경도
    private String postalCode; // 우편번호
    private String roadAddress; // 도로명주소
    private String jibunAddress; // 지번주소
    private String phone; // 전화번호
    private String website; // 홈페이지
    private String closedDays; // 휴무일
    private String operatingHours; // 운영시간
    private String parkingAvailable; // 주차 가능여부
    private String entranceFee; // 입장(이용료)가격 정보
    private String petFriendly; // 반려동물 동반 가능정보
    private String petOnly; // 반려동물 전용 정보
    private String petSizeLimit; // 입장 가능 동물 크기
    private String petRestrictions; // 반려동물 제한사항
    private String indoor; // 장소(실내) 여부
    private String outdoor; // 장소(실외) 여부
    private String description; // 기본 정보_장소설명
    private String petAdditionalFee; // 애견 동반 추가 요금
    private String lastUpdatedDate; // 최종작성일
}

