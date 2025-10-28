package com.linkup.Petory.dto;

import lombok.*;

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
}
