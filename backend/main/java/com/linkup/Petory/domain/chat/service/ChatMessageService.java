package com.linkup.Petory.domain.chat.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.chat.converter.ChatMessageConverter;
import com.linkup.Petory.domain.chat.dto.ChatMessageDTO;
import com.linkup.Petory.domain.chat.entity.ChatMessage;
import com.linkup.Petory.domain.chat.entity.Conversation;
import com.linkup.Petory.domain.chat.entity.ConversationParticipant;
import com.linkup.Petory.domain.chat.entity.MessageType;
import com.linkup.Petory.domain.chat.entity.ParticipantStatus;
import com.linkup.Petory.domain.chat.repository.ChatMessageRepository;
import com.linkup.Petory.domain.chat.repository.ConversationParticipantRepository;
import com.linkup.Petory.domain.chat.repository.ConversationRepository;
import com.linkup.Petory.domain.chat.repository.MessageReadStatusRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageReadStatusRepository readStatusRepository;
    private final UsersRepository usersRepository;
    private final ChatMessageConverter messageConverter;

    /**
     * 메시지 전송
     */
    @Transactional
    public ChatMessageDTO sendMessage(Long conversationIdx, Long senderIdx, String content, MessageType messageType) {
        // 1. 전송자 확인
        Users sender = usersRepository.findById(senderIdx)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (Boolean.TRUE.equals(sender.getIsDeleted())) {
            throw new IllegalStateException("탈퇴한 사용자는 메시지를 보낼 수 없습니다.");
        }

        // 2. 채팅방 확인
        Conversation conversation = conversationRepository.findById(conversationIdx)
                .orElseThrow(() -> new IllegalArgumentException("채팅방을 찾을 수 없습니다."));

        // 3. 참여자인지 확인
        ConversationParticipant senderParticipant = participantRepository
                .findByConversationIdxAndUserIdx(conversationIdx, senderIdx)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 참여자가 아닙니다."));

        if (senderParticipant.getStatus() != ParticipantStatus.ACTIVE) {
            throw new IllegalStateException("채팅방에 참여 중이 아닙니다.");
        }

        // 4. 메시지 저장
        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .sender(sender)
                .content(content)
                .messageType(messageType != null ? messageType : MessageType.TEXT)
                .build();

        message = chatMessageRepository.save(message);

        // 5. 참여자들의 읽지 않은 메시지 수 증가 (본인 제외)
        // DB 레벨 원자적 증가로 Lost Update 방지
        // 현재 (문제 있음)
        // for (ConversationParticipant p : participants) {
        // p.incrementUnreadCount();
        // participantRepository.save(p);
        // }

        // 개선 (원자적 증가)
        participantRepository.incrementUnreadCount(conversationIdx, senderIdx);

        // 6. Conversation 메타데이터 업데이트
        conversation.setLastMessageAt(LocalDateTime.now());
        String preview = messageType == MessageType.IMAGE ? "[사진]"
                : messageType == MessageType.FILE ? "[파일]"
                        : content.length() > 200 ? content.substring(0, 200) : content;
        conversation.setLastMessagePreview(preview);
        conversationRepository.save(conversation);

        return messageConverter.toDTO(message);
    }

    /**
     * 채팅방 메시지 조회 (페이징)
     */
    public Page<ChatMessageDTO> getMessages(Long conversationIdx, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<ChatMessage> messages = chatMessageRepository
                .findByConversationIdxOrderByCreatedAtDesc(conversationIdx, pageable);

        return messages.map(messageConverter::toDTO);
    }

    /**
     * 채팅방 메시지 조회 (커서 기반 페이징)
     */
    public List<ChatMessageDTO> getMessagesBefore(Long conversationIdx, LocalDateTime beforeDate, int size) {
        Pageable pageable = PageRequest.of(0, size);

        List<ChatMessage> messages = chatMessageRepository
                .findByConversationIdxAndCreatedAtBeforeOrderByCreatedAtDesc(conversationIdx, beforeDate, pageable);

        return messages.stream()
                .map(messageConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 메시지 읽음 처리
     */
    @Transactional
    public void markAsRead(Long conversationIdx, Long userId, Long lastMessageIdx) {
        // 참여자 확인
        ConversationParticipant participant = participantRepository
                .findByConversationIdxAndUserIdx(conversationIdx, userId)
                .orElseThrow(() -> new IllegalArgumentException("채팅방 참여자가 아닙니다."));

        // 읽지 않은 메시지 수 초기화
        participant.setUnreadCount(0);
        if (lastMessageIdx != null) {
            ChatMessage lastMessage = chatMessageRepository.findById(lastMessageIdx)
                    .orElse(null);
            if (lastMessage != null) {
                participant.setLastReadMessage(lastMessage);
                participant.setLastReadAt(LocalDateTime.now());
            }
        }
        participantRepository.save(participant);

        // MessageReadStatus 기록 (선택사항 - 필요한 경우)
        // 읽지 않은 메시지들에 대해 읽음 상태 기록
        if (lastMessageIdx != null) {
            List<ChatMessage> unreadMessages = chatMessageRepository
                    .findByConversationIdxOrderByCreatedAtDesc(conversationIdx)
                    .stream()
                    .filter(m -> m.getCreatedAt().isBefore(
                            chatMessageRepository.findById(lastMessageIdx)
                                    .map(ChatMessage::getCreatedAt)
                                    .orElse(LocalDateTime.now()))
                            && !m.getSender().getIdx().equals(userId))
                    .collect(Collectors.toList());

            Users user = usersRepository.findById(userId).orElseThrow();
            for (ChatMessage message : unreadMessages) {
                // 이미 읽음 처리된 메시지는 스킵
                if (!readStatusRepository.existsByMessageAndUser(message, user)) {
                    // 필요시 MessageReadStatus 생성
                    // readStatusRepository.save(...);
                }
            }
        }
    }

    /**
     * 메시지 삭제 (Soft Delete)
     */
    @Transactional
    public void deleteMessage(Long messageIdx, Long userId) {
        ChatMessage message = chatMessageRepository.findById(messageIdx)
                .orElseThrow(() -> new IllegalArgumentException("메시지를 찾을 수 없습니다."));

        // 본인 메시지만 삭제 가능
        if (!message.getSender().getIdx().equals(userId)) {
            throw new IllegalArgumentException("본인 메시지만 삭제할 수 있습니다.");
        }

        message.setIsDeleted(true);
        message.setDeletedAt(LocalDateTime.now());
        chatMessageRepository.save(message);
    }

    /**
     * 메시지 검색
     */
    public List<ChatMessageDTO> searchMessages(Long conversationIdx, String keyword) {
        List<ChatMessage> messages = chatMessageRepository
                .searchMessagesByKeyword(conversationIdx, keyword);

        return messages.stream()
                .map(messageConverter::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * 읽지 않은 메시지 수 조회
     */
    public Long getUnreadCount(Long conversationIdx, Long userId) {
        ConversationParticipant participant = participantRepository
                .findByConversationIdxAndUserIdx(conversationIdx, userId)
                .orElse(null);

        if (participant == null) {
            return 0L;
        }

        return participant.getUnreadCount() != null ? (long) participant.getUnreadCount() : 0L;
    }
}
