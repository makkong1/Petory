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
import com.linkup.Petory.domain.chat.dto.ConversationDTO;
import com.linkup.Petory.domain.chat.service.ConversationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/missing-pets")
@RequiredArgsConstructor
public class MissingPetBoardController {

    private final MissingPetBoardService missingPetBoardService;
    private final ConversationService conversationService;

    @GetMapping
    public ResponseEntity<List<MissingPetBoardDTO>> listBoards(
            @RequestParam(required = false) MissingPetStatus status) {
        return ResponseEntity.ok(missingPetBoardService.getBoards(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MissingPetBoardDTO> getBoard(@PathVariable Long id) {
        return ResponseEntity.ok(missingPetBoardService.getBoard(id));
    }

    @PostMapping
    public ResponseEntity<MissingPetBoardDTO> createBoard(@RequestBody MissingPetBoardDTO request) {
        MissingPetBoardDTO created = missingPetBoardService.createBoard(request);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<MissingPetBoardDTO> updateBoard(
            @PathVariable Long id,
            @RequestBody MissingPetBoardDTO request) {
        MissingPetBoardDTO updated = missingPetBoardService.updateBoard(id, request);
        return ResponseEntity.ok(updated);
    }

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

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteBoard(@PathVariable Long id) {
        missingPetBoardService.deleteBoard(id);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/comments")
    public ResponseEntity<List<MissingPetCommentDTO>> getComments(@PathVariable Long id) {
        return ResponseEntity.ok(missingPetBoardService.getComments(id));
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<MissingPetCommentDTO> addComment(
            @PathVariable Long id,
            @RequestBody MissingPetCommentDTO request) {
        MissingPetCommentDTO created = missingPetBoardService.addComment(id, request);
        return ResponseEntity.ok(created);
    }

    @DeleteMapping("/{boardId}/comments/{commentId}")
    public ResponseEntity<Map<String, Object>> deleteComment(
            @PathVariable Long boardId,
            @PathVariable Long commentId) {
        missingPetBoardService.deleteComment(boardId, commentId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        return ResponseEntity.ok(response);
    }

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
