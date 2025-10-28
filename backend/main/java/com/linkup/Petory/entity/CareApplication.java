package com.linkup.Petory.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;

@Entity
@Table(name = "care_application")
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

    @OneToMany(mappedBy = "careApplication", cascade = CascadeType.ALL)
    private List<CareReview> reviews;
}
