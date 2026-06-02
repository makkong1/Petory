package com.linkup.Petory.domain.petRecommendation.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/** 커뮤니티 게시글 작성 직후 NLP signal 분석을 요청하기 위한 이벤트. */
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
