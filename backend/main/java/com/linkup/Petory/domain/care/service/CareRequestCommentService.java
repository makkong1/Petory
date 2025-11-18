package com.linkup.Petory.domain.care.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.care.converter.CareRequestCommentConverter;
import com.linkup.Petory.domain.care.dto.CareRequestCommentDTO;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestComment;
import com.linkup.Petory.domain.care.repository.CareRequestCommentRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.file.entity.FileTargetType;
import com.linkup.Petory.domain.file.service.AttachmentFileService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CareRequestCommentService {

        private final CareRequestCommentRepository commentRepository;
        private final CareRequestRepository careRequestRepository;
        private final UsersRepository usersRepository;
        private final CareRequestCommentConverter commentConverter;
        private final AttachmentFileService attachmentFileService;

        public List<CareRequestCommentDTO> getComments(Long careRequestId) {
                CareRequest careRequest = careRequestRepository.findById(careRequestId)
                                .orElseThrow(() -> new IllegalArgumentException("CareRequest not found"));
                List<CareRequestComment> comments = commentRepository
                                .findByCareRequestAndIsDeletedFalseOrderByCreatedAtAsc(careRequest);
                return comments.stream()
                                .map(comment -> {
                                        CareRequestCommentDTO dto = commentConverter.toDTO(comment);
                                        dto.setAttachments(attachmentFileService
                                                        .getAttachments(FileTargetType.CARE_COMMENT, comment.getIdx()));
                                        return dto;
                                })
                                .collect(Collectors.toList());
        }

        @Transactional
        public CareRequestCommentDTO addComment(Long careRequestId, CareRequestCommentDTO dto) {
                CareRequest careRequest = careRequestRepository.findById(careRequestId)
                                .orElseThrow(() -> new IllegalArgumentException("CareRequest not found"));
                Users user = usersRepository.findById(dto.getUserId())
                                .orElseThrow(() -> new IllegalArgumentException("User not found"));

                // SERVICE_PROVIDER만 댓글 작성 가능
                if (user.getRole() != Role.SERVICE_PROVIDER) {
                        throw new IllegalStateException("당신은 댓글 작성 불가입니다.");
                }

                CareRequestComment comment = CareRequestComment.builder()
                                .careRequest(careRequest)
                                .user(user)
                                .content(dto.getContent())
                                .build();

                CareRequestComment saved = commentRepository.save(comment);
                if (dto.getAttachments() != null && !dto.getAttachments().isEmpty()) {
                        // 첫 번째 파일만 저장 (syncSingleAttachment는 단일 파일만 지원)
                        FileDTO firstFile = dto.getAttachments().get(0);
                        if (firstFile != null && firstFile.getFilePath() != null) {
                                attachmentFileService.syncSingleAttachment(FileTargetType.CARE_COMMENT, saved.getIdx(),
                                                firstFile.getFilePath(), firstFile.getFileType());
                        }
                }
                CareRequestCommentDTO response = commentConverter.toDTO(saved);
                response.setAttachments(attachmentFileService
                                .getAttachments(FileTargetType.CARE_COMMENT, saved.getIdx()));
                return response;
        }

        @Transactional
        public void deleteComment(Long careRequestId, Long commentId) {
                CareRequest careRequest = careRequestRepository.findById(careRequestId)
                                .orElseThrow(() -> new IllegalArgumentException("CareRequest not found"));
                CareRequestComment comment = commentRepository.findById(commentId)
                                .orElseThrow(() -> new IllegalArgumentException("Comment not found"));

                if (!comment.getCareRequest().getIdx().equals(careRequest.getIdx())) {
                        throw new IllegalArgumentException("Comment does not belong to the specified care request");
                }

                // soft delete instead of physical delete
                comment.setStatus(com.linkup.Petory.domain.common.ContentStatus.DELETED);
                comment.setIsDeleted(true);
                comment.setDeletedAt(java.time.LocalDateTime.now());
                commentRepository.save(comment);
        }
}
