package com.linkup.Petory.domain.meetup.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.meetup.entity.Meetup;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

        // 반경 기반 모임 조회 (Haversine 공식 사용)
        // 거리만 계산하고, 날짜/상태 필터는 Java에서 처리
        @Query(value = "SELECT m.* FROM meetup m " +
                        "WHERE m.latitude IS NOT NULL AND m.longitude IS NOT NULL " +
                        "AND (6371 * acos(cos(radians(:lat)) * cos(radians(m.latitude)) * " +
                        "cos(radians(m.longitude) - radians(:lng)) + " +
                        "sin(radians(:lat)) * sin(radians(m.latitude)))) <= :radius " +
                        "ORDER BY (6371 * acos(cos(radians(:lat)) * cos(radians(m.latitude)) * " +
                        "cos(radians(m.longitude) - radians(:lng)) + " +
                        "sin(radians(:lat)) * sin(radians(m.latitude)))) ASC, m.date ASC", nativeQuery = true)
        List<Meetup> findNearbyMeetups(@Param("lat") Double lat,
                        @Param("lng") Double lng,
                        @Param("radius") Double radius);

        // ✅ Pessimistic Lock으로 동시 접근 방지 (Race Condition 해결)
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("SELECT m FROM Meetup m WHERE m.idx = :idx")
        Optional<Meetup> findByIdWithLock(@Param("idx") Long idx);
}
