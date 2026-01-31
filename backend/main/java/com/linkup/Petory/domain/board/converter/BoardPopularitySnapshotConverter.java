package com.linkup.Petory.domain.board.converter;

import com.linkup.Petory.domain.board.dto.BoardPopularitySnapshotDTO;
import com.linkup.Petory.domain.board.entity.BoardPopularitySnapshot;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.service.AttachmentFileService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BoardPopularitySnapshotConverter {

    private final AttachmentFileService attachmentFileService;

    public BoardPopularitySnapshotDTO toDTO(BoardPopularitySnapshot snapshot) {
        Board board = snapshot.getBoard();
        String boardFilePath = resolvePrimaryFileUrl(board);

        return new BoardPopularitySnapshotDTO(
                snapshot.getSnapshotId(),
                board != null ? board.getIdx() : null,
                snapshot.getPeriodType(),
                snapshot.getPeriodStartDate(),
                snapshot.getPeriodEndDate(),
                snapshot.getRanking(),
                snapshot.getPopularityScore(),
                snapshot.getLikeCount(),
                snapshot.getCommentCount(),
                snapshot.getViewCount(),
                board != null ? board.getTitle() : null,
                board != null ? board.getCategory() : null,
                boardFilePath,
                snapshot.getCreatedAt());
    }

    public BoardPopularitySnapshot toEntity(BoardPopularitySnapshotDTO dto) {
        BoardPopularitySnapshot snapshot = new BoardPopularitySnapshot();
        snapshot.setSnapshotId(dto.snapshotId());
        snapshot.setPeriodType(dto.periodType());
        snapshot.setPeriodStartDate(dto.periodStartDate());
        snapshot.setPeriodEndDate(dto.periodEndDate());
        snapshot.setRanking(dto.ranking());
        snapshot.setPopularityScore(dto.popularityScore());
        snapshot.setLikeCount(dto.likeCount());
        snapshot.setCommentCount(dto.commentCount());
        snapshot.setViewCount(dto.viewCount());
        snapshot.setCreatedAt(dto.createdAt());
        return snapshot;
    }

    public List<BoardPopularitySnapshotDTO> toDTOList(List<BoardPopularitySnapshot> snapshots) {
        return snapshots.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private String resolvePrimaryFileUrl(Board board) {
        if (board == null) {
            return null;
        }
        List<FileDTO> attachments = attachmentFileService.getAttachments(FileTargetType.BOARD, board.getIdx());
        if (attachments == null || attachments.isEmpty()) {
            return null;
        }
        FileDTO primary = attachments.get(0);
        if (primary == null) {
            return null;
        }
        if (StringUtils.hasText(primary.getDownloadUrl())) {
            return primary.getDownloadUrl();
        }
        return attachmentFileService.buildDownloadUrl(primary.getFilePath());
    }
}
