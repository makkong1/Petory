package com.linkup.Petory.domain.board.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.linkup.Petory.domain.board.converter.MissingPetConverter;
import com.linkup.Petory.domain.board.dto.MissingPetBoardDTO;
import com.linkup.Petory.domain.board.dto.MissingPetCommentDTO;
import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetComment;
import com.linkup.Petory.domain.board.entity.MissingPetStatus;
import com.linkup.Petory.domain.board.repository.MissingPetBoardRepository;
import com.linkup.Petory.domain.board.repository.MissingPetCommentRepository;
import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.service.AttachmentFileService;
import com.linkup.Petory.domain.notification.entity.NotificationType;
import com.linkup.Petory.domain.notification.service.NotificationService;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissingPetBoardService {

    private final MissingPetBoardRepository boardRepository;
    private final MissingPetCommentRepository commentRepository;
    private final UsersRepository usersRepository;
    private final MissingPetConverter missingPetConverter;
    private final AttachmentFileService attachmentFileService;
    private final NotificationService notificationService;

    public List<MissingPetBoardDTO> getBoards(MissingPetStatus status) {
        List<MissingPetBoard> boards = status == null
                ? boardRepository.findAllByOrderByCreatedAtDesc()
                : boardRepository.findByStatusOrderByCreatedAtDesc(status);
        return boards.stream()
                .map(this::mapBoardWithAttachments)
                .collect(Collectors.toList());
    }

    public MissingPetBoardDTO getBoard(Long id) {
        MissingPetBoard board = boardRepository.findByIdWithUser(id)
                .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));

        // 삭제된 게시글인지 확인
        if (board.getIsDeleted()) {
            throw new IllegalArgumentException("Missing pet board not found");
        }

        return mapBoardWithAttachments(board);
    }

    @Transactional
    public MissingPetBoardDTO createBoard(MissingPetBoardDTO dto) {
        Users user = usersRepository.findById(dto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // 이메일 인증 확인
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            throw new com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException("실종 제보 작성을 위해 이메일 인증이 필요합니다.");
        }

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
                .build();

        MissingPetBoard saved = boardRepository.save(board);
        if (dto.getImageUrl() != null) {
            attachmentFileService.syncSingleAttachment(FileTargetType.MISSING_PET, saved.getIdx(), dto.getImageUrl(),
                    null);
        }
        return mapBoardWithAttachments(saved);
    }

    @Transactional
    public MissingPetBoardDTO updateBoard(Long id, MissingPetBoardDTO dto) {
        MissingPetBoard board = boardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));

        // 이메일 인증 확인
        Users user = board.getUser();
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            throw new com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException("실종 제보 수정을 위해 이메일 인증이 필요합니다.");
        }

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
            attachmentFileService.syncSingleAttachment(FileTargetType.MISSING_PET, board.getIdx(), dto.getImageUrl(),
                    null);
        }

        return mapBoardWithAttachments(board);
    }

    @Transactional
    public MissingPetBoardDTO updateStatus(Long id, MissingPetStatus status) {
        MissingPetBoard board = boardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));
        board.setStatus(status);
        return mapBoardWithAttachments(board);
    }

    @Transactional
    public void deleteBoard(Long id) {
        MissingPetBoard board = boardRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));

        // 이메일 인증 확인
        Users user = board.getUser();
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            throw new com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException("실종 제보 삭제를 위해 이메일 인증이 필요합니다.");
        }
        // soft delete board and related comments
        board.setIsDeleted(true);
        board.setDeletedAt(java.time.LocalDateTime.now());
        if (board.getComments() != null) {
            for (MissingPetComment c : board.getComments()) {
                c.setIsDeleted(true);
                c.setDeletedAt(java.time.LocalDateTime.now());
            }
        }
        boardRepository.saveAndFlush(board);
    }

    public List<MissingPetCommentDTO> getComments(Long boardId) {
        MissingPetBoard board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));
        List<MissingPetComment> comments = commentRepository.findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(board);
        return comments.stream()
                .map(this::mapCommentWithAttachments)
                .collect(Collectors.toList());
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
        if (board.getComments() != null) {
            board.getComments().add(saved);
        }
        if (dto.getImageUrl() != null) {
            attachmentFileService.syncSingleAttachment(FileTargetType.MISSING_PET_COMMENT, saved.getIdx(),
                    dto.getImageUrl(), null);
        }

        // 알림 발송: 댓글 작성자가 게시글 작성자가 아닌 경우에만 알림 발송
        Long boardOwnerId = board.getUser().getIdx();
        if (!boardOwnerId.equals(user.getIdx())) {
            notificationService.createNotification(
                    boardOwnerId,
                    NotificationType.MISSING_PET_COMMENT,
                    "실종 제보 게시글에 새로운 댓글이 달렸습니다",
                    String.format("%s님이 댓글을 남겼습니다: %s", user.getUsername(),
                            dto.getContent() != null && dto.getContent().length() > 50
                                    ? dto.getContent().substring(0, 50) + "..."
                                    : dto.getContent()),
                    board.getIdx(),
                    "MISSING_PET");
        }

        return mapCommentWithAttachments(saved);
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
        // soft delete comment
        comment.setIsDeleted(true);
        comment.setDeletedAt(java.time.LocalDateTime.now());
        commentRepository.save(comment);
    }

    private MissingPetBoardDTO mapBoardWithAttachments(MissingPetBoard board) {
        MissingPetBoardDTO dto = missingPetConverter.toBoardDTO(board);
        List<FileDTO> attachments = attachmentFileService.getAttachments(FileTargetType.MISSING_PET, board.getIdx());
        dto.setAttachments(attachments);
        dto.setImageUrl(extractPrimaryFileUrl(attachments));
        return dto;
    }

    private MissingPetCommentDTO mapCommentWithAttachments(MissingPetComment comment) {
        MissingPetCommentDTO dto = missingPetConverter.toCommentDTO(comment);
        List<FileDTO> attachments = attachmentFileService.getAttachments(FileTargetType.MISSING_PET_COMMENT,
                comment.getIdx());
        dto.setAttachments(attachments);
        dto.setImageUrl(extractPrimaryFileUrl(attachments));
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

}
