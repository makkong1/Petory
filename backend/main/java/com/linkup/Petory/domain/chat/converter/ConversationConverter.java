package com.linkup.Petory.domain.chat.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.chat.dto.ConversationDTO;
import com.linkup.Petory.domain.chat.entity.Conversation;


@Component
public class ConversationConverter {

    // 순환 참조 방지를 위해 의존성 제거
    // 필요시 Service 레이어에서 개별 Converter를 주입받아 사용

    public ConversationDTO toDTO(Conversation conversation) {
        ConversationDTO.ConversationDTOBuilder builder = ConversationDTO.builder()
                .idx(conversation.getIdx())
                .conversationType(conversation.getConversationType() != null 
                    ? conversation.getConversationType().name() : null)
                .title(conversation.getTitle())
                .relatedType(conversation.getRelatedType() != null 
                    ? conversation.getRelatedType().name() : null)
                .relatedIdx(conversation.getRelatedIdx())
                .status(conversation.getStatus() != null 
                    ? conversation.getStatus().name() : null)
                .lastMessageAt(conversation.getLastMessageAt())
                .lastMessagePreview(conversation.getLastMessagePreview())
                .isDeleted(conversation.getIsDeleted())
                .deletedAt(conversation.getDeletedAt())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .participantCount(conversation.getParticipants() != null 
                    ? conversation.getParticipants().size() : 0);

        // 참여자 정보와 마지막 메시지는 Service 레이어에서 별도로 채워넣기
        // 순환 참조 방지를 위해 Converter 간 의존성 제거

        return builder.build();
    }

    public List<ConversationDTO> toDTOList(List<Conversation> conversations) {
        return conversations.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}

