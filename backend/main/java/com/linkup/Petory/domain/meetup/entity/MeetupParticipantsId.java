package com.linkup.Petory.domain.meetup.entity;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
/**
 * MeetupParticipants 복합 키 클래스. meetup ID + user ID 조합으로 유니크함을 보장한다.
 */
public class MeetupParticipantsId implements Serializable {

    private Long meetup;
    private Long user;
}
