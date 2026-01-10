package com.linkup.Petory.domain.admin.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.board.dto.MissingPetBoardDTO;
import com.linkup.Petory.domain.board.dto.MissingPetCommentDTO;
import com.linkup.Petory.domain.board.entity.MissingPetStatus;
import com.linkup.Petory.domain.board.service.MissingPetBoardService;
import com.linkup.Petory.domain.board.service.MissingPetCommentService;

import lombok.RequiredArgsConstructor;

/**
 * 실종/목격 관리 컨트롤러 (관리자용)
 * - ADMIN과 MASTER 모두 접근 가능
 * - 실종 제보 목록 조회, 상태 변경, 삭제/복구
 * - 댓글 관리
 */
@RestController
@RequestMapping("/api/admin/missing-pets")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MASTER')")
public class AdminMissingPetController {

    private final MissingPetBoardService missingPetBoardService;
    private final MissingPetCommentService missingPetCommentService;

    /**
     * 실종 제보 목록 조회 (필터링 지원)
     */
    @GetMapping
    public ResponseEntity<List<MissingPetBoardDTO>> listMissingPets(
            @RequestParam(value = "status", required = false) MissingPetStatus status,
            @RequestParam(value = "deleted", required = false) Boolean deleted,
            @RequestParam(value = "q", required = false) String q) {

        List<MissingPetBoardDTO> all = missingPetBoardService.getBoards(status);

        // 삭제 여부 필터
        if (deleted != null) {
            final boolean wantDeleted = deleted.booleanValue();
            all = all.stream()
                    .filter(b -> Boolean.TRUE.equals(b.getDeleted()) == wantDeleted)
                    .collect(Collectors.toList());
        }

        // 검색어 필터
        if (q != null && !q.isBlank()) {
            String keyword = q.toLowerCase();
            all = all.stream()
                    .filter(b -> (b.getTitle() != null && b.getTitle().toLowerCase().contains(keyword))
                            || (b.getContent() != null && b.getContent().toLowerCase().contains(keyword))
                            || (b.getPetName() != null && b.getPetName().toLowerCase().contains(keyword))
                            || (b.getUsername() != null && b.getUsername().toLowerCase().contains(keyword)))
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(all);
    }

    /**
     * 실종 제보 상세 조회
     */
    @GetMapping("/{id}")
    public ResponseEntity<MissingPetBoardDTO> getMissingPet(@PathVariable Long id) {
        return ResponseEntity.ok(missingPetBoardService.getBoard(id));
    }

    /**
     * 실종 제보 상태 변경
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
        return ResponseEntity.ok(missingPetBoardService.updateStatus(id, status));
    }

    /**
     * 실종 제보 삭제 (소프트 삭제)
     */
    @PostMapping("/{id}/delete")
    public ResponseEntity<Void> deleteMissingPet(@PathVariable Long id) {
        missingPetBoardService.deleteBoard(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * 실종 제보 복구
     */
    @PostMapping("/{id}/restore")
    public ResponseEntity<MissingPetBoardDTO> restoreMissingPet(@PathVariable Long id) {
        // TODO: 서비스에 복구 메서드 추가 필요
        throw new UnsupportedOperationException("복구 기능은 아직 구현되지 않았습니다.");
    }

    /**
     * 댓글 목록 조회
     * 서비스: MissingPetCommentService.getComments()
     */
    @GetMapping("/{boardId}/comments")
    public ResponseEntity<List<MissingPetCommentDTO>> listComments(
            @PathVariable Long boardId,
            @RequestParam(value = "deleted", required = false) Boolean deleted) {
        List<MissingPetCommentDTO> comments = missingPetCommentService.getComments(boardId);

        // 삭제 여부 필터
        if (deleted != null) {
            final boolean wantDeleted = deleted.booleanValue();
            comments = comments.stream()
                    .filter(c -> Boolean.TRUE.equals(c.getDeleted()) == wantDeleted)
                    .collect(Collectors.toList());
        }

        return ResponseEntity.ok(comments);
    }

    /**
     * 댓글 삭제
     * 서비스: MissingPetCommentService.deleteComment()
     */
    @PostMapping("/{boardId}/comments/{commentId}/delete")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long boardId,
            @PathVariable Long commentId) {
        missingPetCommentService.deleteComment(boardId, commentId);
        return ResponseEntity.noContent().build();
    }
}
