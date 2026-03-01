package com.linkup.Petory.domain.meetup.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.meetup.entity.MeetupParticipants;
import com.linkup.Petory.domain.meetup.entity.MeetupParticipantsId;
import com.linkup.Petory.global.annotation.RepositoryMethod;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaMeetupParticipantsRepository extends JpaRepository<MeetupParticipants, MeetupParticipantsId> {

    @RepositoryMethod("모임 참여자: 모임별 목록 조회")
    @Query("SELECT mp FROM MeetupParticipants mp JOIN FETCH mp.user WHERE mp.meetup.idx = :meetupIdx ORDER BY mp.joinedAt ASC")
    List<MeetupParticipants> findByMeetupIdxOrderByJoinedAtAsc(@Param("meetupIdx") Long meetupIdx);

    @RepositoryMethod("모임 참여자: 사용자별 참여 모임 목록")
    @Query("SELECT mp FROM MeetupParticipants mp " +
           "JOIN FETCH mp.meetup m " +
           "JOIN FETCH mp.user u " +
           "WHERE mp.user.idx = :userIdx " +
           "ORDER BY mp.joinedAt DESC")
    List<MeetupParticipants> findByUserIdxOrderByJoinedAtDesc(@Param("userIdx") Long userIdx);

    @RepositoryMethod("모임 참여자: 모임별 참여자 수")
    Long countByMeetupIdx(Long meetupIdx);

    @RepositoryMethod("모임 참여자: 참여 여부 확인")
    boolean existsByMeetupIdxAndUserIdx(Long meetupIdx, Long userIdx);

    @RepositoryMethod("모임 참여자: 모임+사용자로 조회")
    Optional<MeetupParticipants> findByMeetupIdxAndUserIdx(Long meetupIdx, Long userIdx);

    @RepositoryMethod("모임 참여자: 사용자별 예정 모임 목록")
    @Query("SELECT mp FROM MeetupParticipants mp " +
            "JOIN mp.meetup m " +
            "WHERE mp.user.idx = :userIdx AND m.date > CURRENT_TIMESTAMP " +
            "ORDER BY m.date ASC")
    List<MeetupParticipants> findUpcomingMeetupsByUser(@Param("userIdx") Long userIdx);
}

