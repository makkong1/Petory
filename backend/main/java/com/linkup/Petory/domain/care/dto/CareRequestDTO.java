package com.linkup.Petory.domain.care.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.linkup.Petory.domain.care.entity.CareScheduleMode;
import com.linkup.Petory.domain.user.dto.PetDTO;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareRequestDTO {

    private Long idx;
    @NotBlank
    private String title;
    @NotBlank
    private String description;
    @NotNull
    private LocalDateTime date;

    /**
     * null 이면 서버에서 {@link CareScheduleMode#FIXED} 로 처리
     */
    private CareScheduleMode scheduleMode;

    /**
     * 예상 이용 시간(분). 선택 — 15~1440분
     */
    @Min(15)
    @Max(1440)
    private Integer estimatedDurationMinutes;

    // 최소 금액을 둔다. 1 코인짜리 요청은 사실상 무료라, 사람을 모아놓고 금액을 바닥까지
    // 내리는 경로가 열린다(금액 수정은 OPEN 인 동안 가능하다).
    @NotNull
    @Min(100)
    private Integer offeredCoins;
    private String status; // OPEN, IN_PROGRESS, COMPLETED, CANCELLED
    private LocalDateTime createdAt;
    private Boolean deleted;
    private LocalDateTime deletedAt;

    /** 이행 완료 확인 시각. 어느 쪽이 아직 안 눌렀는지 화면이 알아야 "상대 확인 대기"를 보여줄 수 있다. */
    private LocalDateTime requesterCompletedAt;
    private LocalDateTime providerCompletedAt;

    /** 제시 금액이 마지막으로 바뀐 시각. 화면이 "요청자가 금액을 변경했습니다"를 보여줄 근거. */
    private LocalDateTime offeredCoinsUpdatedAt;

    // 위치 정보
    private Double latitude;
    private Double longitude;
    private String address;

    // 요청자 정보
    private Long userId;
    private String username;
    private String userLocation;

    // 관련 펫 정보 (선택사항)
    private Long petIdx;
    private PetDTO pet;

    // 지원자 정보
    private List<CareApplicationDTO> applications;
    private Integer applicationCount;

    // 댓글 정보
    private List<CareRequestCommentDTO> comments;
    private Integer commentCount;
}
