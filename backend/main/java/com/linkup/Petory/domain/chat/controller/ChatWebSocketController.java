package com.linkup.Petory.domain.chat.controller;

import java.security.Principal;

import org.springframework.dao.DataAccessException;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.linkup.Petory.domain.chat.dto.ChatMessageDTO;
import com.linkup.Petory.domain.chat.dto.ChatWebSocketMessageRequest;
import com.linkup.Petory.domain.chat.dto.ChatWebSocketReadRequest;
import com.linkup.Petory.domain.chat.dto.ChatWebSocketTypingRequest;
import com.linkup.Petory.domain.chat.dto.TypingStatusDTO;
import com.linkup.Petory.domain.chat.entity.MessageType;
import com.linkup.Petory.domain.chat.exception.ChatForbiddenException;
import com.linkup.Petory.domain.chat.exception.ConversationNotFoundException;
import com.linkup.Petory.domain.chat.service.ChatMessageService;
import com.linkup.Petory.domain.user.exception.UserNotFoundException;
import com.linkup.Petory.domain.user.repository.UsersRepository;

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
        private final UsersRepository usersRepository;

        /**
         * 메시지 전송
         * 클라이언트: /app/chat.send
         * 
         * @param messageRequest 메시지 요청 DTO (conversationIdx 포함)
         * @param principal      인증된 사용자 (WebSocket 인증 인터셉터에서 설정)
         */
        @SuppressWarnings("null")
        @MessageMapping("/chat.send")
        public void sendMessage(
                        @Payload ChatWebSocketMessageRequest messageRequest,
                        Principal principal) {

                try {
                        // 사용자 ID 추출 (principal.getName()은 로그인 ID를 반환하므로 Users 테이블에서 조회)
                        String loginId = principal.getName();
                        Long senderIdx = usersRepository.findByIdString(loginId)
                                        .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다: " + loginId))
                                        .getIdx();
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

                } catch (UserNotFoundException | IllegalArgumentException | ChatForbiddenException
                                | ConversationNotFoundException | MessagingException | DataAccessException e) {
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
                        // 사용자 ID 추출
                        String loginId = principal.getName();
                        Long userId = usersRepository.findByIdString(loginId)
                                        .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다: " + loginId))
                                        .getIdx();

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

                } catch (UserNotFoundException | ChatForbiddenException | DataAccessException e) {
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
                        // 사용자 ID 추출
                        String loginId = principal.getName();
                        Long userId = usersRepository.findByIdString(loginId)
                                        .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다: " + loginId))
                                        .getIdx();

                        log.debug("WebSocket 타이핑: conversationIdx={}, userId={}, isTyping={}",
                                        typingRequest.getConversationIdx(), userId, typingRequest.isTyping());

                        // 다른 참여자에게 타이핑 상태 브로드캐스트 (본인 제외)
                        messagingTemplate.convertAndSend(
                                        "/topic/conversation/" + typingRequest.getConversationIdx() + "/typing",
                                        new TypingStatusDTO(userId, typingRequest.isTyping()));

                } catch (UserNotFoundException | MessagingException | DataAccessException e) {
                        log.error("WebSocket 타이핑 처리 실패: {}", e.getMessage(), e);
                }
        }

}
