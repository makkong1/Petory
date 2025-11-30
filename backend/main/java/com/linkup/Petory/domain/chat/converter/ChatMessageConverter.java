package com.linkup.Petory.domain.chat.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.chat.dto.ChatMessageDTO;
import com.linkup.Petory.domain.chat.entity.ChatMessage;


@Component
public class ChatMessageConverter {

    // 순환 참조 방지 - 필요시 Service 레이어에서 주입

    public ChatMessageDTO toDTO(ChatMessage message) {
        ChatMessageDTO.ChatMessageDTOBuilder builder = ChatMessageDTO.builder()
                .idx(message.getIdx())
                .conversationIdx(message.getConversation() != null 
                    ? message.getConversation().getIdx() : null)
                .senderIdx(message.getSender() != null 
                    ? message.getSender().getIdx() : null)
                .messageType(message.getMessageType() != null 
                    ? message.getMessageType().name() : null)
                .content(message.getContent())
                .replyToMessageIdx(message.getReplyToMessage() != null 
                    ? message.getReplyToMessage().getIdx() : null)
                .isDeleted(message.getIsDeleted())
                .deletedAt(message.getDeletedAt())
                .createdAt(message.getCreatedAt())
                .updatedAt(message.getUpdatedAt());

        // 전송자 정보 추가
        if (message.getSender() != null) {
            builder.senderUsername(message.getSender().getUsername())
                   .isDeletedSender(message.getSender().getIsDeleted());
            // 프로필 이미지는 attachment_file에서 가져와야 하므로 여기서는 null
            builder.senderProfileImageUrl(null);
        }

        // 답장 메시지와 읽음 상태는 Service 레이어에서 별도로 채워넣기
        // 순환 참조 방지

        // 파일 첨부 정보는 서비스 레이어에서 추가
        builder.attachments(null);

        return builder.build();
    }

    public List<ChatMessageDTO> toDTOList(List<ChatMessage> messages) {
        return messages.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}

