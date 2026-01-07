package com.linkup.Petory.domain.meetup.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.linkup.Petory.domain.meetup.entity.Meetup;

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

    void delete(Meetup meetup);

    void deleteById(Long id);

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
     * 날짜별 모임 조회 (소프트 삭제 제외)
     */
    List<Meetup> findByDateBetweenOrderByDateAsc(LocalDateTime startDate, LocalDateTime endDate);

    /**
     * 카테고리별 모임 조회 (제목이나 설명에 키워드 포함, 소프트 삭제 제외)
     */
    List<Meetup> findByKeyword(String keyword);

    /**
     * 참여 가능한 모임 조회 (최대 인원 미만, 소프트 삭제 제외)
     */
    List<Meetup> findAvailableMeetups(LocalDateTime currentDate);

    /**
     * 반경 기반 모임 조회 (Haversine 공식 사용, 소프트 삭제 제외)
     */
    List<Meetup> findNearbyMeetups(Double lat, Double lng, Double radius);

    /**
     * Pessimistic Lock으로 동시 접근 방지
     */
    Optional<Meetup> findByIdWithLock(Long idx);

    /**
     * 원자적 UPDATE 쿼리로 동시 접근 방지
     * 반환값: 업데이트된 행 수 (0 또는 1)
     */
    int incrementParticipantsIfAvailable(Long meetupIdx);

    /**
     * 모든 모임 조회 (소프트 삭제 제외) - JOIN FETCH로 N+1 문제 해결
     */
    List<Meetup> findAllNotDeleted();

    /**
     * 특정 모임 조회 (organizer와 participants 포함) - JOIN FETCH로 N+1 문제 해결
     */
    Optional<Meetup> findByIdWithDetails(Long idx);
}
