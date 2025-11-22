package com.linkup.Petory.domain.meetup.entity;

import com.linkup.Petory.domain.user.entity.Users;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "meetup")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meetup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @Column(nullable = false, length = 200)
    private String title; // 모임 제목

    @Lob
    private String description; // 모임 내용

    private String location; // 모임 장소 주소

    private Double latitude; // 위도

    private Double longitude; // 경도

    @Column(nullable = false)
    private LocalDateTime date; // 모임 일시

    @ManyToOne
    @JoinColumn(name = "organizer_idx", nullable = false)
    private Users organizer; // 모임 주최자

    @Builder.Default
    private Integer maxParticipants = 10; // 최대 참여 인원

    @Builder.Default
    private Integer currentParticipants = 0; // 현재 참여자 수

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(length = 20)
    private MeetupStatus status = MeetupStatus.RECRUITING; // 모임 상태

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "meetup", cascade = CascadeType.ALL)
    private List<MeetupParticipants> participants;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
