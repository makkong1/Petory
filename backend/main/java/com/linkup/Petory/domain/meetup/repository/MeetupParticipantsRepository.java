package com.linkup.Petory.domain.meetup.repository;

import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.meetup.entity.MeetupParticipants;
import com.linkup.Petory.domain.meetup.entity.MeetupParticipantsId;

/**
 * MeetupParticipants 도메인 Repository 인터페이스입니다.
 */
public interface MeetupParticipantsRepository {

    // 기본 CRUD 메서드
    MeetupParticipants save(MeetupParticipants participant);

    Optional<MeetupParticipants> findById(MeetupParticipantsId id);

    void delete(MeetupParticipants participant);

    void deleteById(MeetupParticipantsId id);

    /**
     * 특정 모임의 참여자 목록
     */
    List<MeetupParticipants> findByMeetupIdxOrderByJoinedAtAsc(Long meetupIdx);

    /**
     * 특정 사용자가 참여한 모임 목록
     */
    List<MeetupParticipants> findByUserIdxOrderByJoinedAtDesc(Long userIdx);

    /**
     * 특정 모임의 참여자 수
     */
    Long countByMeetupIdx(Long meetupIdx);

    /**
     * 특정 사용자가 특정 모임에 참여했는지 확인
     */
    boolean existsByMeetupIdxAndUserIdx(Long meetupIdx, Long userIdx);

    /**
     * 특정 모임의 참여자 조회
     */
    Optional<MeetupParticipants> findByMeetupIdxAndUserIdx(Long meetupIdx, Long userIdx);

    /**
     * 특정 사용자가 참여한 모임 중 진행 예정인 모임들
     */
    List<MeetupParticipants> findUpcomingMeetupsByUser(Long userIdx);
}
