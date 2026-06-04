package com.linkup.Petory.domain.petRecommendation.event;

import org.springframework.context.ApplicationEvent;

import lombok.Getter;

/**
 * 케어 요청 작성 직후 NLP signal 분석을 요청하기 위한 이벤트.
 */
@Getter
public class CareRequestCreatedEvent extends ApplicationEvent {

    private final Long userIdx;
    private final Long careRequestId;
    private final String text;

    public CareRequestCreatedEvent(Object source, Long userIdx, Long careRequestId, String text) {
        super(source);
        this.userIdx = userIdx;
        this.careRequestId = careRequestId;
        this.text = text;
    }

    public Long getUserIdx() {
        return userIdx;
    }

    public Long getCareRequestId() {
        return careRequestId;
    }

    public String getText() {
        return text;
    }
}
