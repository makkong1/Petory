package com.linkup.Petory.dto;

import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareRequestDTO {
    private Long idx;
    private Long userId;
    private String username;
    private String title;
    private String description;
    private LocalDateTime date;
    private String status;
    private List<CareApplicationDTO> applications;
}
