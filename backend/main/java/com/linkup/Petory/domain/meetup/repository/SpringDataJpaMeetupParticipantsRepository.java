package com.linkup.Petory.domain.meetup.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.meetup.entity.MeetupParticipants;
import com.linkup.Petory.domain.meetup.entity.MeetupParticipantsId;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaMeetupParticipantsRepository extends JpaRepository<MeetupParticipants, MeetupParticipantsId> {

    // 특정 모임의 참여자 목록
    @Query("SELECT mp FROM MeetupParticipants mp WHERE mp.meetup.idx = :meetupIdx ORDER BY mp.joinedAt ASC")
    List<MeetupParticipants> findByMeetupIdxOrderByJoinedAtAsc(@Param("meetupIdx") Long meetupIdx);

    // 특정 사용자가 참여한 모임 목록 (JOIN FETCH 적용 - N+1 쿼리 해결)
    @Query("SELECT mp FROM MeetupParticipants mp " +
           "JOIN FETCH mp.meetup m " +
           "JOIN FETCH mp.user u " +
           "WHERE mp.user.idx = :userIdx " +
           "ORDER BY mp.joinedAt DESC")
    List<MeetupParticipants> findByUserIdxOrderByJoinedAtDesc(@Param("userIdx") Long userIdx);

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

