package com.linkup.Petory.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.converter.CareRequestCommentConverter;
import com.linkup.Petory.dto.CareRequestCommentDTO;
import com.linkup.Petory.entity.CareRequest;
import com.linkup.Petory.entity.CareRequestComment;
import com.linkup.Petory.entity.Role;
import com.linkup.Petory.entity.Users;
import com.linkup.Petory.repository.CareRequestCommentRepository;
import com.linkup.Petory.repository.CareRequestRepository;
import com.linkup.Petory.repository.UsersRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CareRequestCommentService {

    private final CareRequestCommentRepository commentRepository;
    private final CareRequestRepository careRequestRepository;
    private final UsersRepository usersRepository;
    private final CareRequestCommentConverter commentConverter;

    public List<CareRequestCommentDTO> getComments(Long careRequestId) {
        CareRequest careRequest = careRequestRepository.findById(careRequestId)
                .orElseThrow(() -> new IllegalArgumentException("CareRequest not found"));
        List<CareRequestComment> comments = commentRepository.findByCareRequestOrderByCreatedAtAsc(careRequest);
        return comments.stream()
                .map(commentConverter::toDTO)
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
                .commentFilePath(dto.getCommentFilePath())
                .build();

        CareRequestComment saved = commentRepository.save(comment);
        return commentConverter.toDTO(saved);
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

        commentRepository.delete(comment);
    }
}

