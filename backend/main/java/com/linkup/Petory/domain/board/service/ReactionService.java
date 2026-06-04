package com.linkup.Petory.domain.board.service;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.board.exception.BoardNotFoundException;
import com.linkup.Petory.domain.board.exception.CommentNotFoundException;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.UserNotFoundException;
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

    @CacheEvict(value = "boardDetail", key = "#p0")
    public ReactionSummaryDTO reactToBoard(Long boardId, Long userId, ReactionType reactionType) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BoardNotFoundException());
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException());

        Optional<BoardReaction> existing = boardReactionRepository.findByBoardAndUser(board, user);
        ReactionType previousReactionType = null;
        ReactionType currentReactionType = reactionType;
        boolean toggledOff = false;

        if (existing.isPresent() && existing.get().getReactionType() == reactionType) {
            previousReactionType = existing.get().getReactionType();
            currentReactionType = null;
            toggledOff = true;
            boardReactionRepository.delete(existing.get());
        } else if (existing.isPresent()) {
            previousReactionType = existing.get().getReactionType();
            existing.get().setReactionType(reactionType);
            boardReactionRepository.save(existing.get());
            boardRepository.updateLastReactionAt(boardId, LocalDateTime.now());
        } else {
            int inserted = boardReactionRepository.insertIgnore(boardId, userId, reactionType.name());
            if (inserted == 0) {
                return getBoardSummary(boardId, userId);
            }
            boardRepository.updateLastReactionAt(boardId, LocalDateTime.now());
        }

        // 원자적 카운트 조정
        int likeDelta = 0;
        int dislikeDelta = 0;
        if (previousReactionType == ReactionType.LIKE) likeDelta--;
        else if (previousReactionType == ReactionType.DISLIKE) dislikeDelta--;
        if (currentReactionType == ReactionType.LIKE) likeDelta++;
        else if (currentReactionType == ReactionType.DISLIKE) dislikeDelta++;

        if (likeDelta != 0) boardRepository.adjustLikeCount(boardId, likeDelta);
        if (dislikeDelta != 0) boardRepository.adjustDislikeCount(boardId, dislikeDelta);

        // 반환값: 메모리 내 기존 값 + delta (DB 재조회 불필요)
        int newLikeCount = Math.max(0, (board.getLikeCount() != null ? board.getLikeCount() : 0) + likeDelta);
        int newDislikeCount = Math.max(0, (board.getDislikeCount() != null ? board.getDislikeCount() : 0) + dislikeDelta);
        ReactionType userReaction = toggledOff ? null : reactionType;
        return buildBoardSummaryFromCounts(newLikeCount, newDislikeCount, userReaction);
    }

    @Transactional(readOnly = true)
    public ReactionSummaryDTO getBoardSummary(Long boardId, Long userId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new BoardNotFoundException());
        Users user = userId != null
                ? usersRepository.findById(userId).orElse(null)
                : null;
        return buildBoardSummary(board, user);
    }

    public ReactionSummaryDTO reactToComment(Long commentId, Long userId, ReactionType reactionType) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException());
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException());

        Optional<CommentReaction> existing = commentReactionRepository.findByCommentAndUser(comment, user);
        // 뮤테이션 전에 이전 타입을 저장 — 이후 setReactionType이 같은 객체를 변경하기 때문
        ReactionType previousType = existing.map(CommentReaction::getReactionType).orElse(null);

        if (existing.isPresent() && previousType == reactionType) {
            commentReactionRepository.delete(existing.get());
        } else if (existing.isPresent()) {
            existing.get().setReactionType(reactionType);
            commentReactionRepository.save(existing.get());
        } else {
            int inserted = commentReactionRepository.insertIgnore(commentId, userId, reactionType.name());
            if (inserted == 0) {
                return getCommentSummary(commentId, userId);
            }
        }

        boolean toggledOff = (previousType == reactionType);
        ReactionType userReaction = toggledOff ? null : reactionType;
        return buildCommentSummaryWithUserReaction(comment, userReaction);
    }

    @Transactional(readOnly = true)
    public ReactionSummaryDTO getCommentSummary(Long commentId, Long userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException());
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
        return new ReactionSummaryDTO(
                Math.toIntExact(likeCount),
                Math.toIntExact(dislikeCount),
                userReaction);
    }

    /** [리팩토링] reactToBoard 반환용 - count 2회 + find 1회 제거, 엔티티 likeCount/dislikeCount 사용 */
    private ReactionSummaryDTO buildBoardSummaryFromCounts(int likeCount, int dislikeCount,
            ReactionType userReaction) {
        return new ReactionSummaryDTO(likeCount, dislikeCount, userReaction);
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
        return new ReactionSummaryDTO(
                Math.toIntExact(likeCount),
                Math.toIntExact(dislikeCount),
                userReaction);
    }

    /** [리팩토링] reactToComment 반환용 - findByCommentAndUser 1회 제거, userReaction 계산값 전달 */
    private ReactionSummaryDTO buildCommentSummaryWithUserReaction(Comment comment, ReactionType userReaction) {
        long likeCount = commentReactionRepository.countByCommentAndReactionType(comment, ReactionType.LIKE);
        long dislikeCount = commentReactionRepository.countByCommentAndReactionType(comment, ReactionType.DISLIKE);
        return new ReactionSummaryDTO(
                Math.toIntExact(likeCount),
                Math.toIntExact(dislikeCount),
                userReaction);
    }

}
