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
import com.linkup.Petory.global.annotation.RepositoryMethod;

import jakarta.persistence.LockModeType;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaMeetupRepository extends JpaRepository<Meetup, Long> {

    @RepositoryMethod("모임: 주최자별 목록 조회")
    @Query("SELECT m FROM Meetup m WHERE m.organizer.idx = :organizerIdx AND (m.isDeleted = false OR m.isDeleted IS NULL) ORDER BY m.createdAt DESC")
    List<Meetup> findByOrganizerIdxOrderByCreatedAtDesc(@Param("organizerIdx") Long organizerIdx);

    @RepositoryMethod("모임: 지역 범위별 조회")
    @Query("SELECT m FROM Meetup m WHERE " +
                    "m.latitude BETWEEN :minLat AND :maxLat AND " +
                    "m.longitude BETWEEN :minLng AND :maxLng AND " +
                    "(m.isDeleted = false OR m.isDeleted IS NULL) " +
                    "ORDER BY m.date ASC")
    List<Meetup> findByLocationRange(@Param("minLat") Double minLat,
                    @Param("maxLat") Double maxLat,
                    @Param("minLng") Double minLng,
                    @Param("maxLng") Double maxLng);

    @RepositoryMethod("모임: 날짜 범위별 조회")
    @Query("SELECT m FROM Meetup m WHERE " +
                    "m.date BETWEEN :startDate AND :endDate AND " +
                    "(m.isDeleted = false OR m.isDeleted IS NULL) " +
                    "ORDER BY m.date ASC")
    List<Meetup> findByDateBetweenOrderByDateAsc(@Param("startDate") LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    @RepositoryMethod("모임: 키워드 검색")
    @Query("SELECT m FROM Meetup m WHERE " +
                    "(m.title LIKE %:keyword% OR m.description LIKE %:keyword%) AND " +
                    "(m.isDeleted = false OR m.isDeleted IS NULL) " +
                    "ORDER BY m.date ASC")
    List<Meetup> findByKeyword(@Param("keyword") String keyword);

    @RepositoryMethod("모임: 참여 가능 목록 조회")
    @Query("SELECT m FROM Meetup m " +
                    "LEFT JOIN m.participants p " +
                    "WHERE m.date > :currentDate " +
                    "AND (m.isDeleted = false OR m.isDeleted IS NULL) " +
                    "GROUP BY m.idx " +
                    "HAVING COUNT(p) < m.maxParticipants " +
                    "ORDER BY m.date ASC")
    List<Meetup> findAvailableMeetups(@Param("currentDate") LocalDateTime currentDate);

    @RepositoryMethod("모임: 반경 기반 근처 모임 조회")
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

    @RepositoryMethod("모임: 비관적 락 조회 (동시성 제어)")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Meetup m WHERE m.idx = :idx")
    Optional<Meetup> findByIdWithLock(@Param("idx") Long idx);

    @RepositoryMethod("모임: 참여자 수 원자적 증가")
    @Modifying
    @Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1 " +
                    "WHERE m.idx = :meetupIdx " +
                    "  AND m.currentParticipants < m.maxParticipants")
    int incrementParticipantsIfAvailable(@Param("meetupIdx") Long meetupIdx);

    @RepositoryMethod("모임: 전체 목록 조회 (삭제 제외)")
    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer WHERE m.isDeleted = false OR m.isDeleted IS NULL")
    List<Meetup> findAllNotDeleted();

    @RepositoryMethod("모임: 단건 상세 조회 (참여자 포함)")
    @Query("SELECT DISTINCT m FROM Meetup m " +
            "LEFT JOIN FETCH m.organizer " +
            "LEFT JOIN FETCH m.participants p " +
            "LEFT JOIN FETCH p.user " +
            "WHERE m.idx = :idx")
    Optional<Meetup> findByIdWithDetails(@Param("idx") Long idx);
}

