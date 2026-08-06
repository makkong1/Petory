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
    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer o WHERE o.idx = :organizerIdx "
            + "AND (m.status IS NULL OR m.status <> com.linkup.Petory.domain.meetup.entity.MeetupStatus.CANCELLED) "
            + "AND (m.isDeleted = false OR m.isDeleted IS NULL) "
            + "AND o.isDeleted = false AND o.status <> com.linkup.Petory.domain.user.entity.UserStatus.BANNED "
            + "ORDER BY m.createdAt DESC")
    List<Meetup> findByOrganizerIdxOrderByCreatedAtDesc(@Param("organizerIdx") Long organizerIdx);

    @RepositoryMethod("모임: 이벤트 리스너용 주최자의 RECRUITING 모임 조회")
    @Query("SELECT m FROM Meetup m WHERE m.organizer.idx = :organizerIdx AND m.status = :status AND (m.isDeleted = false OR m.isDeleted IS NULL)")
    List<Meetup> findRecruitingByOrganizerIdx(@Param("organizerIdx") Long organizerIdx, @Param("status") MeetupStatus status);

    @RepositoryMethod("모임: 키워드 FULLTEXT 검색 — idx 목록 반환 (N+1 방지용 1단계)")
    // 주최자 필터는 1단계(idx 목록)에 걸어야 LIMIT/페이징 건수가 맞는다.
    @Query(value = "SELECT m.idx FROM meetup m "
            + "INNER JOIN users u ON u.idx = m.organizer_idx "
            + "WHERE (m.is_deleted = false OR m.is_deleted IS NULL) "
            + "AND (m.status IS NULL OR m.status != 'CANCELLED') "
            + "AND u.is_deleted = 0 AND u.status <> 'BANNED' "
            + "AND MATCH(m.title, m.description) AGAINST(:keyword IN NATURAL LANGUAGE MODE) "
            + "ORDER BY m.date ASC", nativeQuery = true)
    List<Long> findIdxByFulltextKeyword(@Param("keyword") String keyword, Pageable pageable);

    //FULLTEXT 인덱스 활용 + JOIN FETCH N+1 방지를 동시에 달성하기 위한 패턴
    //컨트롤러 → 서비스 → 어댑터(도메인 인터페이스 구현체) → JPA 인터페이스 default 메서드 순
    @RepositoryMethod("모임: 키워드 검색 (FULLTEXT 2단계)")
    default List<Meetup> findByKeyword(String keyword, Pageable pageable) {
        // 이전에는 전량 조회 후 서비스에서 subList 로 잘랐다 (DB 는 이미 500건을 다 읽은 뒤였다).
        // 이제 DB LIMIT 으로 내린다. 근거: docs/analysis/query-audit/fixes-2026-07-14.md §4
        List<Long> ids = findIdxByFulltextKeyword(keyword, pageable);
        if (ids.isEmpty()) {
            return List.of();
        }
        return findByIdxInWithOrganizer(ids);
    }

    /**
     * 참여 가능 목록. GROUP BY/HAVING 제거 → currentParticipants 직접 비교로 메모리 페이징 위험 해소.
     * {@link Pageable#unpaged()}이면 전체, 아니면 DB LIMIT/OFFSET 적용.
     */
    @RepositoryMethod("모임: 참여 가능 목록 (페이징 가능, RECRUITING 상태만)")
    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer o "
            + "WHERE m.date > :currentDate "
            + "AND m.currentParticipants < m.maxParticipants "
            + "AND m.status = :recruiting "
            + "AND (m.isDeleted = false OR m.isDeleted IS NULL) "
            + "AND o.isDeleted = false AND o.status <> com.linkup.Petory.domain.user.entity.UserStatus.BANNED "
            + "ORDER BY m.date ASC")
    List<Meetup> findAvailableMeetups(
            @Param("currentDate") LocalDateTime currentDate,
            @Param("recruiting") MeetupStatus recruiting,
            Pageable pageable);

    @RepositoryMethod("모임: 단건 조회 (주최자 포함)")
    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer WHERE m.idx = :idx "
            + "AND (m.status IS NULL OR m.status <> com.linkup.Petory.domain.meetup.entity.MeetupStatus.CANCELLED) "
            + "AND (m.isDeleted = false OR m.isDeleted IS NULL)")
    Optional<Meetup> findByIdWithOrganizer(@Param("idx") Long idx);

    @RepositoryMethod("모임: 반경 기반 근처 모임 ID 목록 (공간 인덱스 정렬·LIMIT)")
    // 주최자 필터는 1단계(idx 목록)에 걸어야 LIMIT 건수가 맞는다.
    @Query(value = "SELECT m.idx FROM meetup m "
            + "INNER JOIN users u ON u.idx = m.organizer_idx "
            + "WHERE m.date > :currentDate "
            + "AND (m.status IS NULL OR m.status NOT IN ('COMPLETED', 'CANCELLED')) "
            + "AND (m.is_deleted = false OR m.is_deleted IS NULL) "
            + "AND u.is_deleted = 0 AND u.status <> 'BANNED' "
            + "AND m.latitude IS NOT NULL "
            + "AND m.longitude IS NOT NULL "
            + "AND ST_Within(m.geo_point, ST_GeomFromText("
            + "CONCAT('POLYGON((', "
            + ":lat - (:radius / 111.0), ' ', :lng - (:radius / (111.0 * COS(RADIANS(:lat)))), ', ', "
            + ":lat - (:radius / 111.0), ' ', :lng + (:radius / (111.0 * COS(RADIANS(:lat)))), ', ', "
            + ":lat + (:radius / 111.0), ' ', :lng + (:radius / (111.0 * COS(RADIANS(:lat)))), ', ', "
            + ":lat + (:radius / 111.0), ' ', :lng - (:radius / (111.0 * COS(RADIANS(:lat)))), ', ', "
            + ":lat - (:radius / 111.0), ' ', :lng - (:radius / (111.0 * COS(RADIANS(:lat)))), '))'), "
            + "4326)) "
            + "AND ST_Distance_Sphere(m.geo_point, ST_GeomFromText("
            + "CONCAT('POINT(', :lat, ' ', :lng, ')'), 4326)) <= (:radius * 1000) "
            // [지도 반경검색 통일] 보조 정렬키를 m.date ASC → m.idx ASC 로 맞췄다.
            // 거리 동점일 때 date 가 같은 모임이 또 있으면 순서가 비결정적이라, 지도를 옮기며
            // 재조회할 때 마커 순서가 흔들릴 수 있었다. PK 를 마지막 키로 두면 항상 고정된다.
            // (같은 규칙을 care·location 에도 적용했다)
            + "ORDER BY ST_Distance_Sphere(m.geo_point, ST_GeomFromText("
            + "CONCAT('POINT(', :lat, ' ', :lng, ')'), 4326)) ASC, m.idx ASC "
            + "LIMIT :limit", nativeQuery = true)
    List<Long> findNearbyMeetupIds(@Param("lat") Double lat,
            @Param("lng") Double lng,
            @Param("radius") Double radius,
            @Param("currentDate") LocalDateTime currentDate,
            @Param("limit") int limit);

    @RepositoryMethod("모임: ID 목록으로 조회 (주최자 페치, N+1 방지)")
    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer WHERE m.idx IN :ids "
            + "AND (m.status IS NULL OR m.status <> com.linkup.Petory.domain.meetup.entity.MeetupStatus.CANCELLED) "
            + "AND (m.isDeleted = false OR m.isDeleted IS NULL)")
    List<Meetup> findByIdxInWithOrganizer(@Param("ids") Collection<Long> ids);

    @RepositoryMethod("모임: 비관적 락 조회 (동시성 제어, 소프트 삭제 제외)")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer WHERE m.idx = :idx AND (m.isDeleted = false OR m.isDeleted IS NULL)")
    Optional<Meetup> findByIdWithLock(@Param("idx") Long idx);

    @RepositoryMethod("모임: 참여자 수 원자적 증가 (RECRUITING 상태 + 인원 미달 조건)")
    @Modifying
    @Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants + 1 "
            + "WHERE m.idx = :meetupIdx "
            + "  AND m.currentParticipants < m.maxParticipants "
            + "  AND m.status = :recruiting")
    int incrementParticipantsIfAvailable(
            @Param("meetupIdx") Long meetupIdx,
            @Param("recruiting") MeetupStatus recruiting);

    // [FIX] 참가 취소 시 원자적 감소 — 기존 read-modify-write(Math.max)는 동시 취소 시 카운트 불일치 위험.
    // currentParticipants > 0 조건으로 음수 방지.
    @RepositoryMethod("모임: 참여자 수 원자적 감소")
    @Modifying
    @Query("UPDATE Meetup m SET m.currentParticipants = m.currentParticipants - 1 "
            + "WHERE m.idx = :meetupIdx "
            + "  AND m.currentParticipants > 0")
    int decrementParticipantsIfPositive(@Param("meetupIdx") Long meetupIdx);

    @RepositoryMethod("모임: 전체 목록 조회 (삭제 제외)")
    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer o "
            + "WHERE (m.status IS NULL OR m.status <> com.linkup.Petory.domain.meetup.entity.MeetupStatus.CANCELLED) "
            + "AND (m.isDeleted = false OR m.isDeleted IS NULL) "
            + "AND o.isDeleted = false AND o.status <> com.linkup.Petory.domain.user.entity.UserStatus.BANNED")
    List<Meetup> findAllNotDeleted();

    /**
     * 전체 목록 페이징 (JOIN FETCH 대신 EntityGraph — Pageable과 호환).
     */
    @EntityGraph(attributePaths = {"organizer"})
    @Query(value = "SELECT m FROM Meetup m JOIN m.organizer o "
            + "WHERE (m.status IS NULL OR m.status <> com.linkup.Petory.domain.meetup.entity.MeetupStatus.CANCELLED) "
            + "AND (m.isDeleted = false OR m.isDeleted IS NULL) "
            + "AND o.isDeleted = false AND o.status <> com.linkup.Petory.domain.user.entity.UserStatus.BANNED",
           countQuery = "SELECT COUNT(m) FROM Meetup m JOIN m.organizer o "
            + "WHERE (m.status IS NULL OR m.status <> com.linkup.Petory.domain.meetup.entity.MeetupStatus.CANCELLED) "
            + "AND (m.isDeleted = false OR m.isDeleted IS NULL) "
            + "AND o.isDeleted = false AND o.status <> com.linkup.Petory.domain.user.entity.UserStatus.BANNED")
    Page<Meetup> findAllNotDeleted(Pageable pageable);

    @EntityGraph(attributePaths = {"organizer"})
    @RepositoryMethod("모임: 관리자 필터 페이징 조회 (keyword 없을 때)")
    @Query("SELECT m FROM Meetup m WHERE "
            + "(m.isDeleted = false OR m.isDeleted IS NULL) AND "
            + "(:status IS NULL OR CAST(m.status AS string) = :status) "
            + "ORDER BY m.createdAt DESC")
    Page<Meetup> findAllForAdmin(
            @Param("status") String status,
            Pageable pageable);

    @RepositoryMethod("모임: 관리자 키워드 페이징 조회 (FULLTEXT title/description + LIKE location)")
    @Query(value
            = "SELECT * FROM meetup m "
            + "WHERE (m.is_deleted = false OR m.is_deleted IS NULL) "
            + "AND (:status IS NULL OR m.status = :status) "
            + "AND (MATCH(m.title, m.description) AGAINST(:keyword IN NATURAL LANGUAGE MODE) "
            + // or조건은 데이터 증가 시 재검토
            "     OR m.location LIKE CONCAT('%', :keyword, '%')) "
            + "ORDER BY m.created_at DESC",
            countQuery
            = "SELECT COUNT(*) FROM meetup m "
            + "WHERE (m.is_deleted = false OR m.is_deleted IS NULL) "
            + "AND (:status IS NULL OR m.status = :status) "
            + "AND (MATCH(m.title, m.description) AGAINST(:keyword IN NATURAL LANGUAGE MODE) "
            + "     OR m.location LIKE CONCAT('%', :keyword, '%'))",
            nativeQuery = true)
    Page<Meetup> findAllForAdminWithKeyword(
            @Param("status") String status,
            @Param("keyword") String keyword,
            Pageable pageable);

    @RepositoryMethod("모임: 단건 상세 조회 (참여자 포함)")
    @Query("SELECT DISTINCT m FROM Meetup m "
            + "LEFT JOIN FETCH m.organizer o "
            + "LEFT JOIN FETCH m.participants p "
            + "LEFT JOIN FETCH p.user "
            + "WHERE m.idx = :idx "
            + "AND (m.status IS NULL OR m.status <> com.linkup.Petory.domain.meetup.entity.MeetupStatus.CANCELLED) "
            + "AND (m.isDeleted = false OR m.isDeleted IS NULL) "
            + "AND o.isDeleted = false AND o.status <> com.linkup.Petory.domain.user.entity.UserStatus.BANNED")
    Optional<Meetup> findByIdWithDetails(@Param("idx") Long idx);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Meetup m SET m.status = :closed WHERE m.status = :recruiting "
            + "AND m.currentParticipants >= m.maxParticipants AND m.date >= :now "
            + "AND (m.isDeleted = false OR m.isDeleted IS NULL)")
    int closeFullRecruitingMeetups(
            @Param("now") LocalDateTime now,
            @Param("recruiting") MeetupStatus recruiting,
            @Param("closed") MeetupStatus closed);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Meetup m SET m.status = :completed WHERE m.date < :now "
            + "AND m.status <> :completed AND m.status <> :cancelled "
            + "AND (m.isDeleted = false OR m.isDeleted IS NULL)")
    int completePastMeetups(
            @Param("now") LocalDateTime now,
            @Param("completed") MeetupStatus completed,
            @Param("cancelled") MeetupStatus cancelled);

    @RepositoryMethod("모임: 기간별 통계")
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT m FROM Meetup m JOIN FETCH m.organizer "
            + "WHERE (m.isDeleted = false OR m.isDeleted IS NULL) "
            + "AND NOT EXISTS ("
            + "  SELECT c FROM Conversation c "
            + "  WHERE c.relatedType = com.linkup.Petory.domain.chat.entity.RelatedType.MEETUP "
            + "  AND c.relatedIdx = m.idx AND c.isDeleted = false"
            + ")")
    List<Meetup> findWithoutChatRoom();
}
