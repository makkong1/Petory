package com.linkup.Petory.domain.notification.converter;

import com.linkup.Petory.domain.notification.dto.NotificationDTO;
import com.linkup.Petory.domain.notification.entity.Notification;

import org.springframework.stereotype.Component;

@Component
public class NotificationConverter {

    public NotificationDTO toDTO(Notification notification) {
        if (notification == null) {
            return null;
        }

        return NotificationDTO.builder()
                .idx(notification.getIdx())
                .userId(notification.getUser() != null ? notification.getUser().getIdx() : null)
                .type(notification.getType())
                .title(notification.getTitle())
                .content(notification.getContent())
                .relatedId(notification.getRelatedId())
                .relatedType(notification.getRelatedType())
                .isRead(notification.getIsRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    public Notification toEntity(NotificationDTO dto) {
        if (dto == null) {
            return null;
        }

        return Notification.builder()
                .idx(dto.getIdx())
                .type(dto.getType())
                .title(dto.getTitle())
                .content(dto.getContent())
                .relatedId(dto.getRelatedId())
                .relatedType(dto.getRelatedType())
                .isRead(dto.getIsRead() != null ? dto.getIsRead() : false)
                .createdAt(dto.getCreatedAt())
                .build();
    }
}

