package com.linkup.Petory.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.dto.BoardDTO;
import com.linkup.Petory.dto.CommentDTO;
import com.linkup.Petory.service.BoardService;
import com.linkup.Petory.service.CommentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;
    private final CommentService commentService;

    // 전체 게시글 조회
    @PreAuthorize("permitAll()")
    @GetMapping
    public ResponseEntity<List<BoardDTO>> getAllBoards(
            @RequestParam(required = false) String category) {
        System.out.println("=== API 호출됨: GET /api/boards ===");
        return ResponseEntity.ok(boardService.getAllBoards(category));
    }

    // 단일 게시글 조회
    @PreAuthorize("permitAll()")
    @GetMapping("/{id}")
    public ResponseEntity<BoardDTO> getBoard(@PathVariable Long id) {
        return ResponseEntity.ok(boardService.getBoard(id));
    }

    // 게시글 생성 (로그인 필요)
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BoardDTO> createBoard(@RequestBody BoardDTO dto) {
        System.out.println("=== 게시글 생성: " + dto.getTitle() + " ===");
        return ResponseEntity.ok(boardService.createBoard(dto));
    }

    // 게시글 수정 (로그인 필요)
    @PutMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<BoardDTO> updateBoard(@PathVariable Long id, @RequestBody BoardDTO dto) {
        return ResponseEntity.ok(boardService.updateBoard(id, dto));
    }

    // 게시글 삭제 (로그인 필요)
    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteBoard(@PathVariable Long id) {
        boardService.deleteBoard(id);
        return ResponseEntity.noContent().build();
    }

    // 내 게시글 조회
    @GetMapping("/my-posts")
    public ResponseEntity<List<BoardDTO>> getMyBoards(@RequestParam Long userId) {
        return ResponseEntity.ok(boardService.getMyBoards(userId));
    }

    // 게시글 검색
    @GetMapping("/search")
    public ResponseEntity<List<BoardDTO>> searchBoards(@RequestParam String keyword) {
        return ResponseEntity.ok(boardService.searchBoards(keyword));
    }

    @PreAuthorize("permitAll()")
    @GetMapping("/{boardId}/comments")
    public ResponseEntity<List<CommentDTO>> getComments(@PathVariable Long boardId) {
        return ResponseEntity.ok(commentService.getComments(boardId));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/{boardId}/comments")
    public ResponseEntity<CommentDTO> addComment(
            @PathVariable Long boardId,
            @RequestBody CommentDTO dto) {
        return ResponseEntity.ok(commentService.addComment(boardId, dto));
    }

    @PreAuthorize("isAuthenticated()")
    @DeleteMapping("/{boardId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long boardId, @PathVariable Long commentId) {
        commentService.deleteComment(boardId, commentId);
        return ResponseEntity.noContent().build();
    }
}
