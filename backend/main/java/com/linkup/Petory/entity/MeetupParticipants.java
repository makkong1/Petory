package com.linkup.Petory.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

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
