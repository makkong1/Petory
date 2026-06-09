package com.linkup.Petory.domain.notification.dto;

import java.time.LocalDateTime;

import com.linkup.Petory.domain.notification.entity.NotificationType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 알림 응답 DTO. 알림 유형·제목·내용·관련 도메인 ID·읽음 여부를 포함한다.
 */
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
