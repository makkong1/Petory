package com.linkup.Petory.domain.chat.controller;

import java.security.Principal;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.linkup.Petory.domain.chat.dto.ChatMessageDTO;
import com.linkup.Petory.domain.chat.entity.MessageType;
import com.linkup.Petory.domain.chat.service.ChatMessageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 실시간 채팅 컨트롤러
 * STOMP 프로토콜을 사용한 실시간 메시지 처리
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatMessageService chatMessageService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * 메시지 전송
     * 클라이언트: /app/chat.send
     * 
     * @param messageRequest 메시지 요청 DTO (conversationIdx 포함)
     * @param principal      인증된 사용자 (WebSocket 인증 인터셉터에서 설정)
     */
    @MessageMapping("/chat.send")
    public void sendMessage(
            @Payload ChatWebSocketMessageRequest messageRequest,
            Principal principal) {

        try {
            // 사용자 ID 추출
            Long senderIdx = Long.parseLong(principal.getName());
            Long conversationIdx = messageRequest.getConversationIdx();

            log.info("WebSocket 메시지 전송: conversationIdx={}, senderIdx={}, content={}",
                    conversationIdx, senderIdx, messageRequest.getContent());

            // 메시지 타입 파싱
            MessageType messageType = messageRequest.getMessageType() != null
                    ? MessageType.valueOf(messageRequest.getMessageType())
                    : MessageType.TEXT;

            // 메시지 전송 (서비스 호출)
            ChatMessageDTO messageDTO = chatMessageService.sendMessage(
                    conversationIdx,
                    senderIdx,
                    messageRequest.getContent(),
                    messageType);

            // 채팅방 참여자들에게 브로드캐스트
            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + conversationIdx,
                    messageDTO);

        } catch (Exception e) {
            log.error("WebSocket 메시지 전송 실패: {}", e.getMessage(), e);

            // 에러 메시지 전송
            ChatMessageDTO errorMessage = new ChatMessageDTO();
            errorMessage.setContent("메시지 전송에 실패했습니다: " + e.getMessage());
            messagingTemplate.convertAndSend(
                    "/user/" + principal.getName() + "/queue/errors",
                    errorMessage);
        }
    }

    /**
     * 메시지 읽음 처리
     * 클라이언트: /app/chat.read
     */
    @MessageMapping("/chat.read")
    public void markAsRead(
            @Payload ChatWebSocketReadRequest readRequest,
            Principal principal) {

        try {
            Long userId = Long.parseLong(principal.getName());

            log.info("WebSocket 읽음 처리: conversationIdx={}, userId={}",
                    readRequest.getConversationIdx(), userId);

            // 읽음 처리
            chatMessageService.markAsRead(
                    readRequest.getConversationIdx(),
                    userId,
                    readRequest.getLastMessageIdx());

            // 다른 참여자에게 읽음 상태 알림 (선택사항)
            // messagingTemplate.convertAndSend(
            // "/topic/conversation/" + readRequest.getConversationIdx() + "/read",
            // new ReadStatusDTO(userId, readRequest.getLastMessageIdx()));

        } catch (Exception e) {
            log.error("WebSocket 읽음 처리 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * 타이핑 표시 (선택사항)
     * 클라이언트: /app/chat.typing
     */
    @MessageMapping("/chat.typing")
    public void typing(
            @Payload ChatWebSocketTypingRequest typingRequest,
            Principal principal) {

        try {
            Long userId = Long.parseLong(principal.getName());

            log.debug("WebSocket 타이핑: conversationIdx={}, userId={}, isTyping={}",
                    typingRequest.getConversationIdx(), userId, typingRequest.isTyping());

            // 다른 참여자에게 타이핑 상태 브로드캐스트 (본인 제외)
            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + typingRequest.getConversationIdx() + "/typing",
                    new TypingStatusDTO(userId, typingRequest.isTyping()));

        } catch (Exception e) {
            log.error("WebSocket 타이핑 처리 실패: {}", e.getMessage(), e);
        }
    }

    /**
     * WebSocket 메시지 요청 DTO
     */
    public static class ChatWebSocketMessageRequest {
        private Long conversationIdx;
        private String content;
        private String messageType;
        private Long replyToMessageIdx;

        // Getters and Setters
        public Long getConversationIdx() {
            return conversationIdx;
        }

        public void setConversationIdx(Long conversationIdx) {
            this.conversationIdx = conversationIdx;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getMessageType() {
            return messageType;
        }

        public void setMessageType(String messageType) {
            this.messageType = messageType;
        }

        public Long getReplyToMessageIdx() {
            return replyToMessageIdx;
        }

        public void setReplyToMessageIdx(Long replyToMessageIdx) {
            this.replyToMessageIdx = replyToMessageIdx;
        }
    }

    /**
     * WebSocket 읽음 요청 DTO
     */
    public static class ChatWebSocketReadRequest {
        private Long conversationIdx;
        private Long lastMessageIdx;

        // Getters and Setters
        public Long getConversationIdx() {
            return conversationIdx;
        }

        public void setConversationIdx(Long conversationIdx) {
            this.conversationIdx = conversationIdx;
        }

        public Long getLastMessageIdx() {
            return lastMessageIdx;
        }

        public void setLastMessageIdx(Long lastMessageIdx) {
            this.lastMessageIdx = lastMessageIdx;
        }
    }

    /**
     * WebSocket 타이핑 요청 DTO
     */
    public static class ChatWebSocketTypingRequest {
        private Long conversationIdx;
        private boolean isTyping;

        // Getters and Setters
        public Long getConversationIdx() {
            return conversationIdx;
        }

        public void setConversationIdx(Long conversationIdx) {
            this.conversationIdx = conversationIdx;
        }

        public boolean isTyping() {
            return isTyping;
        }

        public void setTyping(boolean typing) {
            isTyping = typing;
        }
    }

    /**
     * 타이핑 상태 DTO
     */
    public static class TypingStatusDTO {
        private Long userId;
        private boolean isTyping;

        public TypingStatusDTO(Long userId, boolean isTyping) {
            this.userId = userId;
            this.isTyping = isTyping;
        }

        // Getters and Setters
        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public boolean isTyping() {
            return isTyping;
        }

        public void setTyping(boolean typing) {
            isTyping = typing;
        }
    }
}
