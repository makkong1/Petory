package com.linkup.Petory.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "carereview")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne
    @JoinColumn(name = "care_application_idx", nullable = false)
    private CareApplication careApplication;

    @ManyToOne
    @JoinColumn(name = "reviewer_idx", nullable = false)
    private Users reviewer;

    @ManyToOne
    @JoinColumn(name = "reviewee_idx", nullable = false)
    private Users reviewee;

    @Column(nullable = false)
    private int rating;

    @Lob
    private String comment;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

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
