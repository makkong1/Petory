package com.linkup.Petory.domain.board.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.board.dto.ReactionSummaryDTO;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.BoardReaction;
import com.linkup.Petory.domain.board.entity.Comment;
import com.linkup.Petory.domain.board.entity.CommentReaction;
import com.linkup.Petory.domain.board.entity.ReactionType;
import com.linkup.Petory.domain.board.repository.BoardReactionRepository;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.board.repository.CommentReactionRepository;
import com.linkup.Petory.domain.board.repository.CommentRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ReactionService {

    private final BoardReactionRepository boardReactionRepository;
    private final CommentReactionRepository commentReactionRepository;
    private final BoardRepository boardRepository;
    private final CommentRepository commentRepository;
    private final UsersRepository usersRepository;

    @CacheEvict(value = "boardDetail", key = "#boardId")
    public ReactionSummaryDTO reactToBoard(Long boardId, Long userId, ReactionType reactionType) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Optional<BoardReaction> existing = boardReactionRepository.findByBoardAndUser(board, user);
        ReactionType previousReactionType = null;

        if (existing.isPresent() && existing.get().getReactionType() == reactionType) {
            // 같은 반응을 다시 클릭하면 삭제 (토글)
            previousReactionType = existing.get().getReactionType();
            boardReactionRepository.delete(existing.get());
            // 삭제 시에는 lastReactionAt을 업데이트하지 않음 (마지막 반응 시간 유지)
        } else if (existing.isPresent()) {
            // 반응 타입 변경 (예: 좋아요 -> 싫어요)
            previousReactionType = existing.get().getReactionType();
            BoardReaction reaction = existing.get();
            reaction.setReactionType(reactionType);
            boardReactionRepository.save(reaction);
            // 반응이 변경되었으므로 lastReactionAt 업데이트
            board.setLastReactionAt(LocalDateTime.now());
        } else {
            // 새로운 반응 추가
            BoardReaction reaction = BoardReaction.builder()
                    .board(board)
                    .user(user)
                    .reactionType(reactionType)
                    .build();
            boardReactionRepository.save(reaction);
            // 반응이 추가되었으므로 lastReactionAt 업데이트
            board.setLastReactionAt(LocalDateTime.now());
        }

        // likeCount 실시간 업데이트
        updateBoardLikeCount(board, previousReactionType, reactionType);
        boardRepository.save(board);

        return buildBoardSummary(board, user);
    }

    @Transactional(readOnly = true)
    public ReactionSummaryDTO getBoardSummary(Long boardId, Long userId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));
        Users user = userId != null
                ? usersRepository.findById(userId).orElse(null)
                : null;
        return buildBoardSummary(board, user);
    }

    public ReactionSummaryDTO reactToComment(Long commentId, Long userId, ReactionType reactionType) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Optional<CommentReaction> existing = commentReactionRepository.findByCommentAndUser(comment, user);
        if (existing.isPresent() && existing.get().getReactionType() == reactionType) {
            commentReactionRepository.delete(existing.get());
        } else if (existing.isPresent()) {
            CommentReaction reaction = existing.get();
            reaction.setReactionType(reactionType);
            commentReactionRepository.save(reaction);
        } else {
            CommentReaction reaction = CommentReaction.builder()
                    .comment(comment)
                    .user(user)
                    .reactionType(reactionType)
                    .build();
            commentReactionRepository.save(reaction);
        }

        return buildCommentSummary(comment, user);
    }

    @Transactional(readOnly = true)
    public ReactionSummaryDTO getCommentSummary(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));
        Users user = userId != null
                ? usersRepository.findById(userId).orElse(null)
                : null;
        return buildCommentSummary(comment, user);
    }

    private ReactionSummaryDTO buildBoardSummary(Board board, Users user) {
        long likeCount = boardReactionRepository.countByBoardAndReactionType(board, ReactionType.LIKE);
        long dislikeCount = boardReactionRepository.countByBoardAndReactionType(board, ReactionType.DISLIKE);
        ReactionType userReaction = null;
        if (user != null) {
            userReaction = boardReactionRepository.findByBoardAndUser(board, user)
                    .map(BoardReaction::getReactionType)
                    .orElse(null);
        }
        return ReactionSummaryDTO.builder()
                .likeCount(Math.toIntExact(likeCount))
                .dislikeCount(Math.toIntExact(dislikeCount))
                .userReaction(userReaction)
                .build();
    }

    private ReactionSummaryDTO buildCommentSummary(Comment comment, Users user) {
        long likeCount = commentReactionRepository.countByCommentAndReactionType(comment, ReactionType.LIKE);
        long dislikeCount = commentReactionRepository.countByCommentAndReactionType(comment, ReactionType.DISLIKE);
        ReactionType userReaction = null;
        if (user != null) {
            userReaction = commentReactionRepository.findByCommentAndUser(comment, user)
                    .map(CommentReaction::getReactionType)
                    .orElse(null);
        }
        return ReactionSummaryDTO.builder()
                .likeCount(Math.toIntExact(likeCount))
                .dislikeCount(Math.toIntExact(dislikeCount))
                .userReaction(userReaction)
                .build();
    }

    /**
     * 게시글의 likeCount를 실시간으로 업데이트
     * 
     * @param board                게시글 엔티티
     * @param previousReactionType 이전 반응 타입 (null이면 새로 추가)
     * @param currentReactionType  현재 반응 타입 (null이면 삭제)
     */
    private void updateBoardLikeCount(Board board, ReactionType previousReactionType,
            ReactionType currentReactionType) {
        Integer currentLikeCount = board.getLikeCount() != null ? board.getLikeCount() : 0;

        // 이전 반응이 좋아요였으면 감소
        if (previousReactionType == ReactionType.LIKE) {
            currentLikeCount = Math.max(0, currentLikeCount - 1);
        }

        // 현재 반응이 좋아요면 증가
        if (currentReactionType == ReactionType.LIKE) {
            currentLikeCount = currentLikeCount + 1;
        }

        board.setLikeCount(currentLikeCount);
    }
}
