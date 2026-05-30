package com.linkup.Petory.domain.petRecommendation.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class CommunityPostCreatedEvent extends ApplicationEvent {
    private final Long userIdx;
    private final Long postId;
    private final String text;

    public CommunityPostCreatedEvent(Object source, Long userIdx, Long postId, String text) {
        super(source);
        this.userIdx = userIdx;
        this.postId  = postId;
        this.text    = text;
    }
}
