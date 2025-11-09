package com.linkup.Petory.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.converter.CommentConverter;
import com.linkup.Petory.dto.CommentDTO;
import com.linkup.Petory.entity.Board;
import com.linkup.Petory.entity.Comment;
import com.linkup.Petory.entity.ReactionType;
import com.linkup.Petory.entity.Users;
import com.linkup.Petory.repository.BoardRepository;
import com.linkup.Petory.repository.CommentReactionRepository;
import com.linkup.Petory.repository.CommentRepository;
import com.linkup.Petory.repository.UsersRepository;

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

    public List<CommentDTO> getComments(Long boardId) {
        Board board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Board not found"));
        List<Comment> comments = commentRepository.findByBoardOrderByCreatedAtAsc(board);
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
                .commentFilePath(dto.getCommentFilePath())
                .build();

        Comment saved = commentRepository.save(comment);
        if (board.getComments() != null) {
            board.getComments().add(saved);
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

        commentReactionRepository.deleteByComment(comment);
        commentRepository.delete(comment);
        board.getComments().removeIf(c -> c.getIdx().equals(commentId));
    }

    private CommentDTO mapWithReactionCounts(Comment comment) {
        CommentDTO dto = commentConverter.toDTO(comment);
        long likeCount = commentReactionRepository.countByCommentAndReactionType(comment, ReactionType.LIKE);
        long dislikeCount = commentReactionRepository.countByCommentAndReactionType(comment, ReactionType.DISLIKE);
        dto.setLikeCount(Math.toIntExact(likeCount));
        dto.setDislikeCount(Math.toIntExact(dislikeCount));
        return dto;
    }
}

