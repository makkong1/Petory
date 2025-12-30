package com.linkup.Petory.domain.care.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;

import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.Users;

import com.linkup.Petory.domain.common.BaseTimeEntity;

/**
 * 펫케어 요청 엔티티
 * 역할: 펫케어 요청을 나타내는 핵심 엔티티입니다. 반려동물 돌봄이 필요한 사용자가 서비스 제공자를 모집하기 위해 생성하는 게시물입니다.
 * 요청자는 제목, 설명, 날짜, 관련 펫 정보를 포함하여 요청을 생성하며, 상태는 OPEN → IN_PROGRESS → COMPLETED로
 * 전이됩니다.
 * 하나의 요청에는 여러 지원(CareApplication)과 댓글(CareRequestComment)이 연결될 수 있습니다.
 */
@Entity
@Table(name = "carerequest")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareRequest extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user; // 요청자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pet_idx")
    private Pet pet; // 관련 펫 (선택사항)

    private String title;

    @Lob
    private String description;

    private LocalDateTime date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CareRequestStatus status = CareRequestStatus.OPEN;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "careRequest", cascade = CascadeType.ALL)
    private List<CareApplication> applications;

    @OneToMany(mappedBy = "careRequest", cascade = CascadeType.ALL)
    private List<CareRequestComment> comments;

}
