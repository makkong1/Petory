package com.linkup.Petory.domain.chat.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.chat.dto.ConversationParticipantDTO;
import com.linkup.Petory.domain.chat.entity.ConversationParticipant;

@Component
public class ConversationParticipantConverter {

    public ConversationParticipantDTO toDTO(ConversationParticipant participant) {
        ConversationParticipantDTO.ConversationParticipantDTOBuilder builder = 
            ConversationParticipantDTO.builder()
                .idx(participant.getIdx())
                .conversationIdx(participant.getConversation() != null 
                    ? participant.getConversation().getIdx() : null)
                .userIdx(participant.getUser() != null 
                    ? participant.getUser().getIdx() : null)
                .role(participant.getRole() != null 
                    ? participant.getRole().name() : null)
                .unreadCount(participant.getUnreadCount())
                .lastReadMessageIdx(participant.getLastReadMessage() != null 
                    ? participant.getLastReadMessage().getIdx() : null)
                .lastReadAt(participant.getLastReadAt())
                .status(participant.getStatus() != null 
                    ? participant.getStatus().name() : null)
                .joinedAt(participant.getJoinedAt())
                .leftAt(participant.getLeftAt())
                .isDeleted(participant.getIsDeleted())
                .deletedAt(participant.getDeletedAt())
                .createdAt(participant.getCreatedAt())
                .updatedAt(participant.getUpdatedAt());

        // 사용자 정보 추가
        if (participant.getUser() != null) {
            builder.username(participant.getUser().getUsername())
                   .isDeletedUser(participant.getUser().getIsDeleted());
            // 프로필 이미지는 attachment_file에서 가져와야 하므로 여기서는 null
            builder.userProfileImageUrl(null);
        }

        return builder.build();
    }

    public List<ConversationParticipantDTO> toDTOList(List<ConversationParticipant> participants) {
        return participants.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}

