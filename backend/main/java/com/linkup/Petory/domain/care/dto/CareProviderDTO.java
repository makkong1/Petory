package com.linkup.Petory.domain.care.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 케어 제안 후보로 보여줄 제공자 한 명.
 *
 * <p>평점·완료건수는 <b>표시용</b>이다. 이걸로 줄 세우지 않는다 — 리뷰가 없는 신규 제공자가
 * 영영 안 뽑히기 때문이고, 쏠림이 실제로 관측된 뒤에 정렬을 넣는 게 순서다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareProviderDTO {

    private Long idx;
    private String username;
    private String nickname;
    private String location;

    private Double averageRating;   // 리뷰가 없으면 null
    private int reviewCount;
    private long completedCareCount;

    /** 이 요청으로 이미 제안을 보낸 상대인가. 화면에서 버튼을 잠그는 데 쓴다. */
    private boolean alreadyOffered;
}
