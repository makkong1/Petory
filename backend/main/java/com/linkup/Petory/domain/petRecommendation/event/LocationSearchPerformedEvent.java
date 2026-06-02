package com.linkup.Petory.domain.petRecommendation.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/** 위치 검색 키워드 기반 NLP signal 분석 트리거 이벤트. */
@Getter
public class LocationSearchPerformedEvent extends ApplicationEvent {
    private final Long userIdx;
    private final String keyword;

    public LocationSearchPerformedEvent(Object source, Long userIdx, String keyword) {
        super(source);
        this.userIdx  = userIdx;
        this.keyword  = keyword;
    }
}
