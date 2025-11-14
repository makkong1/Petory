package com.linkup.Petory.domain.board.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.board.converter.BoardConverter;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.board.dto.BoardDTO;
import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.repository.BoardRepository;
import com.linkup.Petory.domain.board.repository.BoardReactionRepository;
import com.linkup.Petory.domain.board.repository.CommentReactionRepository;
import com.linkup.Petory.domain.board.repository.BoardViewLogRepository;
import com.linkup.Petory.domain.board.entity.ReactionType;
import com.linkup.Petory.domain.board.entity.BoardViewLog;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.service.AttachmentFileService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoardService {

    private final BoardRepository boardRepository;
    private final UsersRepository usersRepository;
    private final BoardReactionRepository boardReactionRepository;
    private final CommentReactionRepository commentReactionRepository;
    private final BoardViewLogRepository boardViewLogRepository;
    private final AttachmentFileService attachmentFileService;
    private final BoardConverter boardConverter;

    // 전체 게시글 조회 (카테고리 필터링 포함)
    public List<BoardDTO> getAllBoards(String category) {
        List<Board> boards;

        if (category != null && !category.equals("ALL")) {
            boards = boardRepository.findByCategoryOrderByCreatedAtDesc(category);
        } else {
            boards = boardRepository.findAllByOrderByCreatedAtDesc();
        }

        return boards.stream()
                .map(this::mapWithReactions)
                .collect(Collectors.toList());
    }

    // 단일 게시글 조회 + 조회수 증가
    @Transactional
    public BoardDTO getBoard(Long idx, Long viewerId) {
        Board board = boardRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("Board not found"));

        if (shouldIncrementView(board, viewerId)) {
            incrementViewCount(board);
        }

        return mapWithReactions(board);
    }

    // 게시글 생성
    @Transactional
    public BoardDTO createBoard(BoardDTO dto) {
        Users user = usersRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Board board = Board.builder()
                .title(dto.getTitle())
                .content(dto.getContent())
                .category(dto.getCategory())
                .user(user)
                .boardFilePath(dto.getBoardFilePath())
                .commentFilePath(dto.getCommentFilePath())
                .build();

        Board saved = boardRepository.save(board);
        attachmentFileService.syncSingleAttachment(FileTargetType.BOARD, saved.getIdx(), board.getBoardFilePath(), null);
        return mapWithReactions(saved);
    }

    // 게시글 수정
    @Transactional
    public BoardDTO updateBoard(Long idx, BoardDTO dto) {
        Board board = boardRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("Board not found"));

        if (dto.getTitle() != null)
            board.setTitle(dto.getTitle());
        if (dto.getContent() != null)
            board.setContent(dto.getContent());
        if (dto.getCategory() != null)
            board.setCategory(dto.getCategory());
        if (dto.getBoardFilePath() != null)
            board.setBoardFilePath(dto.getBoardFilePath());
        if (dto.getCommentFilePath() != null)
            board.setCommentFilePath(dto.getCommentFilePath());

        Board updated = boardRepository.save(board);
        attachmentFileService.syncSingleAttachment(FileTargetType.BOARD, updated.getIdx(), updated.getBoardFilePath(), null);
        return mapWithReactions(updated);
    }

    // 게시글 삭제
    @Transactional
    public void deleteBoard(Long idx) {
        Board board = boardRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("Board not found"));

        if (board.getComments() != null) {
            board.getComments().forEach(commentReactionRepository::deleteByComment);
        }
        boardReactionRepository.deleteByBoard(board);
        attachmentFileService.deleteAll(FileTargetType.BOARD, board.getIdx());
        boardRepository.delete(board);
    }

    // 내 게시글 조회
    public List<BoardDTO> getMyBoards(Long userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Board> boards = boardRepository.findByUserOrderByCreatedAtDesc(user);
        return boards.stream()
                .map(this::mapWithReactions)
                .collect(Collectors.toList());
    }

    // 게시글 검색
    public List<BoardDTO> searchBoards(String keyword) {
        List<Board> boards = boardRepository.findByTitleContainingOrContentContainingOrderByCreatedAtDesc(keyword,
                keyword);
        return boards.stream()
                .map(this::mapWithReactions)
                .collect(Collectors.toList());
    }

    private BoardDTO mapWithReactions(Board board) {
        BoardDTO dto = boardConverter.toDTO(board);
        long likeCount = boardReactionRepository.countByBoardAndReactionType(board, ReactionType.LIKE);
        long dislikeCount = boardReactionRepository.countByBoardAndReactionType(board, ReactionType.DISLIKE);
        dto.setLikes(Math.toIntExact(likeCount));
        dto.setDislikes(Math.toIntExact(dislikeCount));
        dto.setAttachments(attachmentFileService.getAttachments(FileTargetType.BOARD, board.getIdx()));
        return dto;
    }

    private void incrementViewCount(Board board) {
        Integer current = board.getViewCount();
        board.setViewCount((current == null ? 0 : current) + 1);
        boardRepository.save(board);
    }

    private boolean shouldIncrementView(Board board, Long viewerId) {
        if (viewerId == null) {
            return true;
        }

        Users viewer = usersRepository.findById(viewerId).orElse(null);
        if (viewer == null) {
            return true;
        }

        boolean alreadyViewed = boardViewLogRepository.existsByBoardAndUser(board, viewer);
        if (alreadyViewed) {
            return false;
        }

        BoardViewLog log = BoardViewLog.builder()
                .board(board)
                .user(viewer)
                .build();
        boardViewLogRepository.save(log);
        return true;
    }
}
