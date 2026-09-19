package com.linkup.Petory.domain.care.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 제안 후보 검색 결과.
 *
 * <p>목록만 돌려주면 화면이 두 가지를 구분하지 못한다 — <b>이 구에 제공자가 없어서</b> 넓혀
 * 찾은 것인지, 원래 이 구 사람들인지. 그래서 찾은 범위를 같이 싣는다.
 *
 * <p>{@code total} 과 {@code providers} 가 다른 이유는 개수를 자르기 때문이다. 더미 데이터
 * 기준으로 한 구에 제공자가 242명까지 나오는데, 전부 내려보내면 패널이 스크롤로 뒤덮인다.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CareProviderSearchDTO {

    /** 찾은 범위. 화면 문구가 갈린다. */
    private String scope;          // DISTRICT(같은 구) / WIDE(같은 시·도) / NONE

    /** 화면에 보여줄 범위 이름 ("중랑구" / "서울"). */
    private String areaLabel;

    /** 자르기 전 전체 후보 수. */
    private int total;

    private List<CareProviderDTO> providers;
}
