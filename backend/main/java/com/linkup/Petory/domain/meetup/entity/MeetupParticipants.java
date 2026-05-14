package com.linkup.Petory.domain.meetup.entity;

import java.time.LocalDateTime;

import com.linkup.Petory.domain.user.entity.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "meetupparticipants")
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
