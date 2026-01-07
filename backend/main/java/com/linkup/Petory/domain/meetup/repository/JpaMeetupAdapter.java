package com.linkup.Petory.domain.meetup.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.meetup.entity.Meetup;

import lombok.RequiredArgsConstructor;

/**
 * MeetupRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaMeetupAdapter implements MeetupRepository {

    private final SpringDataJpaMeetupRepository jpaRepository;

    @Override
    public Meetup save(Meetup meetup) {
        return jpaRepository.save(meetup);
    }

    @Override
    public Optional<Meetup> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(Meetup meetup) {
        jpaRepository.delete(meetup);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
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
    public List<Meetup> findByDateBetweenOrderByDateAsc(LocalDateTime startDate, LocalDateTime endDate) {
        return jpaRepository.findByDateBetweenOrderByDateAsc(startDate, endDate);
    }

    @Override
    public List<Meetup> findByKeyword(String keyword) {
        return jpaRepository.findByKeyword(keyword);
    }

    @Override
    public List<Meetup> findAvailableMeetups(LocalDateTime currentDate) {
        return jpaRepository.findAvailableMeetups(currentDate);
    }

    @Override
    public List<Meetup> findNearbyMeetups(Double lat, Double lng, Double radius) {
        return jpaRepository.findNearbyMeetups(lat, lng, radius);
    }

    @Override
    public Optional<Meetup> findByIdWithLock(Long idx) {
        return jpaRepository.findByIdWithLock(idx);
    }

    @Override
    public int incrementParticipantsIfAvailable(Long meetupIdx) {
        return jpaRepository.incrementParticipantsIfAvailable(meetupIdx);
    }

    @Override
    public List<Meetup> findAllNotDeleted() {
        return jpaRepository.findAllNotDeleted();
    }

    @Override
    public Optional<Meetup> findByIdWithDetails(Long idx) {
        return jpaRepository.findByIdWithDetails(idx);
    }
}

