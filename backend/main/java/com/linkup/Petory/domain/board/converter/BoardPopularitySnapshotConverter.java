package com.linkup.Petory.domain.board.converter;

import com.linkup.Petory.domain.board.dto.BoardPopularitySnapshotDTO;
import com.linkup.Petory.domain.board.entity.BoardPopularitySnapshot;
import org.springframework.stereotype.Component;

@Component
public class BoardPopularitySnapshotConverter {

    public BoardPopularitySnapshotDTO toDTO(BoardPopularitySnapshot snapshot) {
        return BoardPopularitySnapshotDTO.builder()
                .snapshotId(snapshot.getSnapshotId())
                .boardId(snapshot.getBoard() != null ? snapshot.getBoard().getIdx() : null)
                .periodType(snapshot.getPeriodType())
                .periodStartDate(snapshot.getPeriodStartDate())
                .periodEndDate(snapshot.getPeriodEndDate())
                .ranking(snapshot.getRanking())
                .popularityScore(snapshot.getPopularityScore())
                .likeCount(snapshot.getLikeCount())
                .commentCount(snapshot.getCommentCount())
                .viewCount(snapshot.getViewCount())
                .createdAt(snapshot.getCreatedAt())
                .build();
    }

    public BoardPopularitySnapshot toEntity(BoardPopularitySnapshotDTO dto) {
        BoardPopularitySnapshot snapshot = new BoardPopularitySnapshot();
        snapshot.setSnapshotId(dto.getSnapshotId());
        snapshot.setPeriodType(dto.getPeriodType());
        snapshot.setPeriodStartDate(dto.getPeriodStartDate());
        snapshot.setPeriodEndDate(dto.getPeriodEndDate());
        snapshot.setRanking(dto.getRanking());
        snapshot.setPopularityScore(dto.getPopularityScore());
        snapshot.setLikeCount(dto.getLikeCount());
        snapshot.setCommentCount(dto.getCommentCount());
        snapshot.setViewCount(dto.getViewCount());
        snapshot.setCreatedAt(dto.getCreatedAt());
        return snapshot;
    }
}
