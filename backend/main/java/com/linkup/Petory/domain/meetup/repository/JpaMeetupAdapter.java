package com.linkup.Petory.domain.meetup.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.meetup.entity.Meetup;
import com.linkup.Petory.domain.meetup.entity.MeetupStatus;

import lombok.RequiredArgsConstructor;

/**
 * MeetupRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaMeetupAdapter implements MeetupRepository {

    private final SpringDataJpaMeetupRepository jpaRepository;

    @SuppressWarnings("null")
    @Override
    public Meetup save(Meetup meetup) {
        return jpaRepository.save(meetup);
    }

    @SuppressWarnings("null")
    @Override
    public Optional<Meetup> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Meetup> findByOrganizerIdxOrderByCreatedAtDesc(Long organizerIdx) {
        return jpaRepository.findByOrganizerIdxOrderByCreatedAtDesc(organizerIdx);
    }

    @Override
    public List<Meetup> findByLocationRange(
            Double minLat,
            Double maxLat,
            Double minLng,
            Double maxLng) {
        return jpaRepository.findByLocationRange(minLat, maxLat, minLng, maxLng);
    }

    @Override
    public List<Meetup> findByKeyword(String keyword) {
        return jpaRepository.findByKeyword(keyword);
    }

    @Override
    public List<Meetup> findAvailableMeetups(LocalDateTime currentDate, MeetupStatus recruiting, Pageable pageable) {
        return jpaRepository.findAvailableMeetups(currentDate, recruiting, pageable);
    }

    @Override
    public List<Long> findNearbyMeetupIds(Double lat, Double lng, Double radius, LocalDateTime currentDate, int limit) {
        return jpaRepository.findNearbyMeetupIds(lat, lng, radius, currentDate, limit);
    }

    @Override
    public List<Meetup> findByIdxInWithOrganizer(Collection<Long> ids) {
        return jpaRepository.findByIdxInWithOrganizer(ids);
    }

    @Override
    public Optional<Meetup> findByIdWithOrganizer(Long idx) {
        return jpaRepository.findByIdWithOrganizer(idx);
    }

    @Override
    public Optional<Meetup> findByIdWithLock(Long idx) {
        return jpaRepository.findByIdWithLock(idx);
    }

    @Override
    public int incrementParticipantsIfAvailable(Long meetupIdx, MeetupStatus recruiting) {
        return jpaRepository.incrementParticipantsIfAvailable(meetupIdx, recruiting);
    }

    @Override
    public int decrementParticipantsIfPositive(Long meetupIdx) {
        return jpaRepository.decrementParticipantsIfPositive(meetupIdx);
    }

    @Override
    public List<Meetup> findAllNotDeleted() {
        return jpaRepository.findAllNotDeleted();
    }

    @Override
    public Page<Meetup> findAllNotDeleted(Pageable pageable) {
        return jpaRepository.findAllNotDeleted(pageable);
    }

    @Override
    public Page<Meetup> findAllForAdmin(String status, String keyword, Pageable pageable) {
        if (keyword != null && !keyword.isBlank()) {
            return jpaRepository.findAllForAdminWithKeyword(status, keyword, pageable);
        }
        return jpaRepository.findAllForAdmin(status, pageable);
    }

    @Override
    public Optional<Meetup> findByIdWithDetails(Long idx) {
        return jpaRepository.findByIdWithDetails(idx);
    }

    @Override
    public long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end) {
        return jpaRepository.countByCreatedAtBetween(start, end);
    }

    @Override
    public int closeFullRecruitingMeetups(LocalDateTime now) {
        return jpaRepository.closeFullRecruitingMeetups(now, MeetupStatus.RECRUITING, MeetupStatus.CLOSED);
    }

    @Override
    public int completePastMeetups(LocalDateTime now) {
        return jpaRepository.completePastMeetups(now, MeetupStatus.COMPLETED);
    }

    @Override
    public List<Meetup> findWithoutChatRoom() {
        return jpaRepository.findWithoutChatRoom();
    }
}
