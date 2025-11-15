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
                .boardFilePath(boardFilePath)
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
