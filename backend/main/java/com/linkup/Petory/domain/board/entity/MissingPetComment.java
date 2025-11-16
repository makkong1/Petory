package com.linkup.Petory.domain.board.entity;

import java.time.LocalDateTime;

import com.linkup.Petory.domain.common.ContentStatus;
import com.linkup.Petory.domain.user.entity.Users;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "MissingPetComment")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissingPetComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne
    @JoinColumn(name = "board_idx", nullable = false)
    private MissingPetBoard board;

    @ManyToOne
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user;

    @Lob
    private String content;

    private String address; // 목격 위치 주소

    private Double latitude; // 목격 위치 위도

    private Double longitude; // 목격 위치 경도

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private ContentStatus status = ContentStatus.ACTIVE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = ContentStatus.ACTIVE;
        }
    }
}
