package com.linkup.Petory.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

import com.linkup.Petory.domain.common.BaseTimeEntity;

/**
 * 애완동물 예방접종 기록 엔티티
 */
@Entity
@Table(name = "pet_vaccinations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class PetVaccination extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_idx", nullable = false)
    private Pet pet; // 소유 펫

    @Column(name = "vaccine_name", length = 200, nullable = false)
    private String vaccineName; // 백신 이름

    @Column(name = "vaccinated_at")
    private LocalDate vaccinatedAt; // 접종일

    @Column(name = "next_due")
    private LocalDate nextDue; // 다음 접종 예정일

    @Column(name = "notes", length = 500)
    private String notes; // 메모

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

}
