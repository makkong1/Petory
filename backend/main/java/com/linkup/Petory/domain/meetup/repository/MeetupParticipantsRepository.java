package com.linkup.Petory.domain.meetup.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.meetup.entity.MeetupParticipants;
import com.linkup.Petory.domain.meetup.entity.MeetupParticipantsId;

import java.util.List;
import java.util.Optional;

@Repository
public interface MeetupParticipantsRepository extends JpaRepository<MeetupParticipants, MeetupParticipantsId> {

    // 특정 모임의 참여자 목록
    @Query("SELECT mp FROM MeetupParticipants mp WHERE mp.meetup.idx = :meetupIdx ORDER BY mp.joinedAt ASC")
    List<MeetupParticipants> findByMeetupIdxOrderByJoinedAtAsc(@Param("meetupIdx") Long meetupIdx);

    // 특정 사용자가 참여한 모임 목록
    List<MeetupParticipants> findByUserIdxOrderByJoinedAtDesc(Long userIdx);

    // 특정 모임의 참여자 수
    Long countByMeetupIdx(Long meetupIdx);

    // 특정 사용자가 특정 모임에 참여했는지 확인
    boolean existsByMeetupIdxAndUserIdx(Long meetupIdx, Long userIdx);

    // 특정 모임의 참여자 조회
    Optional<MeetupParticipants> findByMeetupIdxAndUserIdx(Long meetupIdx, Long userIdx);

    // 특정 사용자가 참여한 모임 중 진행 예정인 모임들
    @Query("SELECT mp FROM MeetupParticipants mp " +
            "JOIN mp.meetup m " +
            "WHERE mp.user.idx = :userIdx AND m.date > CURRENT_TIMESTAMP " +
            "ORDER BY m.date ASC")
    List<MeetupParticipants> findUpcomingMeetupsByUser(@Param("userIdx") Long userIdx);
}
