package com.linkup.Petory.domain.care.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.care.dto.CareRequestCommentDTO;
import com.linkup.Petory.domain.care.service.CareRequestCommentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/care-requests/{careRequestId}/comments")
@RequiredArgsConstructor
public class CareRequestCommentController {

    private final CareRequestCommentService commentService;

    @GetMapping
    public ResponseEntity<List<CareRequestCommentDTO>> getComments(@PathVariable Long careRequestId) {
        return ResponseEntity.ok(commentService.getComments(careRequestId));
    }

    @PostMapping
    public ResponseEntity<CareRequestCommentDTO> addComment(
            @PathVariable Long careRequestId,
            @RequestBody CareRequestCommentDTO dto) {
        return ResponseEntity.ok(commentService.addComment(careRequestId, dto));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long careRequestId,
            @PathVariable Long commentId) {
        commentService.deleteComment(careRequestId, commentId);
        return ResponseEntity.noContent().build();
    }
}
