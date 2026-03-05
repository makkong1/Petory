package com.linkup.Petory.domain.board.controller;

import java.util.HashMap;
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
import com.linkup.Petory.domain.board.dto.MissingPetBoardPageResponseDTO;
import com.linkup.Petory.domain.board.dto.MissingPetCommentDTO;
import com.linkup.Petory.domain.board.dto.MissingPetCommentPageResponseDTO;
import com.linkup.Petory.domain.board.entity.MissingPetStatus;
import com.linkup.Petory.domain.board.exception.BoardValidationException;
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
     * 실종 제보 목록 조회 (페이징 지원)
     * GET /api/missing-pets?status={status}&page={page}&size={size}
     * 서비스: MissingPetBoardService.getBoardsWithPaging()
     */
    @GetMapping
    public ResponseEntity<MissingPetBoardPageResponseDTO> listBoards(
            @RequestParam(required = false) MissingPetStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(missingPetBoardService.getBoardsWithPaging(status, page, size));
    }

    /**
     * 실종 제보 상세 조회 (댓글 페이징 지원)
     * GET
     * /api/missing-pets/{id}?commentPage={commentPage}&commentSize={commentSize}
     * 서비스: MissingPetBoardService.getBoard()
     * - 댓글 페이징 처리 (commentPage, commentSize 파라미터)
     * - 기본값: commentPage=0, commentSize=20 (첫 페이지, 20개씩)
     * - 댓글 제외: commentSize=0
     * - 댓글 전체: 별도 API 사용 (GET /api/missing-pets/{id}/comments)
     */
    @GetMapping("/{id}")
    public ResponseEntity<MissingPetBoardDTO> getBoard(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int commentPage,
            @RequestParam(defaultValue = "20") int commentSize) {
        // commentSize가 0이면 댓글 제외
        Integer page = commentSize > 0 ? commentPage : null;
        Integer size = commentSize > 0 ? commentSize : null;
        return ResponseEntity.ok(missingPetBoardService.getBoard(id, page, size));
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
            throw BoardValidationException.statusRequired();
        }

        // [리팩토링] valueOf 예외 처리 - 사용자 친화적 에러 메시지
        MissingPetStatus status;
        try {
            status = MissingPetStatus.valueOf(statusValue);
        } catch (IllegalArgumentException e) {
            throw BoardValidationException.invalidStatus("MISSING, FOUND, RESOLVED");
        }
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

    // ========== 댓글 관련 API (MissingPetCommentService) ==========

    /**
     * 댓글 목록 조회 (페이징 지원)
     * GET /api/missing-pets/{id}/comments?page={page}&size={size}
     * 서비스: MissingPetCommentService.getCommentsWithPaging()
     */
    @GetMapping("/{id}/comments")
    public ResponseEntity<MissingPetCommentPageResponseDTO> getComments(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(missingPetCommentService.getCommentsWithPaging(id, page, size));
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
        // [리팩토링] getBoard 전체 조회 → userId 프로젝션 1쿼리
        Long reporterId = missingPetBoardService.getUserIdByBoardIdx(boardIdx);

        ConversationDTO conversation = conversationService.createMissingPetChat(
                boardIdx, reporterId, witnessId);

        return ResponseEntity.ok(conversation);
    }
}
