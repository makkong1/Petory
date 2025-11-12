package com.linkup.Petory.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.linkup.Petory.converter.MissingPetConverter;
import com.linkup.Petory.dto.MissingPetBoardDTO;
import com.linkup.Petory.dto.MissingPetCommentDTO;
import com.linkup.Petory.entity.MissingPetBoard;
import com.linkup.Petory.entity.MissingPetComment;
import com.linkup.Petory.entity.MissingPetStatus;
import com.linkup.Petory.entity.Users;
import com.linkup.Petory.repository.MissingPetBoardRepository;
import com.linkup.Petory.repository.MissingPetCommentRepository;
import com.linkup.Petory.repository.UsersRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissingPetBoardService {

    private final MissingPetBoardRepository boardRepository;
    private final MissingPetCommentRepository commentRepository;
    private final UsersRepository usersRepository;
    private final MissingPetConverter missingPetConverter;

    public List<MissingPetBoardDTO> getBoards(MissingPetStatus status) {
        List<MissingPetBoard> boards = status == null
                ? boardRepository.findAllByOrderByCreatedAtDesc()
                : boardRepository.findByStatusOrderByCreatedAtDesc(status);
        return missingPetConverter.toBoardDTOList(boards);
    }

    public MissingPetBoardDTO getBoard(Long id) {
        MissingPetBoard board = boardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));
        return missingPetConverter.toBoardDTO(board);
    }

    @Transactional
    public MissingPetBoardDTO createBoard(MissingPetBoardDTO dto) {
        Users user = usersRepository.findById(dto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        MissingPetBoard board = MissingPetBoard.builder()
                .user(user)
                .title(dto.getTitle())
                .content(dto.getContent())
                .petName(dto.getPetName())
                .species(dto.getSpecies())
                .breed(dto.getBreed())
                .gender(dto.getGender())
                .age(dto.getAge())
                .color(dto.getColor())
                .lostDate(dto.getLostDate())
                .lostLocation(dto.getLostLocation())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .status(dto.getStatus())
                .imageUrl(dto.getImageUrl())
                .build();

        MissingPetBoard saved = boardRepository.save(board);
        return missingPetConverter.toBoardDTO(saved);
    }

    @Transactional
    public MissingPetBoardDTO updateBoard(Long id, MissingPetBoardDTO dto) {
        MissingPetBoard board = boardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));

        if (StringUtils.hasText(dto.getTitle())) {
            board.setTitle(dto.getTitle());
        }
        if (dto.getContent() != null) {
            board.setContent(dto.getContent());
        }
        if (dto.getPetName() != null) {
            board.setPetName(dto.getPetName());
        }
        if (dto.getSpecies() != null) {
            board.setSpecies(dto.getSpecies());
        }
        if (dto.getBreed() != null) {
            board.setBreed(dto.getBreed());
        }
        if (dto.getGender() != null) {
            board.setGender(dto.getGender());
        }
        if (dto.getAge() != null) {
            board.setAge(dto.getAge());
        }
        if (dto.getColor() != null) {
            board.setColor(dto.getColor());
        }
        if (dto.getLostDate() != null) {
            board.setLostDate(dto.getLostDate());
        }
        if (dto.getLostLocation() != null) {
            board.setLostLocation(dto.getLostLocation());
        }
        if (dto.getLatitude() != null) {
            board.setLatitude(dto.getLatitude());
        }
        if (dto.getLongitude() != null) {
            board.setLongitude(dto.getLongitude());
        }
        if (dto.getStatus() != null) {
            board.setStatus(dto.getStatus());
        }
        if (dto.getImageUrl() != null) {
            board.setImageUrl(dto.getImageUrl());
        }

        return missingPetConverter.toBoardDTO(board);
    }

    @Transactional
    public MissingPetBoardDTO updateStatus(Long id, MissingPetStatus status) {
        MissingPetBoard board = boardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));
        board.setStatus(status);
        return missingPetConverter.toBoardDTO(board);
    }

    @Transactional
    public void deleteBoard(Long id) {
        boardRepository.deleteById(id);
    }

    public List<MissingPetCommentDTO> getComments(Long boardId) {
        MissingPetBoard board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));
        List<MissingPetComment> comments = commentRepository.findByBoardOrderByCreatedAtAsc(board);
        return missingPetConverter.toCommentDTOList(comments);
    }

    @Transactional
    public MissingPetCommentDTO addComment(Long boardId, MissingPetCommentDTO dto) {
        MissingPetBoard board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));
        Users user = usersRepository.findById(dto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        MissingPetComment comment = MissingPetComment.builder()
                .board(board)
                .user(user)
                .content(dto.getContent())
                .address(dto.getAddress())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .build();

        MissingPetComment saved = commentRepository.save(comment);
        board.getComments().add(saved);
        return missingPetConverter.toCommentDTO(saved);
    }

    @Transactional
    public void deleteComment(Long boardId, Long commentId) {
        MissingPetBoard board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));
        MissingPetComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));

        if (!comment.getBoard().getIdx().equals(board.getIdx())) {
            throw new IllegalArgumentException("Comment does not belong to the specified board");
        }

        board.getComments().removeIf(c -> c.getIdx().equals(commentId));
        commentRepository.delete(comment);
    }
}

