package com.linkup.Petory.domain.admin.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.board.dto.BoardDTO;
import com.linkup.Petory.domain.board.dto.BoardPageResponseDTO;
import com.linkup.Petory.domain.board.dto.CommentDTO;
import com.linkup.Petory.domain.board.service.BoardService;
import com.linkup.Petory.domain.board.service.CommentService;
import com.linkup.Petory.domain.common.ContentStatus;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/boards")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MASTER')")
public class AdminBoardController {

    private final BoardService boardService;
    private final CommentService commentService;

    /**
     * [리팩토링] listBoards(페이징 없음) 제거, listBoardsWithPaging → getAdminBoardsWithPagingOptimized (DB 레벨 필터링)
     */
    @GetMapping("/paging")
    public ResponseEntity<BoardPageResponseDTO> listBoardsWithPaging(
            @RequestParam(value = "status", required = false, defaultValue = "ALL") String status,
            @RequestParam(value = "deleted", required = false) Boolean deleted,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(boardService.getAdminBoardsWithPagingOptimized(status, deleted, category, q, page, size));
    }

    /**
     * 관리자용 단일 게시글 조회 (조회수 증가 없음)
     * [리팩토링] listBoards 전체 로드 제거 → getBoard(id) 단건 조회로 대체
     * GET /api/admin/boards/{id}
     * - 삭제된 게시글도 조회 가능
     */
    @GetMapping("/{id}")
    public ResponseEntity<BoardDTO> getBoard(@PathVariable("id") Long id) {
        return ResponseEntity.ok(boardService.getBoardForAdmin(id));
    }

    @PatchMapping("/{id}/blind")
    public ResponseEntity<BoardDTO> blindBoard(@PathVariable("id") Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(boardService.updateBoardStatus(id, ContentStatus.BLINDED));
    }

    @PatchMapping("/{id}/unblind")
    public ResponseEntity<BoardDTO> unblindBoard(@PathVariable("id") Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(boardService.updateBoardStatus(id, ContentStatus.ACTIVE));
    }

    @PostMapping("/{id}/delete")
    public ResponseEntity<Void> softDeleteBoard(@PathVariable("id") Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        boardService.deleteBoard(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public ResponseEntity<BoardDTO> restoreBoard(@PathVariable("id") Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(boardService.restoreBoard(id));
    }

    // Comments moderation list for a board (status: ALL/ACTIVE/BLINDED/DELETED;
    // deleted true/false)
    @GetMapping("/{boardId}/comments")
    public ResponseEntity<List<CommentDTO>> listComments(
            @PathVariable("boardId") Long boardId,
            @RequestParam(value = "status", required = false, defaultValue = "ALL") String status,
            @RequestParam(value = "deleted", required = false) Boolean deleted) {
        // 관리자용: 작성자 상태 체크 없이 조회
        List<CommentDTO> list = commentService.getCommentsForAdmin(boardId);
        if (deleted != null) {
            final boolean wantDeleted = deleted.booleanValue();
            list = list.stream().filter(c -> Boolean.TRUE.equals(c.getDeleted()) == wantDeleted)
                    .collect(Collectors.toList());
        }
        if (status != null && !"ALL".equalsIgnoreCase(status)) {
            list = list.stream().filter(c -> status.equalsIgnoreCase(c.getStatus())).collect(Collectors.toList());
        }
        return ResponseEntity.ok(list);
    }

    @PatchMapping("/{boardId}/comments/{commentId}/blind")
    public ResponseEntity<CommentDTO> blindComment(@PathVariable Long boardId, @PathVariable Long commentId,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(commentService.updateCommentStatus(boardId, commentId, ContentStatus.BLINDED));
    }

    @PatchMapping("/{boardId}/comments/{commentId}/unblind")
    public ResponseEntity<CommentDTO> unblindComment(@PathVariable Long boardId, @PathVariable Long commentId,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(commentService.updateCommentStatus(boardId, commentId, ContentStatus.ACTIVE));
    }

    @PostMapping("/{boardId}/comments/{commentId}/delete")
    public ResponseEntity<Void> softDeleteComment(@PathVariable Long boardId, @PathVariable Long commentId,
            @RequestBody(required = false) Map<String, Object> body) {
        commentService.deleteComment(boardId, commentId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{boardId}/comments/{commentId}/restore")
    public ResponseEntity<CommentDTO> restoreComment(@PathVariable Long boardId, @PathVariable Long commentId,
            @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(commentService.restoreComment(boardId, commentId));
    }
}

