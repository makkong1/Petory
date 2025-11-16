package com.linkup.Petory.domain.board.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.board.dto.BoardDTO;
import com.linkup.Petory.domain.board.dto.CommentDTO;
import com.linkup.Petory.domain.board.service.BoardService;
import com.linkup.Petory.domain.board.service.CommentService;
import com.linkup.Petory.domain.common.ContentStatus;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MASTER')")
public class AdminCommunityController {

    private final BoardService boardService;
    private final CommentService commentService;

    // Boards moderation list with filters (status: ALL/ACTIVE/BLINDED/DELETED; deleted: true/false; category; q)
@GetMapping("/boards")
    public ResponseEntity<List<BoardDTO>> listBoards(
@RequestParam(value = "status", required = false, defaultValue = "ALL") String status,
@RequestParam(value = "deleted", required = false) Boolean deleted,
@RequestParam(value = "category", required = false) String category,
@RequestParam(value = "q", required = false) String q) {
        List<BoardDTO> all = boardService.getAllBoards(category); // already excludes deleted
        // include deleted if requested
        if (Boolean.TRUE.equals(deleted)) {
            // fetch all (including deleted) then filter by category/q
            List<BoardDTO> allIncludingDeleted = boardService.getAllBoards(null);
if (category != null && !"ALL".equalsIgnoreCase(category)) {
                allIncludingDeleted = allIncludingDeleted.stream()
                        .filter(b -> category.equalsIgnoreCase(b.getCategory()))
                        .collect(Collectors.toList());
            }
            all = allIncludingDeleted;
        }
        // filter by deleted flag when provided
        if (deleted != null) {
            final boolean wantDeleted = deleted.booleanValue();
            all = all.stream().filter(b -> Boolean.TRUE.equals(b.getDeleted()) == wantDeleted).collect(Collectors.toList());
        }
        // filter by status when provided (except ALL)
if (status != null && !"ALL".equalsIgnoreCase(status)) {
            all = all.stream()
                    .filter(b -> status.equalsIgnoreCase(b.getStatus()))
                    .collect(Collectors.toList());
        }
        // filter by keyword
        if (q != null && !q.isBlank()) {
            String kw = q.toLowerCase();
            all = all.stream()
                    .filter(b -> (b.getTitle() != null && b.getTitle().toLowerCase().contains(kw))
                            || (b.getContent() != null && b.getContent().toLowerCase().contains(kw))
                            || (b.getUsername() != null && b.getUsername().toLowerCase().contains(kw)))
                    .collect(Collectors.toList());
        }
        return ResponseEntity.ok(all);
    }

@PatchMapping("/boards/{id}/blind")
public ResponseEntity<BoardDTO> blindBoard(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(boardService.updateBoardStatus(id, ContentStatus.BLINDED));
    }

@PatchMapping("/boards/{id}/unblind")
public ResponseEntity<BoardDTO> unblindBoard(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(boardService.updateBoardStatus(id, ContentStatus.ACTIVE));
    }

@PostMapping("/boards/{id}/delete")
public ResponseEntity<Void> softDeleteBoard(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        boardService.deleteBoard(id);
        return ResponseEntity.noContent().build();
    }

@PostMapping("/boards/{id}/restore")
public ResponseEntity<BoardDTO> restoreBoard(@PathVariable("id") Long id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(boardService.restoreBoard(id));
    }

    // Comments moderation list for a board (status: ALL/ACTIVE/BLINDED/DELETED; deleted true/false)
@GetMapping("/boards/{boardId}/comments")
    public ResponseEntity<List<CommentDTO>> listComments(
@PathVariable("boardId") Long boardId,
@RequestParam(value = "status", required = false, defaultValue = "ALL") String status,
@RequestParam(value = "deleted", required = false) Boolean deleted) {
        List<CommentDTO> list = commentService.getComments(boardId);
        if (deleted != null) {
            final boolean wantDeleted = deleted.booleanValue();
            list = list.stream().filter(c -> Boolean.TRUE.equals(c.getDeleted()) == wantDeleted).collect(Collectors.toList());
        }
if (status != null && !"ALL".equalsIgnoreCase(status)) {
            list = list.stream().filter(c -> status.equalsIgnoreCase(c.getStatus())).collect(Collectors.toList());
        }
        return ResponseEntity.ok(list);
    }

@PatchMapping("/boards/{boardId}/comments/{commentId}/blind")
public ResponseEntity<CommentDTO> blindComment(@PathVariable Long boardId, @PathVariable Long commentId, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(commentService.updateCommentStatus(boardId, commentId, ContentStatus.BLINDED));
    }

@PatchMapping("/boards/{boardId}/comments/{commentId}/unblind")
public ResponseEntity<CommentDTO> unblindComment(@PathVariable Long boardId, @PathVariable Long commentId, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(commentService.updateCommentStatus(boardId, commentId, ContentStatus.ACTIVE));
    }

@PostMapping("/boards/{boardId}/comments/{commentId}/delete")
public ResponseEntity<Void> softDeleteComment(@PathVariable Long boardId, @PathVariable Long commentId, @RequestBody(required = false) Map<String, Object> body) {
        commentService.deleteComment(boardId, commentId);
        return ResponseEntity.noContent().build();
    }

@PostMapping("/boards/{boardId}/comments/{commentId}/restore")
public ResponseEntity<CommentDTO> restoreComment(@PathVariable Long boardId, @PathVariable Long commentId, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(commentService.restoreComment(boardId, commentId));
    }
}


