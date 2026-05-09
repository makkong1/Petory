package com.linkup.Petory.domain.chat.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.chat.dto.ConversationDTO;
import com.linkup.Petory.domain.chat.dto.CreateConversationRequest;
import com.linkup.Petory.domain.chat.entity.ConversationStatus;
import com.linkup.Petory.domain.chat.entity.ConversationType;
import com.linkup.Petory.domain.chat.entity.RelatedType;
import com.linkup.Petory.domain.chat.service.ConversationService;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    private Long getCurrentUserId() {
        return authenticatedUserIdResolver.requireCurrentUserIdx();
    }

    /**
     * 내 채팅방 목록 조회
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ConversationDTO>> getMyConversations() {
        return ResponseEntity.ok(conversationService.getMyConversations(getCurrentUserId()));
    }

    /**
     * 채팅방 상세 조회
     */
    @GetMapping("/{conversationIdx}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationDTO> getConversation(@PathVariable("conversationIdx") Long conversationIdx) {
        return ResponseEntity.ok(conversationService.getConversation(conversationIdx, getCurrentUserId()));
    }

    /**
     * 채팅방 생성 (범용)
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationDTO> createConversation(@Valid @RequestBody CreateConversationRequest request) {
        ConversationType conversationType = ConversationType.valueOf(request.conversationType());
        RelatedType relatedType = request.relatedType() != null
                ? RelatedType.valueOf(request.relatedType())
                : null;

        ConversationDTO dto = conversationService.createConversation(
                conversationType,
                relatedType,
                request.relatedIdx(),
                request.title(),
                request.participantUserIds(),
                getCurrentUserId());

        return ResponseEntity.ok(dto);
    }

    /**
     * 펫케어 요청 채팅방 생성
     */
    @PostMapping("/care-request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationDTO> createCareRequestConversation(
            @RequestParam("careApplicationIdx") Long careApplicationIdx) {
        return ResponseEntity.ok(conversationService.createCareRequestConversation(
                careApplicationIdx, getCurrentUserId()));
    }

    /**
     * 1:1 일반 채팅방 생성 또는 조회
     */
    @PostMapping("/direct")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationDTO> getOrCreateDirectConversation(
            @RequestParam("otherUserId") Long otherUserId) {
        return ResponseEntity.ok(conversationService.getOrCreateDirectConversation(
                getCurrentUserId(), otherUserId));
    }

    /**
     * 채팅방 나가기
     */
    @PostMapping("/{conversationIdx}/leave")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> leaveConversation(@PathVariable("conversationIdx") Long conversationIdx) {
        conversationService.leaveConversation(conversationIdx, getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 채팅방 삭제
     */
    @DeleteMapping("/{conversationIdx}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteConversation(@PathVariable("conversationIdx") Long conversationIdx) {
        conversationService.deleteConversation(conversationIdx, getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 채팅방 상태 변경
     */
    @PatchMapping("/{conversationIdx}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationDTO> updateConversationStatus(
            @PathVariable("conversationIdx") Long conversationIdx,
            @RequestParam("status") String status) {
        ConversationStatus conversationStatus = ConversationStatus.valueOf(status);
        return ResponseEntity.ok(conversationService.updateConversationStatus(
                conversationIdx, conversationStatus, getCurrentUserId()));
    }

    /**
     * 산책모임 채팅방 참여
     */
    @PostMapping("/meetup/{meetupIdx}/join")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationDTO> joinMeetupChat(
            @PathVariable("meetupIdx") Long meetupIdx) {
        return ResponseEntity.ok(conversationService.joinMeetupChat(meetupIdx, getCurrentUserId()));
    }

    /**
     * 산책모임 채팅방 참여 인원 수 조회
     */
    @GetMapping("/meetup/{meetupIdx}/participant-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Integer> getMeetupChatParticipantCount(
            @PathVariable("meetupIdx") Long meetupIdx) {
        return ResponseEntity.ok(conversationService.getMeetupChatParticipantCount(meetupIdx));
    }

    /**
     * 펫케어 거래 확정
     */
    @PostMapping("/{conversationIdx}/confirm-deal")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> confirmCareDeal(@PathVariable("conversationIdx") Long conversationIdx) {
        conversationService.confirmCareDeal(conversationIdx, getCurrentUserId());
        return ResponseEntity.noContent().build();
    }
}
