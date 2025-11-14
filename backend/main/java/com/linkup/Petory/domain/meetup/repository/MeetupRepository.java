package com.linkup.Petory.domain.meetup.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.meetup.entity.Meetup;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MeetupRepository extends JpaRepository<Meetup, Long> {

        // 주최자별 모임 조회
        List<Meetup> findByOrganizerIdxOrderByCreatedAtDesc(Long organizerIdx);

        // 지역별 모임 조회 (위도/경도 범위)
        @Query("SELECT m FROM Meetup m WHERE " +
                        "m.latitude BETWEEN :minLat AND :maxLat AND " +
                        "m.longitude BETWEEN :minLng AND :maxLng " +
                        "ORDER BY m.date ASC")
        List<Meetup> findByLocationRange(@Param("minLat") Double minLat,
                        @Param("maxLat") Double maxLat,
                        @Param("minLng") Double minLng,
                        @Param("maxLng") Double maxLng);

        // 날짜별 모임 조회
        List<Meetup> findByDateBetweenOrderByDateAsc(LocalDateTime startDate, LocalDateTime endDate);

        // 카테고리별 모임 조회 (제목이나 설명에 키워드 포함)
        @Query("SELECT m FROM Meetup m WHERE " +
                        "m.title LIKE %:keyword% OR m.description LIKE %:keyword% " +
                        "ORDER BY m.date ASC")
        List<Meetup> findByKeyword(@Param("keyword") String keyword);

        // 참여 가능한 모임 조회 (최대 인원 미만)
        @Query("SELECT m FROM Meetup m WHERE " +
                        "m.maxParticipants > (SELECT COUNT(p) FROM MeetupParticipants p WHERE p.meetup.idx = m.idx) " +
                        "AND m.date > :currentDate " +
                        "ORDER BY m.date ASC")
        List<Meetup> findAvailableMeetups(@Param("currentDate") LocalDateTime currentDate);
}
