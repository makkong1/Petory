package com.linkup.Petory.domain.meetup.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.meetup.entity.MeetupParticipants;
import com.linkup.Petory.domain.meetup.entity.MeetupParticipantsId;

import lombok.RequiredArgsConstructor;

/**
 * MeetupParticipantsRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaMeetupParticipantsAdapter implements MeetupParticipantsRepository {

    private final SpringDataJpaMeetupParticipantsRepository jpaRepository;

    @Override
    public MeetupParticipants save(MeetupParticipants participant) {
        return jpaRepository.save(participant);
    }

    @Override
    public Optional<MeetupParticipants> findById(MeetupParticipantsId id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(MeetupParticipants participant) {
        jpaRepository.delete(participant);
    }

    @Override
    public void deleteById(MeetupParticipantsId id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<MeetupParticipants> findByMeetupIdxOrderByJoinedAtAsc(Long meetupIdx) {
        return jpaRepository.findByMeetupIdxOrderByJoinedAtAsc(meetupIdx);
    }

    @Override
    public List<MeetupParticipants> findByUserIdxOrderByJoinedAtDesc(Long userIdx) {
        return jpaRepository.findByUserIdxOrderByJoinedAtDesc(userIdx);
    }

    @Override
    public Long countByMeetupIdx(Long meetupIdx) {
        return jpaRepository.countByMeetupIdx(meetupIdx);
    }

    @Override
    public boolean existsByMeetupIdxAndUserIdx(Long meetupIdx, Long userIdx) {
        return jpaRepository.existsByMeetupIdxAndUserIdx(meetupIdx, userIdx);
    }

    @Override
    public Optional<MeetupParticipants> findByMeetupIdxAndUserIdx(Long meetupIdx, Long userIdx) {
        return jpaRepository.findByMeetupIdxAndUserIdx(meetupIdx, userIdx);
    }

    @Override
    public List<MeetupParticipants> findUpcomingMeetupsByUser(Long userIdx) {
        return jpaRepository.findUpcomingMeetupsByUser(userIdx);
    }
}

