package com.linkup.Petory.domain.board.converter;

import com.linkup.Petory.domain.board.dto.BoardPopularitySnapshotDTO;
import com.linkup.Petory.domain.board.entity.BoardPopularitySnapshot;
import com.linkup.Petory.domain.board.entity.Board;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class BoardPopularitySnapshotConverter {

    public BoardPopularitySnapshotDTO toDTO(BoardPopularitySnapshot snapshot) {
        Board board = snapshot.getBoard();
        return BoardPopularitySnapshotDTO.builder()
                .snapshotId(snapshot.getSnapshotId())
                .boardId(board != null ? board.getIdx() : null)
                .periodType(snapshot.getPeriodType())
                .periodStartDate(snapshot.getPeriodStartDate())
                .periodEndDate(snapshot.getPeriodEndDate())
                .ranking(snapshot.getRanking())
                .popularityScore(snapshot.getPopularityScore())
                .likeCount(snapshot.getLikeCount())
                .commentCount(snapshot.getCommentCount())
                .viewCount(snapshot.getViewCount())
                .boardTitle(board != null ? board.getTitle() : null)
                .boardCategory(board != null ? board.getCategory() : null)
                .boardFilePath(board != null ? board.getBoardFilePath() : null)
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

    public List<BoardPopularitySnapshotDTO> toDTOList(List<BoardPopularitySnapshot> snapshots) {
        return snapshots.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}
