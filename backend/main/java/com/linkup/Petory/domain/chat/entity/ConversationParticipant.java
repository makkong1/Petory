package com.linkup.Petory.domain.chat.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

import com.linkup.Petory.domain.user.entity.Users;

@Entity
@Table(name = "conversation_participant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_idx", nullable = false)
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    @Builder.Default
    private ParticipantRole role = ParticipantRole.MEMBER;

    @Column(name = "unread_count", nullable = false)
    @Builder.Default
    private Integer unreadCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_message_idx")
    private ChatMessage lastReadMessage;

    @Column(name = "last_read_at")
    private LocalDateTime lastReadAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ParticipantStatus status = ParticipantStatus.ACTIVE;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.joinedAt = LocalDateTime.now();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.role == null) {
            this.role = ParticipantRole.MEMBER;
        }
        if (this.unreadCount == null) {
            this.unreadCount = 0;
        }
        if (this.status == null) {
            this.status = ParticipantStatus.ACTIVE;
        }
        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // 읽지 않은 메시지 수 증가
    public void incrementUnreadCount() {
        if (this.unreadCount == null) {
            this.unreadCount = 0;
        }
        this.unreadCount++;
    }

    // 읽지 않은 메시지 수 감소 (0 이하로 내려가지 않음)
    public void decrementUnreadCount() {
        if (this.unreadCount != null && this.unreadCount > 0) {
            this.unreadCount--;
        }
    }
}

