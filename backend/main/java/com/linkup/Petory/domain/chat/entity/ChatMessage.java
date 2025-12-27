package com.linkup.Petory.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import com.linkup.Petory.domain.common.BaseTimeEntity;
import com.linkup.Petory.domain.user.entity.Users;

@Entity
@Table(name = "chatmessage")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_idx", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_idx", nullable = false)
    private Users sender;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_idx")
    private ChatMessage replyToMessage;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        // BaseTimeEntity가 createdAt, updatedAt을 자동 관리하므로 여기서는 기본값만 설정
        if (this.messageType == null) {
            this.messageType = MessageType.TEXT;
        }
        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
    }
}
