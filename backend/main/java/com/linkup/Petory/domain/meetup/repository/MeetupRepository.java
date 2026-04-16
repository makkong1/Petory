package com.linkup.Petory.domain.meetup.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.linkup.Petory.domain.meetup.entity.Meetup;
import com.linkup.Petory.domain.meetup.entity.MeetupStatus;

/**
 * Meetup 도메인 Repository 인터페이스입니다.
 * 
 * 이 인터페이스는 도메인 레벨의 순수 인터페이스로, JPA나 다른 기술에 의존하지 않습니다.
 * 다양한 데이터베이스 구현체(JPA, MyBatis, NoSQL 등)로 교체 가능하도록 설계되었습니다.
 * 
 * 구현체:
 * - JpaMeetupAdapter: JPA 기반 구현체
 * - 다른 DB로 변경 시 새로운 어댑터를 만들고 @Primary를 옮기면 됩니다.
 */
public interface MeetupRepository {

    // 기본 CRUD 메서드
    Meetup save(Meetup meetup);

    Optional<Meetup> findById(Long id);

    /**
     * 주최자별 모임 조회 (소프트 삭제 제외)
     */
    List<Meetup> findByOrganizerIdxOrderByCreatedAtDesc(Long organizerIdx);

    /**
     * 지역별 모임 조회 (위도/경도 범위, 소프트 삭제 제외)
     */
    List<Meetup> findByLocationRange(
            Double minLat,
            Double maxLat,
            Double minLng,
            Double maxLng);

    /**
     * 카테고리별 모임 조회 (제목이나 설명에 키워드 포함, 소프트 삭제 제외)
     */
    List<Meetup> findByKeyword(String keyword);

    /**
     * 참여 가능한 모임 조회 (RECRUITING 상태 + 인원 미달, 소프트 삭제 제외).
     * {@link Pageable#unpaged()}이면 전체, 그 외에는 DB LIMIT/OFFSET 적용.
     */
    List<Meetup> findAvailableMeetups(LocalDateTime currentDate, MeetupStatus recruiting, Pageable pageable);

    /**
     * 반경 기반 근처 모임 ID (거리·일시 정렬, 상한 적용) 후 {@link #findByIdxInWithOrganizer(Collection)}로 주최자 페치.
     */
    List<Long> findNearbyMeetupIds(Double lat, Double lng, Double radius, LocalDateTime currentDate, int limit);

    /**
     * ID 목록으로 모임 조회 (주최자 JOIN FETCH, 삭제 제외)
     */
    List<Meetup> findByIdxInWithOrganizer(Collection<Long> ids);

    /**
     * 단건 조회 (주최자 포함) - 참가/취소 시 권한 확인용
     */
    Optional<Meetup> findByIdWithOrganizer(Long idx);

    /**
     * Pessimistic Lock으로 동시 접근 방지
     */
    Optional<Meetup> findByIdWithLock(Long idx);

    /**
     * 원자적 UPDATE 쿼리로 동시 접근 방지 (RECRUITING 상태인 경우에만 증가)
     * 반환값: 업데이트된 행 수 (0 또는 1)
     */
    int incrementParticipantsIfAvailable(Long meetupIdx, MeetupStatus recruiting);

    /**
     * [FIX] 참가 취소 시 원자적 감소 (currentParticipants > 0 조건으로 음수 방지)
     * 반환값: 업데이트된 행 수 (0 또는 1)
     */
    int decrementParticipantsIfPositive(Long meetupIdx);

    /**
     * 모든 모임 조회 (소프트 삭제 제외) - JOIN FETCH로 N+1 문제 해결
     */
    List<Meetup> findAllNotDeleted();

    /**
     * 모든 모임 페이징 (소프트 삭제 제외, 주최자 로딩 포함)
     */
    Page<Meetup> findAllNotDeleted(Pageable pageable);

    /**
     * 특정 모임 조회 (organizer와 participants 포함) - JOIN FETCH로 N+1 문제 해결
     */
    Optional<Meetup> findByIdWithDetails(Long idx);

    /**
     * 통계용: 특정 기간 동안 생성된 모임 수
     */
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    /**
     * 정원 마감된 모집중 모임 → CLOSED (일시가 아직 지나지 않은 경우만)
     */
    int closeFullRecruitingMeetups(LocalDateTime now);

    /**
     * 일시가 지난 모임 → COMPLETED
     */
    int completePastMeetups(LocalDateTime now);
}
