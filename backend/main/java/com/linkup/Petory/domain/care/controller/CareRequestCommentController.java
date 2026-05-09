package com.linkup.Petory.domain.care.controller;

import java.util.List;

import jakarta.validation.Valid;

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
    public ResponseEntity<List<CareRequestCommentDTO>> getComments(@PathVariable("careRequestId") Long careRequestId) {
        return ResponseEntity.ok(commentService.getComments(careRequestId));
    }

    @PostMapping
    public ResponseEntity<CareRequestCommentDTO> addComment(
            @PathVariable("careRequestId") Long careRequestId,
            @Valid @RequestBody CareRequestCommentDTO dto) {
        return ResponseEntity.ok(commentService.addComment(careRequestId, dto));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable("careRequestId") Long careRequestId,
            @PathVariable("commentId") Long commentId) {
        commentService.deleteComment(careRequestId, commentId);
        return ResponseEntity.noContent().build();
    }
}
