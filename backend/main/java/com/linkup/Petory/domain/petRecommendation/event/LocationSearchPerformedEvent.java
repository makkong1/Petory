package com.linkup.Petory.domain.petRecommendation.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

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
