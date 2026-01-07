package com.linkup.Petory.domain.care.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    List<CareRequest> saveAll(List<CareRequest> careRequests);

    Optional<CareRequest> findById(Long id);

    /**
     * ID로 엔티티 참조 조회 (프록시 객체 반환, 실제 조회는 지연됨)
     */
    CareRequest getReferenceById(Long id);

    void delete(CareRequest careRequest);

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
         * 위치별 케어 요청 조회 (사용자 위치 기반)
         */
        List<CareRequest> findByUser_LocationContaining(String location);

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
         * 단일 케어 요청 조회 (펫 정보 포함)
         */
        Optional<CareRequest> findByIdWithPet(Long idx);

        /**
         * 단일 케어 요청 조회 (펫 정보 및 지원 정보 포함)
         */
        Optional<CareRequest> findByIdWithApplications(Long idx);

        /**
         * 통계용: 특정 기간 동안 생성된 케어 요청 수
         */
        long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

        /**
         * 통계용: 특정 기간과 상태별 케어 요청 수
         */
        long countByDateBetweenAndStatus(LocalDateTime start, LocalDateTime end, CareRequestStatus status);
}
