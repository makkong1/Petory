package com.linkup.Petory.entity;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class MeetupParticipantsId implements Serializable {

    private Long meetup;
    private Long user;
}
