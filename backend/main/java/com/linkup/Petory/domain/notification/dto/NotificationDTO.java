package com.linkup.Petory.domain.notification.dto;

import java.time.LocalDateTime;

import com.linkup.Petory.domain.notification.entity.NotificationType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDTO {

    private Long idx;
    private Long userId;
    private NotificationType type;
    private String title;
    private String content;
    private Long relatedId;
    private String relatedType;
    private Boolean isRead;
    private LocalDateTime createdAt;
}

