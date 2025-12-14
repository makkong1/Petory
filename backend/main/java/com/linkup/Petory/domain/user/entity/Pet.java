package com.linkup.Petory.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.linkup.Petory.domain.common.BaseTimeEntity;

/**
 * 애완동물 엔티티
 * - 사용자가 등록한 반려동물 정보
 * - 펫케어 요청, 실종 제보 등에서 재사용
 */
@Entity
@Table(name = "pets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Pet extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user; // 소유자

    @Column(name = "pet_name", length = 50, nullable = false)
    private String petName; // 애완동물 이름

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PetType petType; // 종류 (강아지, 고양이, 기타)

    @Column(length = 50)
    private String breed; // 품종 (예: 골든 리트리버, 페르시안 등)

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private PetGender gender; // 성별 (M: 수컷, F: 암컷, UNKNOWN: 미확인)

    @Column(length = 30)
    private String age; // 나이 (예: "3살", "5개월" 등) - birthDate가 있으면 계산 가능

    @Column(length = 50)
    private String color; // 색상/털색

    @Column(name = "weight", precision = 5, scale = 2)
    private BigDecimal weight; // 몸무게 (kg) - DECIMAL(5,2)로 저장

    @Column(name = "is_neutered", nullable = false)
    @Builder.Default
    private Boolean isNeutered = false; // 중성화 여부

    @Column(name = "birth_date")
    private LocalDate birthDate; // 생년월일 (선택사항) - 있으면 나이 계산 가능

    @Lob
    @Column(name = "health_info")
    private String healthInfo; // 건강 정보 (질병, 알레르기, 특이사항 등)

    @Lob
    @Column(name = "special_notes")
    private String specialNotes; // 특이사항 (성격, 주의사항 등)

    @Column(name = "profile_image_url", length = 500)
    private String profileImageUrl; // 프로필 사진 URL

    // 소프트 삭제
    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "pet", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<PetVaccination> vaccinations; // 예방접종 기록 목록

}
