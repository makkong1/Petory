package com.linkup.Petory.domain.chat.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.chat.dto.ConversationDTO;
import com.linkup.Petory.domain.chat.dto.CreateConversationRequest;
import com.linkup.Petory.domain.chat.entity.ConversationStatus;
import com.linkup.Petory.domain.chat.entity.ConversationType;
import com.linkup.Petory.domain.chat.entity.RelatedType;
import com.linkup.Petory.domain.chat.service.ConversationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chat/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    /**
     * 내 채팅방 목록 조회
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ConversationDTO>> getMyConversations(@RequestParam Long userId) {
        return ResponseEntity.ok(conversationService.getMyConversations(userId));
    }

    /**
     * 채팅방 상세 조회
     */
    @GetMapping("/{conversationIdx}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationDTO> getConversation(
            @PathVariable Long conversationIdx,
            @RequestParam Long userId) {
        return ResponseEntity.ok(conversationService.getConversation(conversationIdx, userId));
    }

    /**
     * 채팅방 생성 (범용)
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationDTO> createConversation(@RequestBody CreateConversationRequest request) {
        ConversationType conversationType = ConversationType.valueOf(request.getConversationType());
        RelatedType relatedType = request.getRelatedType() != null 
            ? RelatedType.valueOf(request.getRelatedType()) : null;
        
        ConversationDTO dto = conversationService.createConversation(
                conversationType,
                relatedType,
                request.getRelatedIdx(),
                request.getTitle(),
                request.getParticipantUserIds());
        
        return ResponseEntity.ok(dto);
    }

    /**
     * 펫케어 요청 채팅방 생성
     */
    @PostMapping("/care-request")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationDTO> createCareRequestConversation(
            @RequestParam Long careApplicationIdx,
            @RequestParam Long requesterId,
            @RequestParam Long providerId) {
        return ResponseEntity.ok(conversationService.createCareRequestConversation(
                careApplicationIdx, requesterId, providerId));
    }

    /**
     * 1:1 일반 채팅방 생성 또는 조회
     */
    @PostMapping("/direct")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationDTO> getOrCreateDirectConversation(
            @RequestParam Long user1Id,
            @RequestParam Long user2Id) {
        return ResponseEntity.ok(conversationService.getOrCreateDirectConversation(user1Id, user2Id));
    }

    /**
     * 채팅방 나가기
     */
    @PostMapping("/{conversationIdx}/leave")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> leaveConversation(
            @PathVariable Long conversationIdx,
            @RequestParam Long userId) {
        conversationService.leaveConversation(conversationIdx, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 채팅방 삭제
     */
    @DeleteMapping("/{conversationIdx}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteConversation(
            @PathVariable Long conversationIdx,
            @RequestParam Long userId) {
        conversationService.deleteConversation(conversationIdx, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 채팅방 상태 변경
     */
    @PatchMapping("/{conversationIdx}/status")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationDTO> updateConversationStatus(
            @PathVariable Long conversationIdx,
            @RequestParam String status) {
        ConversationStatus conversationStatus = ConversationStatus.valueOf(status);
        return ResponseEntity.ok(conversationService.updateConversationStatus(conversationIdx, conversationStatus));
    }

    /**
     * 산책모임 채팅방 참여
     */
    @PostMapping("/meetup/{meetupIdx}/join")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConversationDTO> joinMeetupChat(
            @PathVariable Long meetupIdx,
            @RequestParam Long userId) {
        return ResponseEntity.ok(conversationService.joinMeetupChat(meetupIdx, userId));
    }

    /**
     * 산책모임 채팅방 참여 인원 수 조회
     */
    @GetMapping("/meetup/{meetupIdx}/participant-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Integer> getMeetupChatParticipantCount(@PathVariable Long meetupIdx) {
        return ResponseEntity.ok(conversationService.getMeetupChatParticipantCount(meetupIdx));
    }
}

