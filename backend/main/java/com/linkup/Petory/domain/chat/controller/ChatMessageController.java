package com.linkup.Petory.domain.chat.controller;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.chat.dto.ChatMessageDTO;
import com.linkup.Petory.domain.chat.dto.SendMessageRequest;
import com.linkup.Petory.domain.chat.entity.MessageType;
import com.linkup.Petory.domain.chat.service.ChatMessageService;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chat/messages")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    private Long getCurrentUserId() {
        return authenticatedUserIdResolver.requireCurrentUserIdx();
    }

    /**
     * 메시지 전송
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatMessageDTO> sendMessage(@RequestBody SendMessageRequest request) {

        MessageType messageType = request.messageType() != null
                ? MessageType.valueOf(request.messageType())
                : MessageType.TEXT;

        ChatMessageDTO dto = chatMessageService.sendMessage(
                request.conversationIdx(),
                getCurrentUserId(),
                request.content(),
                messageType);

        return ResponseEntity.ok(dto);
    }

    /**
     * 채팅방 메시지 조회 (페이징)
     * 재참여한 경우 joinedAt 이후 메시지만 조회
     */
    @GetMapping("/conversation/{conversationIdx}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ChatMessageDTO>> getMessages(
            @PathVariable("conversationIdx") Long conversationIdx,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        return ResponseEntity.ok(chatMessageService.getMessages(conversationIdx, getCurrentUserId(), page, size));
    }

    /**
     * 채팅방 메시지 조회 (커서 기반 페이징)
     */
    @GetMapping("/conversation/{conversationIdx}/before")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ChatMessageDTO>> getMessagesBefore(
            @PathVariable("conversationIdx") Long conversationIdx,
            @RequestParam("beforeDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beforeDate,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        return ResponseEntity.ok(chatMessageService.getMessagesBefore(
                conversationIdx, getCurrentUserId(), beforeDate, size));
    }

    /**
     * 메시지 읽음 처리
     */
    @PostMapping("/conversation/{conversationIdx}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAsRead(
            @PathVariable("conversationIdx") Long conversationIdx,
            @RequestParam(value = "lastMessageIdx", required = false) Long lastMessageIdx) {
        chatMessageService.markAsRead(conversationIdx, getCurrentUserId(), lastMessageIdx);
        return ResponseEntity.noContent().build();
    }

    /**
     * 메시지 삭제
     */
    @DeleteMapping("/{messageIdx}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteMessage(@PathVariable("messageIdx") Long messageIdx) {
        chatMessageService.deleteMessage(messageIdx, getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 메시지 검색
     */
    @GetMapping("/conversation/{conversationIdx}/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ChatMessageDTO>> searchMessages(
            @PathVariable("conversationIdx") Long conversationIdx,
            @RequestParam("keyword") String keyword) {
        return ResponseEntity.ok(chatMessageService.searchMessages(
                conversationIdx, getCurrentUserId(), keyword));
    }

    /**
     * 읽지 않은 메시지 수 조회
     */
    @GetMapping("/conversation/{conversationIdx}/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> getUnreadCount(@PathVariable("conversationIdx") Long conversationIdx) {
        return ResponseEntity.ok(chatMessageService.getUnreadCount(conversationIdx, getCurrentUserId()));
    }
}
