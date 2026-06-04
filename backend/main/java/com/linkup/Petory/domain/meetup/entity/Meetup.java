package com.linkup.Petory.domain.meetup.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.linkup.Petory.domain.common.BaseTimeEntity;
import com.linkup.Petory.domain.user.entity.Users;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 모임 엔티티. 생성 시 그룹 채팅방이 자동 생성되며, 인원 제한·상태(RECRUITING/CLOSED/COMPLETED)를 관리한다. */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Meetup extends BaseTimeEntity {

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

    @ManyToOne(fetch = FetchType.LAZY)
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

    // createdAt, updatedAt은 BaseTimeEntity에서 상속받음

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "meetup", cascade = CascadeType.ALL)
    @BatchSize(size = 50) // 목록 조회 시 participants N+1 방지
    private List<MeetupParticipants> participants;

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onCreate() {
        if (this.isDeleted == null) {
            this.isDeleted = false;
        }
    }
}
