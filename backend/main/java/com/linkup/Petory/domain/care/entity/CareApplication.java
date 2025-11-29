package com.linkup.Petory.domain.care.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

import com.linkup.Petory.domain.user.entity.Users;

@Entity
@Table(name = "careapplication")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne
    @JoinColumn(name = "care_request_idx", nullable = false)
    private CareRequest careRequest;

    @ManyToOne
    @JoinColumn(name = "provider_idx", nullable = false)
    private Users provider; // 케어 제공자

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CareApplicationStatus status = CareApplicationStatus.PENDING;

    @Lob
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "careApplication", cascade = CascadeType.ALL)
    private List<CareReview> reviews;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
