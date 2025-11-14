package com.linkup.Petory.domain.location.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 카카오 로컬 API 주소 검색 응답 DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoAddressDTO {
    private Meta meta;
    private List<Document> documents;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        @JsonProperty("total_count")
        private Integer totalCount;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Document {
        private Address address;

        @JsonProperty("road_address")
        private RoadAddress roadAddress;

        private String x; // longitude
        private String y; // latitude

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Address {
            @JsonProperty("address_name")
            private String addressName;
            private String x; // longitude
            private String y; // latitude
        }

        @Data
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class RoadAddress {
            @JsonProperty("address_name")
            private String addressName;
            private String x; // longitude
            private String y; // latitude
        }
    }
}
