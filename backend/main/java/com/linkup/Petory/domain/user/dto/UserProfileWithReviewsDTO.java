package com.linkup.Petory.domain.user.dto;

import java.util.List;

import com.linkup.Petory.domain.care.dto.CareReviewDTO;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfileWithReviewsDTO {
    private UsersDTO user;
    private List<CareReviewDTO> reviews;
    private Double averageRating;
    private Integer reviewCount;
}

