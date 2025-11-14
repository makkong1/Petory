package com.linkup.Petory.domain.location.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 카카오 로컬 API 장소 검색 응답 DTO
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class KakaoPlaceDTO {
    private Meta meta;
    private List<Document> documents;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        @JsonProperty("total_count")
        private Integer totalCount;

        @JsonProperty("pageable_count")
        private Integer pageableCount;

        @JsonProperty("is_end")
        private Boolean isEnd;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Document {
        private String id;
        @JsonProperty("place_name")
        private String placeName;
        @JsonProperty("category_name")
        private String categoryName;
        @JsonProperty("category_group_code")
        private String categoryGroupCode;
        @JsonProperty("category_group_name")
        private String categoryGroupName;
        private String phone;
        @JsonProperty("address_name")
        private String addressName;
        @JsonProperty("road_address_name")
        private String roadAddressName;
        private String x; // longitude
        private String y; // latitude
        private String distance;
        private String place_url;
        private String link;
    }
}
