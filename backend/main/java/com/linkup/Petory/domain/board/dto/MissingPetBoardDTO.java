package com.linkup.Petory.domain.board.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.linkup.Petory.domain.board.entity.MissingPetGender;
import com.linkup.Petory.domain.board.entity.MissingPetStatus;
import com.linkup.Petory.domain.file.dto.FileDTO;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 실종 반려동물 게시글 응답 DTO. 반려동물 특징·실종 위치·댓글 목록·첨부파일을 포함한다. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissingPetBoardDTO {
    private Long idx;
    private Long userId;
    private String username;
    private String nickname;
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
    private Boolean deleted;
    private LocalDateTime deletedAt;
    private List<MissingPetCommentDTO> comments;
    private Integer commentCount;
    private List<FileDTO> attachments;
}
