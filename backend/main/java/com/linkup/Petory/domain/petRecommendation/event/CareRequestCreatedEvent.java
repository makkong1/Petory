package com.linkup.Petory.domain.petRecommendation.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class CareRequestCreatedEvent extends ApplicationEvent {
    private final Long userIdx;
    private final Long careRequestId;
    private final String text;

    public CareRequestCreatedEvent(Object source, Long userIdx, Long careRequestId, String text) {
        super(source);
        this.userIdx       = userIdx;
        this.careRequestId = careRequestId;
        this.text          = text;
    }
}
