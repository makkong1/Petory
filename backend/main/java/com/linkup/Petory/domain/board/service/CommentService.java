package com.linkup.Petory.domain.board.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
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
import com.linkup.Petory.domain.notification.entity.NotificationType;
import com.linkup.Petory.domain.notification.service.NotificationService;
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
    private final NotificationService notificationService;

    public List<CommentDTO> getComments(Long boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));
        List<Comment> comments = commentRepository.findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(board);
        return comments.stream()
                .map(this::mapWithReactionCounts)
                .collect(Collectors.toList());
    }

    @CacheEvict(value = "boardDetail", key = "#boardId")
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

        // commentCount 실시간 업데이트
        incrementBoardCommentCount(board);
        boardRepository.save(board);

        if (dto.getCommentFilePath() != null) {
            attachmentFileService.syncSingleAttachment(FileTargetType.COMMENT, saved.getIdx(), dto.getCommentFilePath(),
                    null);
        }

        // 알림 발송: 댓글 작성자가 게시글 작성자가 아닌 경우에만 알림 발송
        Long boardOwnerId = board.getUser().getIdx();
        if (!boardOwnerId.equals(user.getIdx())) {
            notificationService.createNotification(
                    boardOwnerId,
                    NotificationType.BOARD_COMMENT,
                    "내 게시글에 새로운 댓글이 달렸습니다",
                    String.format("%s님이 댓글을 남겼습니다: %s", user.getUsername(),
                            dto.getContent().length() > 50 ? dto.getContent().substring(0, 50) + "..."
                                    : dto.getContent()),
                    board.getIdx(),
                    "BOARD");
        }

        return mapWithReactionCounts(saved);
    }

    @CacheEvict(value = "boardDetail", key = "#boardId")
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

        // commentCount 실시간 업데이트 (삭제된 댓글은 카운트에서 제외)
        decrementBoardCommentCount(board);
        boardRepository.save(board);
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

    @CacheEvict(value = "boardDetail", key = "#boardId")
    @Transactional
    public CommentDTO updateCommentStatus(Long boardId, Long commentId,
            com.linkup.Petory.domain.common.ContentStatus status) {
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

    @CacheEvict(value = "boardDetail", key = "#boardId")
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

        // commentCount 실시간 업데이트 (복구된 댓글은 카운트에 포함)
        incrementBoardCommentCount(board);
        boardRepository.save(board);

        return mapWithReactionCounts(saved);
    }

    /**
     * 게시글의 commentCount를 증가시킴
     */
    private void incrementBoardCommentCount(Board board) {
        Integer currentCount = board.getCommentCount() != null ? board.getCommentCount() : 0;
        board.setCommentCount(currentCount + 1);
    }

    /**
     * 게시글의 commentCount를 감소시킴
     */
    private void decrementBoardCommentCount(Board board) {
        Integer currentCount = board.getCommentCount() != null ? board.getCommentCount() : 0;
        board.setCommentCount(Math.max(0, currentCount - 1));
    }
}
