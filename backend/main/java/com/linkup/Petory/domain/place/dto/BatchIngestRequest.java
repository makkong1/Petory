package com.linkup.Petory.domain.place.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.List;

@Getter @NoArgsConstructor
public class BatchIngestRequest {

    private List<CandidateItem> candidates;

    @Getter @NoArgsConstructor
    public static class CandidateItem {
        private String name;
        private String address;
        private Double lat;
        private Double lng;
        private String category;
        private String phone;
        private String collectedFrom;
        private String evidenceText;
    }
}
