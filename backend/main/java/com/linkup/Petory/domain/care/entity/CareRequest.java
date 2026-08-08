package com.linkup.Petory.domain.care.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.linkup.Petory.domain.common.BaseTimeEntity;
import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.Users;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 펫케어 요청 엔티티 역할: 펫케어 요청을 나타내는 핵심 엔티티입니다. 반려동물 돌봄이 필요한 사용자가 서비스 제공자를 모집하기 위해
 * 생성하는 게시물입니다. 요청자는 제목, 설명, 날짜, 관련 펫 정보를 포함하여 요청을 생성하며, 상태는 OPEN → IN_PROGRESS
 * → COMPLETED로 전이됩니다. 하나의 요청에는 여러 지원(CareApplication)과 댓글(CareRequestComment)이
 * 연결될 수 있습니다.
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

    @Column(columnDefinition = "LONGTEXT")
    private String description;

    private LocalDateTime date;

    /**
     * 일정이 위 date에 고정되는지, 채팅 후 조율인지
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "schedule_mode", nullable = false, length = 32)
    @Builder.Default
    private CareScheduleMode scheduleMode = CareScheduleMode.FIXED;

    /**
     * 예상 돌봄 소요 시간(분). 선택.
     */
    @Column(name = "estimated_duration_minutes")
    private Integer estimatedDurationMinutes;

    @Column(name = "offered_coins")
    private Integer offeredCoins; // 제시한 코인 가격 (요청자가 설정)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CareRequestStatus status = CareRequestStatus.OPEN;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // [FIX] 케어 완료 시각 — 통계 집계용. CareRequest.date(케어 예정일)와 구분
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /** 요청자가 이행 완료를 확인한 시각. NULL 이면 미확인. */
    @Column(name = "requester_completed_at")
    private LocalDateTime requesterCompletedAt;

    /** 제공자가 이행 완료를 확인한 시각. NULL 이면 미확인. */
    @Column(name = "provider_completed_at")
    private LocalDateTime providerCompletedAt;

    /**
     * 제시 금액이 마지막으로 바뀐 시각. NULL 이면 등록 이후 변경 없음.
     *
     * 낡은 거래 확정을 가려내는 판정에는 쓰지 않는다 — 처음엔 이 시각과 확정 시각을 비교했는데,
     * 둘 다 `datetime`(초 단위)이라 같은 초에 일어난 변경·확정을 구분하지 못했다(V15 참고).
     * 지금은 참여자가 동의한 금액을 직접 들고 비교한다. 이 컬럼은 표시용으로 남긴다.
     */
    @Column(name = "offered_coins_updated_at")
    private LocalDateTime offeredCoinsUpdatedAt;

    /** 제시 금액을 바꾸고 변경 시각을 남긴다. */
    public void changeOfferedCoins(int newAmount) {
        this.offeredCoins = newAmount;
        this.offeredCoinsUpdatedAt = LocalDateTime.now();
    }

    /**
     * 한쪽의 이행 완료 확인을 기록한다. 이미 확인했다면 시각을 덮어쓰지 않는다
     * (재시도로 같은 요청이 두 번 와도 결과가 같아야 하므로).
     *
     * @return 이번 호출로 새로 기록됐으면 true, 이미 확인 상태였으면 false
     */
    public boolean confirmCompletionBy(boolean isRequester) {
        if (isRequester) {
            if (this.requesterCompletedAt != null) {
                return false;
            }
            this.requesterCompletedAt = LocalDateTime.now();
        } else {
            if (this.providerCompletedAt != null) {
                return false;
            }
            this.providerCompletedAt = LocalDateTime.now();
        }
        return true;
    }

    /** 양쪽이 모두 이행 완료를 확인했는가. 정산은 이 조건에서만 일어난다. */
    public boolean isBothCompletionConfirmed() {
        return this.requesterCompletedAt != null && this.providerCompletedAt != null;
    }

    public void transitionTo(CareRequestStatus newStatus) {
        // 같은 상태로의 재요청은 무해한 no-op — 재시도가 에러가 되지 않게 둔다.
        if (newStatus == this.status) {
            return;
        }
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                    "허용되지 않는 상태 전이입니다: " + this.status + " -> " + newStatus);
        }
        if (newStatus == CareRequestStatus.COMPLETED) {
            this.completedAt = LocalDateTime.now();
        }
        this.status = newStatus;
    }

    public void softDelete() {
        this.isDeleted = true;
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.isDeleted = false;
        this.deletedAt = null;
    }

    /**
     * 펫케어 지원 목록 (서비스 제공자들의 지원)
     */
    @OneToMany(mappedBy = "careRequest", cascade = CascadeType.ALL)
    @BatchSize(size = 50)  // 페이징 목록 조회 시 CareApplication N+1 방지
    private List<CareApplication> applications;

    /**
     * 펫케어 요청 댓글 목록
     */
    @OneToMany(mappedBy = "careRequest", cascade = CascadeType.ALL)
    private List<CareRequestComment> comments;

}
