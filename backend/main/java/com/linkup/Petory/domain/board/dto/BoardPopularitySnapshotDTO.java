package com.linkup.Petory.domain.board.dto;

import com.linkup.Petory.domain.board.entity.PopularityPeriodType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 인기 게시글 스냅샷 응답 DTO
 * <p>record: Java 16+ 불변 데이터 캐리어. 생성자/Getter/equals/hashCode/toString 자동 생성.</p>
 *
 * @param snapshotId    스냅샷 ID
 * @param boardId       게시글 ID
 * @param periodType    집계 기간 타입 (WEEKLY, MONTHLY)
 * @param periodStartDate 집계 시작일
 * @param periodEndDate   집계 종료일
 * @param ranking       순위
 * @param popularityScore 인기도 점수
 * @param likeCount     좋아요 수
 * @param commentCount  댓글 수
 * @param viewCount     조회수
 * @param boardTitle    게시글 제목
 * @param boardCategory 게시글 카테고리
 * @param boardFilePath 대표 이미지 URL
 * @param createdAt     스냅샷 생성 시각
 */
public record BoardPopularitySnapshotDTO(
        Long snapshotId,
        Long boardId,
        PopularityPeriodType periodType,
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        Integer ranking,
        Integer popularityScore,
        Integer likeCount,
        Integer commentCount,
        Integer viewCount,
        String boardTitle,
        String boardCategory,
        String boardFilePath,
        LocalDateTime createdAt
) {
}
