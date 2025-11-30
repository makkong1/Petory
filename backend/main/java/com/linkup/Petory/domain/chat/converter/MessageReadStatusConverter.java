package com.linkup.Petory.domain.chat.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.chat.dto.MessageReadStatusDTO;
import com.linkup.Petory.domain.chat.entity.MessageReadStatus;

@Component
public class MessageReadStatusConverter {

    public MessageReadStatusDTO toDTO(MessageReadStatus readStatus) {
        return MessageReadStatusDTO.builder()
                .idx(readStatus.getIdx())
                .messageIdx(readStatus.getMessage() != null 
                    ? readStatus.getMessage().getIdx() : null)
                .userIdx(readStatus.getUser() != null 
                    ? readStatus.getUser().getIdx() : null)
                .username(readStatus.getUser() != null 
                    ? readStatus.getUser().getUsername() : null)
                .readAt(readStatus.getReadAt())
                .build();
    }

    public List<MessageReadStatusDTO> toDTOList(List<MessageReadStatus> readStatuses) {
        return readStatuses.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }
}

