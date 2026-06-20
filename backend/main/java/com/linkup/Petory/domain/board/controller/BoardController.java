package com.linkup.Petory.domain.board.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.board.dto.BoardDTO;
import com.linkup.Petory.domain.board.dto.BoardPageResponseDTO;
import com.linkup.Petory.domain.board.dto.CommentDTO;
import com.linkup.Petory.domain.board.dto.CommentPageResponseDTO;
import com.linkup.Petory.domain.board.dto.ReactionRequest;
import com.linkup.Petory.domain.board.dto.ReactionSummaryDTO;
import com.linkup.Petory.domain.board.dto.BoardPopularitySnapshotDTO;
import com.linkup.Petory.domain.board.service.ReactionService;
import com.linkup.Petory.domain.board.service.BoardService;
import com.linkup.Petory.domain.board.service.CommentService;
import com.linkup.Petory.domain.board.service.BoardPopularityService;
import com.linkup.Petory.domain.board.entity.PopularityPeriodType;
import com.linkup.Petory.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final CommentService commentService;
    private final ReactionService reactionService;
    private final BoardPopularityService boardPopularityService;

    // 전체 게시글 조회 (페이징 지원)
    @PreAuthorize("permitAll()")
    @GetMapping
    public ResponseEntity<BoardPageResponseDTO> getAllBoards(
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(boardService.getAllBoardsWithPaging(category, page, size));
    }

    // 단일 게시글 조회
    @PreAuthorize("permitAll()")
    @GetMapping("/{id}")
    public ResponseEntity<BoardDTO> getBoard(
            @PathVariable("id") Long id,
            @RequestParam(value = "viewerId", required = false) Long viewerId) {
        return ResponseEntity.ok(boardService.getBoard(id, viewerId));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/popular")
    public ResponseEntity<List<BoardPopularitySnapshotDTO>> getPopularBoards(
            @RequestParam(name = "period", defaultValue = "WEEKLY") PopularityPeriodType periodType) {
        return ResponseEntity.ok(boardPopularityService.getPopularBoards(periodType));
    }

    // 게시글 생성 (로그인 필요)
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BoardDTO> createBoard(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @Valid @RequestBody BoardDTO dto) {
        return ResponseEntity.ok(boardService.createBoard(dto, userDetails.getLoginId()));
    }

    // 게시글 수정 (로그인 필요)
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BoardDTO> updateBoard(@PathVariable("id") Long id, @RequestBody BoardDTO dto) {
        return ResponseEntity.ok(boardService.updateBoard(id, dto));
    }

    // 게시글 삭제 (로그인 필요)
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteBoard(@PathVariable("id") Long id) {
        boardService.deleteBoard(id);
        return ResponseEntity.noContent().build();
    }

    // 내 게시글 조회
    @GetMapping("/my-posts")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<BoardDTO>> getMyBoards(@RequestParam("userId") Long userId) {
        return ResponseEntity.ok(boardService.getMyBoards(userId));
    }

    // 게시글 검색 (페이징 지원)
    @GetMapping("/search")
    @PreAuthorize("permitAll()")
    public ResponseEntity<BoardPageResponseDTO> searchBoards(
            @RequestParam("keyword") String keyword,
            @RequestParam(value = "searchType", required = false, defaultValue = "TITLE_CONTENT") String searchType,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(boardService.searchBoardsWithPaging(keyword, searchType, page, size));
    }

    /**
     * 댓글 목록 조회 (페이징 지원)
     * GET /api/boards/{boardId}/comments?page={page}&size={size}
     * 서비스: CommentService.getCommentsWithPaging()
     */
    @PreAuthorize("permitAll()")
    @GetMapping("/{boardId}/comments")
    public ResponseEntity<CommentPageResponseDTO> getComments(
            @PathVariable("boardId") Long boardId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(commentService.getCommentsWithPaging(boardId, page, size));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{boardId}/comments")
    public ResponseEntity<CommentDTO> addComment(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @PathVariable("boardId") Long boardId,
            @Valid @RequestBody CommentDTO dto) {
        return ResponseEntity.ok(commentService.addComment(boardId, dto, userDetails.getLoginId()));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{boardId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable("boardId") Long boardId,
            @PathVariable("commentId") Long commentId) {
        commentService.deleteComment(boardId, commentId);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{boardId}/reactions")
    public ResponseEntity<ReactionSummaryDTO> reactToBoard(
            @PathVariable("boardId") Long boardId,
            @Valid @RequestBody ReactionRequest request) {
        ReactionSummaryDTO summary = reactionService.reactToBoard(boardId, request.userId(),
                request.reactionType());
        return ResponseEntity.ok(summary);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{boardId}/comments/{commentId}/reactions")
    public ResponseEntity<ReactionSummaryDTO> reactToComment(
            @PathVariable("boardId") Long boardId,
            @PathVariable("commentId") Long commentId,
            @Valid @RequestBody ReactionRequest request) {
        ReactionSummaryDTO summary = reactionService.reactToComment(commentId, request.userId(),
                request.reactionType());
        return ResponseEntity.ok(summary);
    }
}
