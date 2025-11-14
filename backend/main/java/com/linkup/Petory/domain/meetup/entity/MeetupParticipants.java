package com.linkup.Petory.domain.meetup.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

import com.linkup.Petory.domain.user.entity.Users;

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
    @ManyToOne
    @JoinColumn(name = "meetup_idx", nullable = false)
    private Meetup meetup;

    @Id
    @ManyToOne
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user;

    private LocalDateTime joinedAt;

    @PrePersist
    protected void onCreate() {
        this.joinedAt = LocalDateTime.now();
    }
}
