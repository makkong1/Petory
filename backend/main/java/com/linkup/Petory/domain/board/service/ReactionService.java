package com.linkup.Petory.domain.board.service;

import java.util.Optional;

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

    public ReactionSummaryDTO reactToBoard(Long boardId, Long userId, ReactionType reactionType) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Optional<BoardReaction> existing = boardReactionRepository.findByBoardAndUser(board, user);
        if (existing.isPresent() && existing.get().getReactionType() == reactionType) {
            boardReactionRepository.delete(existing.get());
        } else if (existing.isPresent()) {
            BoardReaction reaction = existing.get();
            reaction.setReactionType(reactionType);
            boardReactionRepository.save(reaction);
        } else {
            BoardReaction reaction = BoardReaction.builder()
                    .board(board)
                    .user(user)
                    .reactionType(reactionType)
                    .build();
            boardReactionRepository.save(reaction);
        }

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
}
