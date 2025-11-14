package com.linkup.Petory.domain.care.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

import com.linkup.Petory.domain.user.entity.Users;

@Entity
@Table(name = "carerequest")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user; // 요청자

    private String title;

    @Lob
    private String description;

    private LocalDateTime date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CareRequestStatus status = CareRequestStatus.OPEN;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "careRequest", cascade = CascadeType.ALL)
    private List<CareApplication> applications;

    @OneToMany(mappedBy = "careRequest", cascade = CascadeType.ALL)
    private List<CareRequestComment> comments;

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
