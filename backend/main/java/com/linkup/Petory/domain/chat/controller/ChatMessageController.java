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

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/chat/messages")
@RequiredArgsConstructor
public class ChatMessageController {

    private final ChatMessageService chatMessageService;

    /**
     * 메시지 전송
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ChatMessageDTO> sendMessage(
            @RequestBody SendMessageRequest request,
            @RequestParam Long senderIdx) {
        
        MessageType messageType = request.getMessageType() != null 
            ? MessageType.valueOf(request.getMessageType()) : MessageType.TEXT;
        
        ChatMessageDTO dto = chatMessageService.sendMessage(
                request.getConversationIdx(),
                senderIdx,
                request.getContent(),
                messageType);
        
        return ResponseEntity.ok(dto);
    }

    /**
     * 채팅방 메시지 조회 (페이징)
     */
    @GetMapping("/conversation/{conversationIdx}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<ChatMessageDTO>> getMessages(
            @PathVariable Long conversationIdx,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(chatMessageService.getMessages(conversationIdx, page, size));
    }

    /**
     * 채팅방 메시지 조회 (커서 기반 페이징)
     */
    @GetMapping("/conversation/{conversationIdx}/before")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ChatMessageDTO>> getMessagesBefore(
            @PathVariable Long conversationIdx,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime beforeDate,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(chatMessageService.getMessagesBefore(conversationIdx, beforeDate, size));
    }

    /**
     * 메시지 읽음 처리
     */
    @PostMapping("/conversation/{conversationIdx}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> markAsRead(
            @PathVariable Long conversationIdx,
            @RequestParam Long userId,
            @RequestParam(required = false) Long lastMessageIdx) {
        chatMessageService.markAsRead(conversationIdx, userId, lastMessageIdx);
        return ResponseEntity.noContent().build();
    }

    /**
     * 메시지 삭제
     */
    @DeleteMapping("/{messageIdx}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> deleteMessage(
            @PathVariable Long messageIdx,
            @RequestParam Long userId) {
        chatMessageService.deleteMessage(messageIdx, userId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 메시지 검색
     */
    @GetMapping("/conversation/{conversationIdx}/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<ChatMessageDTO>> searchMessages(
            @PathVariable Long conversationIdx,
            @RequestParam String keyword) {
        return ResponseEntity.ok(chatMessageService.searchMessages(conversationIdx, keyword));
    }

    /**
     * 읽지 않은 메시지 수 조회
     */
    @GetMapping("/conversation/{conversationIdx}/unread-count")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Long> getUnreadCount(
            @PathVariable Long conversationIdx,
            @RequestParam Long userId) {
        return ResponseEntity.ok(chatMessageService.getUnreadCount(conversationIdx, userId));
    }
}

