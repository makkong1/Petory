package com.linkup.Petory.domain.meetup.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

import com.linkup.Petory.domain.user.entity.Users;

@Entity
@Table(name = "meetupparticipants", indexes = {
    @Index(name = "user_idx",                                 columnList = "user_idx"),
    @Index(name = "idx_meetupparticipants_user_liked_joined", columnList = "user_idx, liked, joined_at")
})
@IdClass(MeetupParticipantsId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MeetupParticipants {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meetup_idx", nullable = false)
    private Meetup meetup;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user;

    private LocalDateTime joinedAt;

    @Builder.Default
    @Column(nullable = false)
    private Boolean liked = false;

    @PrePersist
    protected void onCreate() {
        this.joinedAt = LocalDateTime.now();
        if (this.liked == null) {
            this.liked = false;
        }
    }
}
