package com.linkup.Petory.domain.board.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.linkup.Petory.domain.user.entity.Users;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "MissingPetBoard")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MissingPetBoard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long idx;

    @ManyToOne
    @JoinColumn(name = "user_idx", nullable = false)
    private Users user;

    @Column(length = 100, nullable = false)
    private String title;

    @Lob
    private String content;

    @Column(name = "pet_name", length = 50)
    private String petName;

    @Column(length = 50)
    private String species;

    @Column(length = 50)
    private String breed;

    @Enumerated(EnumType.STRING)
    private MissingPetGender gender;

    @Column(length = 30)
    private String age;

    @Column(length = 50)
    private String color;

    @Column(name = "lost_date")
    private LocalDate lostDate;

    @Column(name = "lost_location", length = 255)
    private String lostLocation;

    @Column(precision = 15, scale = 12)
    private BigDecimal latitude;

    @Column(precision = 15, scale = 12)
    private BigDecimal longitude;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MissingPetStatus status = MissingPetStatus.MISSING;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<MissingPetComment> comments = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = MissingPetStatus.MISSING;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
