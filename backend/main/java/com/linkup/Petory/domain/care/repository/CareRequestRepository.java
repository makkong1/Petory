package com.linkup.Petory.domain.care.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * CareRequest 도메인 Repository 인터페이스입니다.
 * 
 * 이 인터페이스는 도메인 레벨의 순수 인터페이스로, JPA나 다른 기술에 의존하지 않습니다.
 * 다양한 데이터베이스 구현체(JPA, MyBatis, NoSQL 등)로 교체 가능하도록 설계되었습니다.
 * 
 * 구현체:
 * - JpaCareRequestAdapter: JPA 기반 구현체
 * - 다른 DB로 변경 시 새로운 어댑터를 만들고 @Primary를 옮기면 됩니다.
 */
public interface CareRequestRepository {

    // 기본 CRUD 메서드
    CareRequest save(CareRequest careRequest);

    Optional<CareRequest> findById(Long id);

    /**
     * ID로 엔티티 참조 조회 (프록시 객체 반환, 실제 조회는 지연됨)
     */
    CareRequest getReferenceById(Long id);

    void deleteById(Long id);

        /**
         * 사용자별 케어 요청 조회 (최신순) - 작성자도 활성 상태여야 함
         */
        List<CareRequest> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user);

        /**
         * 전체 케어 요청 조회 - 작성자도 활성 상태여야 함
         */
        List<CareRequest> findAllActiveRequests();

        /**
         * 상태별 케어 요청 조회 - 작성자도 활성 상태여야 함
         */
        List<CareRequest> findByStatusAndIsDeletedFalse(CareRequestStatus status);

        /**
         * 제목이나 설명에 키워드 포함된 케어 요청 검색 - 작성자도 활성 상태여야 함
         */
        List<CareRequest> findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCaseAndIsDeletedFalse(
                        String titleKeyword,
                        String descKeyword);

        /**
         * 날짜가 지났고 특정 상태인 요청 조회 (스케줄러용)
         */
        List<CareRequest> findByDateBeforeAndStatusIn(
                        LocalDateTime now,
                        List<CareRequestStatus> statuses);

        /**
         * 단일 케어 요청 조회 (작성자 포함) - 수정/삭제 시 권한 확인용
         */
        Optional<CareRequest> findByIdWithUser(Long idx);

        /**
         * 단일 케어 요청 조회 (펫 정보 및 지원 정보 포함)
         */
        Optional<CareRequest> findByIdWithApplications(Long idx);

        /**
         * 통계용: 특정 기간 동안 생성된 케어 요청 수
         */
        long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

        /**
         * @deprecated 케어 예정일(date) 기준 집계 — 통계 오류. countByCompletedAtBetween 사용 권장
         */
        @Deprecated
        long countByDateBetweenAndStatus(LocalDateTime start, LocalDateTime end, CareRequestStatus status);

        /**
         * [FIX] 통계용: 특정 기간 동안 실제 완료된 케어 요청 수 (completedAt 기준)
         */
        long countByCompletedAtBetween(LocalDateTime start, LocalDateTime end);

        /**
         * 페이징 - 전체 조회 (location optional)
         */
        Page<CareRequest> findAllActiveRequestsWithPaging(String location, Pageable pageable);

        /**
         * 페이징 - 상태별 조회 (location optional)
         */
        Page<CareRequest> findByStatusAndIsDeletedFalseWithPaging(CareRequestStatus status, String location, Pageable pageable);

        /**
         * 페이징 - 검색
         */
        Page<CareRequest> searchWithPaging(String keyword, Pageable pageable);

        /**
         * 반경 기반 근처 케어 요청 조회 (지도 표출용)
         */
        List<CareRequest> findNearby(double lat, double lng, double radiusKm, int limit);

        /**
         * 관리자용 케어 요청 페이징 (status / deleted / keyword 복합 필터)
         * deleted null → 전체(삭제 포함), false → 미삭제만, true → 삭제된 것만
         */
        Page<CareRequest> findAllForAdmin(String status, Boolean deleted, String keyword, Pageable pageable);
}
