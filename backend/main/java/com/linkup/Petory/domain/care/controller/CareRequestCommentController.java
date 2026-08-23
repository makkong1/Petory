package com.linkup.Petory.domain.care.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.care.dto.CareRequestCommentDTO;
import com.linkup.Petory.domain.care.service.CareRequestCommentService;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 펫케어 요청 댓글 조회·작성·삭제 API.
 */
@RestController
@RequestMapping("/api/care-requests/{careRequestId}/comments")
@RequiredArgsConstructor
public class CareRequestCommentController {

    private final CareRequestCommentService commentService;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    private Long getCurrentUserId() {
        return authenticatedUserIdResolver.requireCurrentUserIdx();
    }

    @GetMapping
    public ResponseEntity<List<CareRequestCommentDTO>> getComments(@PathVariable("careRequestId") Long careRequestId) {
        return ResponseEntity.ok(commentService.getComments(careRequestId));
    }

    @PostMapping
    public ResponseEntity<CareRequestCommentDTO> addComment(
            @PathVariable("careRequestId") Long careRequestId,
            @Valid @RequestBody CareRequestCommentDTO dto) {
        return ResponseEntity.ok(commentService.addComment(careRequestId, dto, getCurrentUserId()));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable("careRequestId") Long careRequestId,
            @PathVariable("commentId") Long commentId) {
        commentService.deleteComment(careRequestId, commentId, getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
