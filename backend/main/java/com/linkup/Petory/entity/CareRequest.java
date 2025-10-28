package com.linkup.Petory.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "care_request")
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

    @OneToMany(mappedBy = "careRequest", cascade = CascadeType.ALL)
    private List<CareApplication> applications;
}
