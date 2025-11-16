package com.linkup.Petory.domain.board.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.board.converter.CommentConverter;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.board.dto.CommentDTO;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.Comment;
import com.linkup.Petory.domain.board.entity.ReactionType;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.board.repository.CommentReactionRepository;
import com.linkup.Petory.domain.board.repository.CommentRepository;
import com.linkup.Petory.domain.common.ContentStatus;
import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.service.AttachmentFileService;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final BoardRepository boardRepository;
    private final UsersRepository usersRepository;
    private final CommentReactionRepository commentReactionRepository;
    private final CommentConverter commentConverter;
    private final AttachmentFileService attachmentFileService;

    public List<CommentDTO> getComments(Long boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));
        List<Comment> comments = commentRepository.findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(board);
        return comments.stream()
                .map(this::mapWithReactionCounts)
                .collect(Collectors.toList());
    }

    @Transactional
    public CommentDTO addComment(Long boardId, CommentDTO dto) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));
        Users user = usersRepository.findById(dto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Comment comment = Comment.builder()
                .board(board)
                .user(user)
                .content(dto.getContent())
                .build();

        Comment saved = commentRepository.save(comment);
        if (board.getComments() != null) {
            board.getComments().add(saved);
        }
        if (dto.getCommentFilePath() != null) {
            attachmentFileService.syncSingleAttachment(FileTargetType.COMMENT, saved.getIdx(), dto.getCommentFilePath(),
                    null);
        }
        return mapWithReactionCounts(saved);
    }

    @Transactional
    public void deleteComment(Long boardId, Long commentId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));

        if (!comment.getBoard().getIdx().equals(board.getIdx())) {
            throw new IllegalArgumentException("Comment does not belong to the specified board");
        }

        // Soft delete instead of physical delete
        comment.setStatus(ContentStatus.DELETED);
        comment.setIsDeleted(true);
        comment.setDeletedAt(LocalDateTime.now());
        commentRepository.save(comment);
        // keep attachments and reactions for audit/possible restore
    }

    private CommentDTO mapWithReactionCounts(Comment comment) {
        CommentDTO dto = commentConverter.toDTO(comment);
        long likeCount = commentReactionRepository.countByCommentAndReactionType(comment, ReactionType.LIKE);
        long dislikeCount = commentReactionRepository.countByCommentAndReactionType(comment, ReactionType.DISLIKE);
        dto.setLikeCount(Math.toIntExact(likeCount));
        dto.setDislikeCount(Math.toIntExact(dislikeCount));
        List<FileDTO> attachments = attachmentFileService.getAttachments(FileTargetType.COMMENT, comment.getIdx());
        dto.setAttachments(attachments);
        dto.setCommentFilePath(extractPrimaryFileUrl(attachments));
        return dto;
    }

    private String extractPrimaryFileUrl(List<FileDTO> attachments) {
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

    @Transactional
    public CommentDTO updateCommentStatus(Long boardId, Long commentId, com.linkup.Petory.domain.common.ContentStatus status) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));
        if (!comment.getBoard().getIdx().equals(board.getIdx())) {
            throw new IllegalArgumentException("Comment does not belong to the specified board");
        }
        if (!Boolean.TRUE.equals(comment.getIsDeleted())) {
            comment.setStatus(status);
        }
        Comment saved = commentRepository.save(comment);
        return mapWithReactionCounts(saved);
    }

    @Transactional
    public CommentDTO restoreComment(Long boardId, Long commentId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));
        if (!comment.getBoard().getIdx().equals(board.getIdx())) {
            throw new IllegalArgumentException("Comment does not belong to the specified board");
        }
        comment.setIsDeleted(false);
        comment.setDeletedAt(null);
        if (comment.getStatus() == com.linkup.Petory.domain.common.ContentStatus.DELETED) {
            comment.setStatus(com.linkup.Petory.domain.common.ContentStatus.ACTIVE);
        }
        Comment saved = commentRepository.save(comment);
        return mapWithReactionCounts(saved);
    }
}
