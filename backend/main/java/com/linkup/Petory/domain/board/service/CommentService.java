package com.linkup.Petory.domain.board.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.board.converter.CommentConverter;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.board.dto.CommentDTO;
import com.linkup.Petory.domain.board.dto.CommentPageResponseDTO;
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

    /**
     * 댓글 목록 조회 (페이징 지원)
     * 엔드포인트: GET /api/boards/{boardId}/comments?page={page}&size={size}
     * - 생성일 기준 오름차순 정렬
     * - 삭제된 댓글 제외
     * - 각 댓글의 파일 정보 포함
     * - 댓글 파일 배치 조회 (N+1 문제 해결)
     */
    public CommentPageResponseDTO getCommentsWithPaging(Long boardId, int page, int size) {
        // 게시글 존재 확인
        boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));

        Pageable pageable = PageRequest.of(page, size);
        Page<Comment> commentPage = commentRepository.findByBoardIdAndIsDeletedFalseOrderByCreatedAtAsc(boardId,
                pageable);

        if (commentPage.isEmpty()) {
            return new CommentPageResponseDTO(
                    new ArrayList<>(),
                    0,
                    0,
                    page,
                    size,
                    false,
                    false);
        }

        List<Comment> comments = commentPage.getContent();

        // 댓글 ID 리스트 추출
        List<Long> commentIds = comments.stream()
                .map(Comment::getIdx)
                .collect(Collectors.toList());

        // 댓글 파일 배치 조회 (N+1 문제 해결)
        Map<Long, List<FileDTO>> filesByCommentId = attachmentFileService.getAttachmentsBatch(
                FileTargetType.COMMENT, commentIds);

        // 댓글 반응(좋아요/싫어요) 배치 조회 (N+1 문제 해결)
        Map<Long, Map<ReactionType, Long>> reactionCountsMap = getReactionCountsBatch(commentIds);

        // DTO 변환 (배치 조회된 반응/파일 정보 사용)
        List<CommentDTO> commentDTOs = mapCommentsWithReactionCountsBatch(
                comments, reactionCountsMap, filesByCommentId);

        return new CommentPageResponseDTO(
                commentDTOs,
                commentPage.getTotalElements(),
                commentPage.getTotalPages(),
                page,
                size,
                commentPage.hasNext(),
                commentPage.hasPrevious());
    }

    /**
     * 댓글 목록 조회 (페이징 없음 - 하위 호환성)
     * 엔드포인트: GET /api/boards/{boardId}/comments
     * - 생성일 기준 오름차순 정렬
     * - 삭제된 댓글 제외
     * - 각 댓글의 파일 정보 포함
     */
    public List<CommentDTO> getComments(Long boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));
        // 일반 사용자용: 작성자도 활성 상태여야 함
        List<Comment> comments = commentRepository.findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(board);
        if (comments.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> commentIds = comments.stream().map(Comment::getIdx).collect(Collectors.toList());
        Map<Long, Map<ReactionType, Long>> reactionCountsMap = getReactionCountsBatch(commentIds);
        Map<Long, List<FileDTO>> filesByCommentId = attachmentFileService.getAttachmentsBatch(
                FileTargetType.COMMENT, commentIds);
        return mapCommentsWithReactionCountsBatch(comments, reactionCountsMap, filesByCommentId);
    }

    /**
     * 관리자용 댓글 조회
     * - 작성자 상태 체크 없이 조회 (삭제된 사용자 댓글도 포함)
     * - AdminBoardController에서 사용
     */
    public List<CommentDTO> getCommentsForAdmin(Long boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));
        List<Comment> comments = commentRepository.findByBoardAndIsDeletedFalseForAdmin(board);
        if (comments.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> commentIds = comments.stream().map(Comment::getIdx).collect(Collectors.toList());
        Map<Long, Map<ReactionType, Long>> reactionCountsMap = getReactionCountsBatch(commentIds);
        Map<Long, List<FileDTO>> filesByCommentId = attachmentFileService.getAttachmentsBatch(
                FileTargetType.COMMENT, commentIds);
        return mapCommentsWithReactionCountsBatch(comments, reactionCountsMap, filesByCommentId);
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
    public CommentDTO updateComment(Long boardId, Long commentId, CommentDTO dto) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));

        if (!comment.getBoard().getIdx().equals(board.getIdx())) {
            throw new IllegalArgumentException("Comment does not belong to the specified board");
        }

        // 이메일 인증 확인
        Users user = comment.getUser();
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            throw new EmailVerificationRequiredException(
                    "댓글 수정을 위해 이메일 인증이 필요합니다.",
                    com.linkup.Petory.domain.user.entity.EmailVerificationPurpose.COMMENT_EDIT);
        }

        // 댓글 내용 업데이트
        if (dto.getContent() != null) {
            comment.setContent(dto.getContent());
        }

        Comment saved = commentRepository.save(comment);

        // 첨부파일 업데이트
        if (dto.getCommentFilePath() != null) {
            attachmentFileService.syncSingleAttachment(FileTargetType.COMMENT, saved.getIdx(), dto.getCommentFilePath(),
                    null);
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

        // 이메일 인증 확인
        Users user = comment.getUser();
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            throw new EmailVerificationRequiredException(
                    "댓글 삭제를 위해 이메일 인증이 필요합니다.",
                    com.linkup.Petory.domain.user.entity.EmailVerificationPurpose.COMMENT_EDIT);
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
        dto.setCommentFilePath(attachmentFileService.extractPrimaryFileUrl(attachments));
        return dto;
    }

    /**
     * 여러 댓글의 반응(좋아요/싫어요) 카운트를 배치로 조회
     * 반환값: Map<CommentId, Map<ReactionType, Count>>
     */
    private Map<Long, Map<ReactionType, Long>> getReactionCountsBatch(List<Long> commentIds) {
        if (commentIds.isEmpty()) {
            return new HashMap<>();
        }
        final int BATCH_SIZE = 500;
        Map<Long, Map<ReactionType, Long>> countsMap = new HashMap<>();
        for (int i = 0; i < commentIds.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, commentIds.size());
            List<Long> batch = commentIds.subList(i, end);
            List<Object[]> results = commentReactionRepository.countByCommentsGroupByReactionType(batch);
            for (Object[] result : results) {
                Long commentId = ((Number) result[0]).longValue();
                ReactionType reactionType = (ReactionType) result[1];
                Long count = ((Number) result[2]).longValue();
                countsMap.computeIfAbsent(commentId, k -> new HashMap<>()).put(reactionType, count);
            }
        }
        return countsMap;
    }

    /**
     * 댓글 목록을 DTO로 변환 (배치 조회된 반응/파일 정보 사용)
     */
    private List<CommentDTO> mapCommentsWithReactionCountsBatch(
            List<Comment> comments,
            Map<Long, Map<ReactionType, Long>> reactionCountsMap,
            Map<Long, List<FileDTO>> filesByCommentId) {
        return comments.stream()
                .map(comment -> {
                    CommentDTO dto = commentConverter.toDTO(comment);
                    Map<ReactionType, Long> counts = reactionCountsMap.getOrDefault(
                            comment.getIdx(), new HashMap<>());
                    dto.setLikeCount(Math.toIntExact(counts.getOrDefault(ReactionType.LIKE, 0L)));
                    dto.setDislikeCount(Math.toIntExact(counts.getOrDefault(ReactionType.DISLIKE, 0L)));
                    List<FileDTO> attachments = filesByCommentId.getOrDefault(comment.getIdx(), List.of());
                    dto.setAttachments(attachments);
                    dto.setCommentFilePath(attachmentFileService.extractPrimaryFileUrl(attachments));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 댓글 상태 변경 (관리자용)
     * - AdminBoardController에서 사용
     */
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

    /**
     * 댓글 복구 (관리자용)
     * - AdminBoardController에서 사용
     */
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
