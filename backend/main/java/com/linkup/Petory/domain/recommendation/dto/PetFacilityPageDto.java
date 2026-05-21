package com.linkup.Petory.domain.recommendation.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PetFacilityPageDto {
    private List<PetFacilityDto> items;
    @JsonProperty("next_cursor")
    private Long nextCursor;
    @JsonProperty("has_next")
    private boolean hasNext;
}
