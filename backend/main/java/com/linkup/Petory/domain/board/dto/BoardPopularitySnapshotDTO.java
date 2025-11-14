package com.linkup.Petory.domain.board.dto;

import com.linkup.Petory.domain.board.entity.PopularityPeriodType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoardPopularitySnapshotDTO {

    private Long snapshotId;
    private Long boardId;
    private PopularityPeriodType periodType;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private Integer ranking;
    private Integer popularityScore;
    private Integer likeCount;
    private Integer commentCount;
    private Integer viewCount;
    private String boardTitle;
    private String boardCategory;
    private String boardFilePath;
    private LocalDateTime createdAt;
}
