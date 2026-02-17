package com.linkup.Petory.domain.board.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.board.converter.MissingPetConverter;
import com.linkup.Petory.domain.board.dto.MissingPetCommentDTO;
import com.linkup.Petory.domain.board.dto.MissingPetCommentPageResponseDTO;
import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetComment;
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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MissingPetCommentService {

    private final MissingPetBoardRepository boardRepository;
    private final MissingPetCommentRepository commentRepository;
    private final UsersRepository usersRepository;
    private final MissingPetConverter missingPetConverter;
    private final AttachmentFileService attachmentFileService;
    private final NotificationService notificationService;

    /**
     * 댓글 목록 조회 (페이징 지원)
     * 엔드포인트: GET /api/missing-pets/{id}/comments?page={page}&size={size}
     * - 생성일 기준 오름차순 정렬
     * - 삭제된 댓글 제외
     * - 각 댓글의 파일 정보 포함
     * - 댓글 파일 배치 조회 (N+1 문제 해결)
     */
    public MissingPetCommentPageResponseDTO getCommentsWithPaging(Long boardId, int page, int size) {
        // 게시글 존재 확인
        boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));

        Pageable pageable = PageRequest.of(page, size);
        Page<MissingPetComment> commentPage = commentRepository.findByBoardIdAndIsDeletedFalseOrderByCreatedAtAsc(boardId, pageable);

        if (commentPage.isEmpty()) {
            return new MissingPetCommentPageResponseDTO(
                    new ArrayList<>(),
                    0,
                    0,
                    page,
                    size,
                    false,
                    false);
        }

        List<MissingPetComment> comments = commentPage.getContent();

        // 댓글 ID 리스트 추출
        List<Long> commentIds = comments.stream()
                .map(MissingPetComment::getIdx)
                .collect(Collectors.toList());

        // 댓글 파일 배치 조회 (N+1 문제 해결)
        Map<Long, List<FileDTO>> filesByCommentId = attachmentFileService.getAttachmentsBatch(
                FileTargetType.MISSING_PET_COMMENT, commentIds);

        // DTO 변환 (배치 조회된 파일 정보 사용)
        List<MissingPetCommentDTO> commentDTOs = comments.stream()
                .map(comment -> {
                    MissingPetCommentDTO dto = missingPetConverter.toCommentDTO(comment);
                    List<FileDTO> attachments = filesByCommentId.getOrDefault(comment.getIdx(), List.of());
                    dto.setAttachments(attachments);
                    dto.setImageUrl(attachmentFileService.extractPrimaryFileUrl(attachments));
                    return dto;
                })
                .collect(Collectors.toList());

        return new MissingPetCommentPageResponseDTO(
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
     * 엔드포인트: GET /api/missing-pets/{id}/comments
     * - 생성일 기준 오름차순 정렬
     * - 삭제된 댓글 제외
     * - 각 댓글의 파일 정보 포함
     * - 댓글 파일 배치 조회 (N+1 문제 해결)
     */
    public List<MissingPetCommentDTO> getComments(Long boardId) {
        MissingPetBoard board = boardRepository.findById(boardId)
                .orElseThrow(() -> new IllegalArgumentException("Missing pet board not found"));
        List<MissingPetComment> comments = commentRepository.findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(board);

        // 댓글 ID 리스트 추출
        List<Long> commentIds = comments.stream()
                .map(MissingPetComment::getIdx)
                .collect(Collectors.toList());

        // 댓글 파일 배치 조회 (N+1 문제 해결)
        Map<Long, List<FileDTO>> filesByCommentId = commentIds.isEmpty()
                ? Map.of()
                : attachmentFileService.getAttachmentsBatch(
                        FileTargetType.MISSING_PET_COMMENT, commentIds);

        // DTO 변환 (배치 조회된 파일 정보 사용)
        return comments.stream()
                .map(comment -> {
                    MissingPetCommentDTO dto = missingPetConverter.toCommentDTO(comment);
                    List<FileDTO> attachments = filesByCommentId.getOrDefault(comment.getIdx(), List.of());
                    dto.setAttachments(attachments);
                    dto.setImageUrl(attachmentFileService.extractPrimaryFileUrl(attachments));
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 댓글 작성
     * 엔드포인트: POST /api/missing-pets/{id}/comments
     * - 댓글 이미지 업로드 지원
     * - 목격 위치(주소, 좌표) 저장 가능
     * - 게시글 작성자에게 알림 발송 (비동기 처리)
     */
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

        // 알림 발송: 댓글 작성자가 게시글 작성자가 아닌 경우에만 알림 발송 (비동기 처리)
        Long boardOwnerId = board.getUser().getIdx();
        if (!boardOwnerId.equals(user.getIdx())) {
            sendMissingPetCommentNotificationAsync(
                    boardOwnerId,
                    user.getUsername(),
                    dto.getContent(),
                    board.getIdx());
        }

        return mapCommentWithAttachments(saved);
    }

    /**
     * 댓글 삭제 (소프트 삭제)
     * 엔드포인트: DELETE /api/missing-pets/{boardId}/comments/{commentId}
     * - 게시글과 댓글의 연관 관계 검증
     */
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

    /**
     * 게시글의 댓글 수 조회 (COUNT 쿼리 사용 - N건 로드 방지)
     * @param board 게시글 엔티티
     * @return 댓글 수
     */
    public int getCommentCount(MissingPetBoard board) {
        return (int) commentRepository.countByBoardAndIsDeletedFalse(board);
    }

    /**
     * 게시글별 댓글 수 배치 조회 (N+1 문제 해결)
     * @param boardIds 게시글 ID 목록
     * @return 게시글 ID를 키로 하는 댓글 수 맵
     */
    public Map<Long, Integer> getCommentCountsBatch(List<Long> boardIds) {
        if (boardIds == null || boardIds.isEmpty()) {
            return Map.of();
        }
        
        List<Object[]> results = commentRepository.countCommentsByBoardIds(boardIds);
        return results.stream()
                .collect(Collectors.toMap(
                        row -> ((Long) row[0]),
                        row -> ((Long) row[1]).intValue()
                ));
    }

    /**
     * 게시글의 모든 댓글 소프트 삭제
     * @param board 게시글 엔티티
     */
    @Transactional
    public void deleteAllCommentsByBoard(MissingPetBoard board) {
        List<MissingPetComment> comments = commentRepository.findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(board);
        for (MissingPetComment c : comments) {
            c.setIsDeleted(true);
            c.setDeletedAt(java.time.LocalDateTime.now());
            commentRepository.save(c);
        }
    }

    /**
     * 댓글 DTO 매핑 (파일 정보 포함)
     * 단일 댓글 조회 시 사용
     */
    private MissingPetCommentDTO mapCommentWithAttachments(MissingPetComment comment) {
        MissingPetCommentDTO dto = missingPetConverter.toCommentDTO(comment);
        List<FileDTO> attachments = attachmentFileService.getAttachments(FileTargetType.MISSING_PET_COMMENT,
                comment.getIdx());
        dto.setAttachments(attachments);
        dto.setImageUrl(attachmentFileService.extractPrimaryFileUrl(attachments));
        return dto;
    }

    /**
     * 실종제보 댓글 알림 발송 (비동기 처리)
     * 알림 발송 실패 시에도 댓글 작성은 성공하도록 분리
     */
    @Async
    public void sendMissingPetCommentNotificationAsync(Long boardOwnerId, String username, String content,
            Long boardIdx) {
        try {
            String notificationContent = content != null && content.length() > 50
                    ? content.substring(0, 50) + "..."
                    : content;

            notificationService.createNotification(
                    boardOwnerId,
                    NotificationType.MISSING_PET_COMMENT,
                    "실종 제보 게시글에 새로운 댓글이 달렸습니다",
                    String.format("%s님이 댓글을 남겼습니다: %s", username, notificationContent),
                    boardIdx,
                    "MISSING_PET");
        } catch (Exception e) {
            log.error("실종제보 댓글 알림 발송 실패: boardOwnerId={}, boardIdx={}, error={}",
                    boardOwnerId, boardIdx, e.getMessage(), e);
            // 알림 발송 실패는 로깅만 하고 예외를 던지지 않음 (댓글 작성과 분리)
        }
    }
}
