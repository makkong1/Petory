package com.linkup.Petory.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.dto.BoardDTO;
import com.linkup.Petory.service.BoardService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/boards")
@RequiredArgsConstructor
public class BoardController {

    private final BoardService boardService;

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
}
