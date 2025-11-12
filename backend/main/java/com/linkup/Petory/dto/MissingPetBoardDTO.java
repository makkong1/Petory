package com.linkup.Petory.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.linkup.Petory.entity.MissingPetGender;
import com.linkup.Petory.entity.MissingPetStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissingPetBoardDTO {
    private Long idx;
    private Long userId;
    private String username;
    private String title;
    private String content;
    private String phoneNumber;
    private String petName;
    private String species;
    private String breed;
    private MissingPetGender gender;
    private String age;
    private String color;
    private LocalDate lostDate;
    private String lostLocation;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private MissingPetStatus status;
    private String imageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<MissingPetCommentDTO> comments;
    private Integer commentCount;
}
