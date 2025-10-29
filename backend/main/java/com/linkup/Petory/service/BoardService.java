package com.linkup.Petory.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.converter.BoardConverter;
import com.linkup.Petory.dto.BoardDTO;
import com.linkup.Petory.entity.Board;
import com.linkup.Petory.entity.Users;
import com.linkup.Petory.repository.BoardRepository;
import com.linkup.Petory.repository.UsersRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class BoardService {

    private final BoardRepository boardRepository;
    private final UsersRepository usersRepository;
    private final BoardConverter boardConverter;

    // 전체 게시글 조회 (카테고리 필터링 포함)
    public List<BoardDTO> getAllBoards(String category) {
        List<Board> boards;

        if (category != null && !category.equals("ALL")) {
            boards = boardRepository.findByCategoryOrderByCreatedAtDesc(category);
        } else {
            boards = boardRepository.findAllByOrderByCreatedAtDesc();
        }

        return boardConverter.toDTOList(boards);
    }

    // 단일 게시글 조회
    public BoardDTO getBoard(Long idx) {
        Board board = boardRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("Board not found"));
        return boardConverter.toDTO(board);
    }

    // 게시글 생성
    public BoardDTO createBoard(BoardDTO dto) {
        Users user = usersRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        Board board = Board.builder()
                .title(dto.getTitle())
                .content(dto.getContent())
                .category(dto.getCategory())
                .user(user)
                .build();

        Board saved = boardRepository.save(board);
        return boardConverter.toDTO(saved);
    }

    // 게시글 수정
    public BoardDTO updateBoard(Long idx, BoardDTO dto) {
        Board board = boardRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("Board not found"));

        if (dto.getTitle() != null)
            board.setTitle(dto.getTitle());
        if (dto.getContent() != null)
            board.setContent(dto.getContent());
        if (dto.getCategory() != null)
            board.setCategory(dto.getCategory());

        Board updated = boardRepository.save(board);
        return boardConverter.toDTO(updated);
    }

    // 게시글 삭제
    public void deleteBoard(Long idx) {
        boardRepository.deleteById(idx);
    }

    // 내 게시글 조회
    public List<BoardDTO> getMyBoards(Long userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Board> boards = boardRepository.findByUserOrderByCreatedAtDesc(user);
        return boardConverter.toDTOList(boards);
    }

    // 게시글 검색
    public List<BoardDTO> searchBoards(String keyword) {
        List<Board> boards = boardRepository.findByTitleContainingOrContentContainingOrderByCreatedAtDesc(keyword,
                keyword);
        return boardConverter.toDTOList(boards);
    }
}
