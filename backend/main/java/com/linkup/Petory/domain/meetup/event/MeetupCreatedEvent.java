package com.linkup.Petory.domain.meetup.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 모임 생성 완료 이벤트
 * 모임 생성이 성공적으로 완료된 후 발행되는 이벤트
 * 채팅방 생성 등 후처리 작업을 위해 사용
 */
@Getter
public class MeetupCreatedEvent extends ApplicationEvent {

    private final Long meetupIdx;
    private final Long organizerIdx;
    private final String meetupTitle;

    public MeetupCreatedEvent(Object source, Long meetupIdx, Long organizerIdx, String meetupTitle) {
        super(source);
        this.meetupIdx = meetupIdx;
        this.organizerIdx = organizerIdx;
        this.meetupTitle = meetupTitle;
    }
}
