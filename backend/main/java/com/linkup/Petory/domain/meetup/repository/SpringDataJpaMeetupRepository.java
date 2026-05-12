package com.linkup.Petory.domain.meetup.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.linkup.Petory.domain.meetup.entity.Meetup;
import com.linkup.Petory.domain.meetup.entity.MeetupStatus;
import com.linkup.Petory.global.annotation.RepositoryMethod;

import jakarta.persistence.LockModeType;

/**
 * Spring Data JPA 전용 인터페이스입니다.
 */
public interface SpringDataJpaMeetupRepository extends JpaRepository<Meetup, Long> {

    @RepositoryMethod("모임: 주최자별 목록 조회")
    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer WHERE m.organizer.idx = :organizerIdx AND (m.isDeleted = false OR m.isDeleted IS NULL) ORDER BY m.createdAt DESC")
    List<Meetup> findByOrganizerIdxOrderByCreatedAtDesc(@Param("organizerIdx") Long organizerIdx);

    @RepositoryMethod("모임: 지역 범위별 조회")
    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer WHERE " +
                    "m.latitude BETWEEN :minLat AND :maxLat AND " +
                    "m.longitude BETWEEN :minLng AND :maxLng AND " +
                    "(m.isDeleted = false OR m.isDeleted IS NULL) " +
                    "ORDER BY m.date ASC")
    List<Meetup> findByLocationRange(@Param("minLat") Double minLat,
                    @Param("maxLat") Double maxLat,
                    @Param("minLng") Double minLng,
                    @Param("maxLng") Double maxLng);

    @RepositoryMethod("모임: 키워드 검색")
    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer WHERE " +
                    "(m.title LIKE %:keyword% OR m.description LIKE %:keyword%) AND " +
                    "(m.isDeleted = false OR m.isDeleted IS NULL) " +
                    "ORDER BY m.date ASC")
    List<Meetup> findByKeyword(@Param("keyword") String keyword);

    /**
     * 참여 가능 목록. GROUP BY/HAVING 제거 → currentParticipants 직접 비교로 메모리 페이징 위험 해소.
     * {@link Pageable#unpaged()}이면 전체, 아니면 DB LIMIT/OFFSET 적용.
     */
    @RepositoryMethod("모임: 참여 가능 목록 (페이징 가능, RECRUITING 상태만)")
    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer " +
                    "WHERE m.date > :currentDate " +
                    "AND m.currentParticipants < m.maxParticipants " +
                    "AND m.status = :recruiting " +
                    "AND (m.isDeleted = false OR m.isDeleted IS NULL) " +
                    "ORDER BY m.date ASC")
    List<Meetup> findAvailableMeetups(
                    @Param("currentDate") LocalDateTime currentDate,
                    @Param("recruiting") MeetupStatus recruiting,
                    Pageable pageable);

    @RepositoryMethod("모임: 단건 조회 (주최자 포함)")
    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer WHERE m.idx = :idx " +
                    "AND (m.isDeleted = false OR m.isDeleted IS NULL)")
    Optional<Meetup> findByIdWithOrganizer(@Param("idx") Long idx);

    @RepositoryMethod("모임: 반경 기반 근처 모임 ID 목록 (정렬·LIMIT)")
    @Query(value = "SELECT m.idx FROM meetup m " +
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
                    "         sin(radians(:lat)) * sin(radians(m.latitude)))) ASC, m.date ASC " +
                    "LIMIT :limit", nativeQuery = true)
    List<Long> findNearbyMeetupIds(@Param("lat") Double lat,
                    @Param("lng") Double lng,
                    @Param("radius") Double radius,
                    @Param("currentDate") LocalDateTime currentDate,
                    @Param("limit") int limit);

    @RepositoryMethod("모임: ID 목록으로 조회 (주최자 페치, N+1 방지)")
    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer WHERE m.idx IN :ids " +
                    "AND (m.isDeleted = false OR m.isDeleted IS NULL)")
    List<Meetup> findByIdxInWithOrganizer(@Param("ids") Collection<Long> ids);

    @RepositoryMethod("모임: 비관적 락 조회 (동시성 제어, 소프트 삭제 제외)")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer WHERE m.idx = :idx AND (m.isDeleted = false OR m.isDeleted IS NULL)")
    Optional<Meetup> findByIdWithLock(@Param("idx") Long idx);

    @RepositoryMethod("모임: 참여자 수 원자적 증가 (RECRUITING 상태 + 인원 미달 조건)")
    @Modifying
    @Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1 " +
                    "WHERE m.idx = :meetupIdx " +
                    "  AND m.currentParticipants < m.maxParticipants " +
                    "  AND m.status = :recruiting")
    int incrementParticipantsIfAvailable(
                    @Param("meetupIdx") Long meetupIdx,
                    @Param("recruiting") MeetupStatus recruiting);

    // [FIX] 참가 취소 시 원자적 감소 — 기존 read-modify-write(Math.max)는 동시 취소 시 카운트 불일치 위험.
    // currentParticipants > 0 조건으로 음수 방지.
    @RepositoryMethod("모임: 참여자 수 원자적 감소")
    @Modifying
    @Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants - 1 " +
                    "WHERE m.idx = :meetupIdx " +
                    "  AND m.currentParticipants > 0")
    int decrementParticipantsIfPositive(@Param("meetupIdx") Long meetupIdx);

    @RepositoryMethod("모임: 전체 목록 조회 (삭제 제외)")
    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer WHERE m.isDeleted = false OR m.isDeleted IS NULL")
    List<Meetup> findAllNotDeleted();

    /**
     * 전체 목록 페이징 (JOIN FETCH 대신 EntityGraph — Pageable과 호환).
     */
    @EntityGraph(attributePaths = {"organizer"})
    @Query("SELECT m FROM Meetup m WHERE (m.isDeleted = false OR m.isDeleted IS NULL)")
    Page<Meetup> findAllNotDeleted(Pageable pageable);

    @EntityGraph(attributePaths = {"organizer"})
    @RepositoryMethod("모임: 관리자 필터 페이징 조회")
    @Query("SELECT m FROM Meetup m WHERE " +
            "(m.isDeleted = false OR m.isDeleted IS NULL) AND " +
            "(:status IS NULL OR CAST(m.status AS string) = :status) AND " +
            "(:keyword IS NULL OR m.title LIKE %:keyword% OR m.description LIKE %:keyword% OR m.location LIKE %:keyword%) " +
            "ORDER BY m.createdAt DESC")
    Page<Meetup> findAllForAdmin(
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @RepositoryMethod("모임: 단건 상세 조회 (참여자 포함)")
    @Query("SELECT DISTINCT m FROM Meetup m " +
            "LEFT JOIN FETCH m.organizer " +
            "LEFT JOIN FETCH m.participants p " +
            "LEFT JOIN FETCH p.user " +
            "WHERE m.idx = :idx AND (m.isDeleted = false OR m.isDeleted IS NULL)")
    Optional<Meetup> findByIdWithDetails(@Param("idx") Long idx);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Meetup m SET m.status = :closed WHERE m.status = :recruiting " +
                    "AND m.currentParticipants >= m.maxParticipants AND m.date >= :now " +
                    "AND (m.isDeleted = false OR m.isDeleted IS NULL)")
    int closeFullRecruitingMeetups(
            @Param("now") LocalDateTime now,
            @Param("recruiting") MeetupStatus recruiting,
            @Param("closed") MeetupStatus closed);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Meetup m SET m.status = :completed WHERE m.date < :now AND m.status <> :completed " +
                    "AND (m.isDeleted = false OR m.isDeleted IS NULL)")
    int completePastMeetups(@Param("now") LocalDateTime now, @Param("completed") MeetupStatus completed);

    @RepositoryMethod("모임: 기간별 통계")
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer " +
            "WHERE (m.isDeleted = false OR m.isDeleted IS NULL) " +
            "AND NOT EXISTS (" +
            "  SELECT c FROM Conversation c " +
            "  WHERE c.relatedType = com.linkup.Petory.domain.chat.entity.RelatedType.MEETUP " +
            "  AND c.relatedIdx = m.idx AND c.isDeleted = false" +
            ")")
    List<Meetup> findWithoutChatRoom();
}
