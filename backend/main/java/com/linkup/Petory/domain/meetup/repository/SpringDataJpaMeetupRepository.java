package com.linkup.Petory.domain.meetup.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.meetup.entity.Meetup;

import jakarta.persistence.LockModeType;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaMeetupRepository extends JpaRepository<Meetup, Long> {

    // 주최자별 모임 조회 (소프트 삭제 제외)
    @Query("SELECT m FROM Meetup m WHERE m.organizer.idx = :organizerIdx AND (m.isDeleted = false OR m.isDeleted IS NULL) ORDER BY m.createdAt DESC")
    List<Meetup> findByOrganizerIdxOrderByCreatedAtDesc(@Param("organizerIdx") Long organizerIdx);

    // 지역별 모임 조회 (위도/경도 범위, 소프트 삭제 제외)
    @Query("SELECT m FROM Meetup m WHERE " +
                    "m.latitude BETWEEN :minLat AND :maxLat AND " +
                    "m.longitude BETWEEN :minLng AND :maxLng AND " +
                    "(m.isDeleted = false OR m.isDeleted IS NULL) " +
                    "ORDER BY m.date ASC")
    List<Meetup> findByLocationRange(@Param("minLat") Double minLat,
                    @Param("maxLat") Double maxLat,
                    @Param("minLng") Double minLng,
                    @Param("maxLng") Double maxLng);

    // 날짜별 모임 조회 (소프트 삭제 제외)
    @Query("SELECT m FROM Meetup m WHERE " +
                    "m.date BETWEEN :startDate AND :endDate AND " +
                    "(m.isDeleted = false OR m.isDeleted IS NULL) " +
                    "ORDER BY m.date ASC")
    List<Meetup> findByDateBetweenOrderByDateAsc(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    // 카테고리별 모임 조회 (제목이나 설명에 키워드 포함, 소프트 삭제 제외)
    @Query("SELECT m FROM Meetup m WHERE " +
                    "(m.title LIKE %:keyword% OR m.description LIKE %:keyword%) AND " +
                    "(m.isDeleted = false OR m.isDeleted IS NULL) " +
                    "ORDER BY m.date ASC")
    List<Meetup> findByKeyword(@Param("keyword") String keyword);

    // 참여 가능한 모임 조회 (최대 인원 미만, 소프트 삭제 제외)
    @Query("SELECT m FROM Meetup m WHERE " +
                    "m.maxParticipants > (SELECT COUNT(p) FROM MeetupParticipants p WHERE p.meetup.idx = m.idx) " +
                    "AND m.date > :currentDate AND " +
                    "(m.isDeleted = false OR m.isDeleted IS NULL) " +
                    "ORDER BY m.date ASC")
    List<Meetup> findAvailableMeetups(@Param("currentDate") LocalDateTime currentDate);

    // 반경 기반 모임 조회 (Haversine 공식 사용, 소프트 삭제 제외)
    // ✅ 리팩토링: 날짜/상태 필터링 추가 (인메모리 필터링 제거)
    // ✅ 최적화: Bounding Box로 먼저 필터링 (idx_meetup_location 인덱스 활용)
    // 위도 1도 ≈ 111km, 경도 1도 ≈ 111km * cos(위도)
    @Query(value = "SELECT m.* FROM meetup m " +
                    "WHERE m.date > :currentDate " +
                    "AND (m.status IS NULL OR m.status != 'COMPLETED') " +
                    "AND (m.is_deleted = false OR m.is_deleted IS NULL) " +
                    "AND m.latitude BETWEEN (:lat - :radius / 111.0) AND (:lat + :radius / 111.0) " +
                    "AND m.longitude BETWEEN (:lng - :radius / (111.0 * cos(radians(:lat)))) AND (:lng + :radius / (111.0 * cos(radians(:lat)))) " +
                    "AND (6371 * acos(cos(radians(:lat)) * cos(radians(m.latitude)) * " +
                    "    cos(radians(m.longitude) - radians(:lng)) + " +
                    "    sin(radians(:lat)) * sin(radians(m.latitude)))) <= :radius " +
                    "ORDER BY (6371 * acos(cos(radians(:lat)) * cos(radians(m.latitude)) * " +
                    "         cos(radians(m.longitude) - radians(:lng)) + " +
                    "         sin(radians(:lat)) * sin(radians(m.latitude)))) ASC, m.date ASC", nativeQuery = true)
    List<Meetup> findNearbyMeetups(@Param("lat") Double lat,
                    @Param("lng") Double lng,
                    @Param("radius") Double radius,
                    @Param("currentDate") LocalDateTime currentDate);

    // Pessimistic Lock으로 동시 접근 방지
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Meetup m WHERE m.idx = :idx")
    Optional<Meetup> findByIdWithLock(@Param("idx") Long idx);

    // 원자적 UPDATE 쿼리로 동시 접근 방지
    @Modifying
    @Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1 " +
                    "WHERE m.idx = :meetupIdx " +
                    "  AND m.currentParticipants < m.maxParticipants")
    int incrementParticipantsIfAvailable(@Param("meetupIdx") Long meetupIdx);

    // 모든 모임 조회 (소프트 삭제 제외) - JOIN FETCH로 N+1 문제 해결
    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer WHERE m.isDeleted = false OR m.isDeleted IS NULL")
    List<Meetup> findAllNotDeleted();

    // 특정 모임 조회 (organizer와 participants 포함) - JOIN FETCH로 N+1 문제 해결
    @Query("SELECT DISTINCT m FROM Meetup m " +
            "LEFT JOIN FETCH m.organizer " +
            "LEFT JOIN FETCH m.participants p " +
            "LEFT JOIN FETCH p.user " +
            "WHERE m.idx = :idx")
    Optional<Meetup> findByIdWithDetails(@Param("idx") Long idx);
}

