package com.linkup.Petory.domain.board.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.board.dto.MissingPetBoardDTO;
import com.linkup.Petory.domain.board.dto.MissingPetCommentDTO;
import com.linkup.Petory.domain.board.entity.MissingPetStatus;
import com.linkup.Petory.domain.board.service.MissingPetBoardService;
import com.linkup.Petory.domain.board.service.MissingPetCommentService;
import com.linkup.Petory.domain.chat.dto.ConversationDTO;
import com.linkup.Petory.domain.chat.service.ConversationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 실종 제보 게시글 및 댓글 API 컨트롤러
 * 
 * 서비스 분리:
 * - MissingPetBoardService: 게시글 관련 로직 (CRUD)
 * - MissingPetCommentService: 댓글 관련 로직 (CRUD)
 */
@Slf4j
@RestController
@RequestMapping("/api/missing-pets")
@RequiredArgsConstructor
public class MissingPetBoardController {

    private final MissingPetBoardService missingPetBoardService;
    private final MissingPetCommentService missingPetCommentService;
    private final ConversationService conversationService;

    // ==================== 게시글 관련 API (MissingPetBoardService) ====================

    /**
     * 실종 제보 목록 조회
     * GET /api/missing-pets?status={status}
     * 서비스: MissingPetBoardService.getBoards()
     */
    @GetMapping
    public ResponseEntity<List<MissingPetBoardDTO>> listBoards(
            @RequestParam(required = false) MissingPetStatus status) {
        return ResponseEntity.ok(missingPetBoardService.getBoards(status));
    }

    /**
     * 실종 제보 상세 조회
     * GET /api/missing-pets/{id}
     * 서비스: MissingPetBoardService.getBoard()
     * 참고: 댓글은 별도 API로 조회 (GET /api/missing-pets/{id}/comments)
     */
    @GetMapping("/{id}")
    public ResponseEntity<MissingPetBoardDTO> getBoard(@PathVariable Long id) {
        return ResponseEntity.ok(missingPetBoardService.getBoard(id));
    }

    /**
     * 실종 제보 작성
     * POST /api/missing-pets
     * 서비스: MissingPetBoardService.createBoard()
     */
    @PostMapping
    public ResponseEntity<MissingPetBoardDTO> createBoard(@RequestBody MissingPetBoardDTO request) {
        MissingPetBoardDTO created = missingPetBoardService.createBoard(request);
        return ResponseEntity.ok(created);
    }

    /**
     * 실종 제보 수정
     * PUT /api/missing-pets/{id}
     * 서비스: MissingPetBoardService.updateBoard()
     */
    @PutMapping("/{id}")
    public ResponseEntity<MissingPetBoardDTO> updateBoard(
            @PathVariable Long id,
            @RequestBody MissingPetBoardDTO request) {
        MissingPetBoardDTO updated = missingPetBoardService.updateBoard(id, request);
        return ResponseEntity.ok(updated);
    }

    /**
     * 실종 제보 상태 변경
     * PATCH /api/missing-pets/{id}/status
     * 서비스: MissingPetBoardService.updateStatus()
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<MissingPetBoardDTO> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {
        String statusValue = body.get("status");
        if (statusValue == null) {
            throw new IllegalArgumentException("status is required");
        }

        MissingPetStatus status = MissingPetStatus.valueOf(statusValue);
        MissingPetBoardDTO updated = missingPetBoardService.updateStatus(id, status);
        return ResponseEntity.ok(updated);
    }

    /**
     * 실종 제보 삭제 (소프트 삭제)
     * DELETE /api/missing-pets/{id}
     * 서비스: MissingPetBoardService.deleteBoard()
     * 참고: 관련 댓글도 함께 소프트 삭제됨 (MissingPetCommentService.deleteAllCommentsByBoard())
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteBoard(@PathVariable Long id) {
        missingPetBoardService.deleteBoard(id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    // ==================== 댓글 관련 API (MissingPetCommentService)
    // ====================

    /**
     * 댓글 목록 조회
     * GET /api/missing-pets/{id}/comments
     * 서비스: MissingPetCommentService.getComments()
     */
    @GetMapping("/{id}/comments")
    public ResponseEntity<List<MissingPetCommentDTO>> getComments(@PathVariable Long id) {
        return ResponseEntity.ok(missingPetCommentService.getComments(id));
    }

    /**
     * 댓글 작성
     * POST /api/missing-pets/{id}/comments
     * 서비스: MissingPetCommentService.addComment()
     */
    @PostMapping("/{id}/comments")
    public ResponseEntity<MissingPetCommentDTO> addComment(
            @PathVariable Long id,
            @RequestBody MissingPetCommentDTO request) {
        MissingPetCommentDTO created = missingPetCommentService.addComment(id, request);
        return ResponseEntity.ok(created);
    }

    /**
     * 댓글 삭제 (소프트 삭제)
     * DELETE /api/missing-pets/{boardId}/comments/{commentId}
     * 서비스: MissingPetCommentService.deleteComment()
     */
    @DeleteMapping("/{boardId}/comments/{commentId}")
    public ResponseEntity<Map<String, Object>> deleteComment(
            @PathVariable Long boardId,
            @PathVariable Long commentId) {
        missingPetCommentService.deleteComment(boardId, commentId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    // ==================== 채팅 관련 API ====================

    /**
     * 실종제보 채팅 시작 ("목격했어요" 버튼 클릭)
     * POST /api/missing-pets/{boardIdx}/start-chat?witnessId={witnessId}
     * 서비스: MissingPetBoardService.getBoard() +
     * ConversationService.createMissingPetChat()
     */

    /**
     * 실종제보 채팅 시작 ("목격했어요" 버튼 클릭)
     */
    @PostMapping("/{boardIdx}/start-chat")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationDTO> startMissingPetChat(
            @PathVariable Long boardIdx,
            @RequestParam Long witnessId) {
        // 실종제보 조회하여 제보자 ID 확인
        MissingPetBoardDTO board = missingPetBoardService.getBoard(boardIdx);
        Long reporterId = board.getUserId();

        ConversationDTO conversation = conversationService.createMissingPetChat(
                boardIdx, reporterId, witnessId);

        return ResponseEntity.ok(conversation);
    }
}
