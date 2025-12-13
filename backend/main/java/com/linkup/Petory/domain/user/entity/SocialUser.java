package com.linkup.Petory.domain.user.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "socialuser")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne
    @JoinColumn(name = "users_idx", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 25)
    private Provider provider;

    @Column(nullable = false)
    private String providerId;

    // Provider별 상세 정보 필드
    @Lob
    @Column(name = "provider_data", columnDefinition = "TEXT")
    private String providerData; // Provider별 원본 데이터 JSON (모든 OAuth2 응답 데이터 저장)

    @Column(name = "provider_profile_image", length = 500)
    private String providerProfileImage; // Provider별 프로필 이미지 URL

    @Column(name = "provider_name", length = 100)
    private String providerName; // Provider별 이름 (구글: given_name + family_name, 네이버: name)

    @Column(name = "provider_phone", length = 50)
    private String providerPhone; // Provider별 전화번호 (네이버: mobile 또는 mobile_e164)

    @Column(name = "provider_age_range", length = 20)
    private String providerAgeRange; // Provider별 나이대 (네이버: 20-29 형식)

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