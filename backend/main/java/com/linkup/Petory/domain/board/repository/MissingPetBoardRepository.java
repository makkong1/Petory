package com.linkup.Petory.domain.board.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetStatus;
import com.linkup.Petory.domain.user.entity.Users;

/**
 * MissingPetBoard 도메인 Repository 인터페이스입니다.
 * 
 * 이 인터페이스는 도메인 레벨의 순수 인터페이스로, JPA나 다른 기술에 의존하지 않습니다.
 * 다양한 데이터베이스 구현체(JPA, MyBatis, NoSQL 등)로 교체 가능하도록 설계되었습니다.
 * 
 * 구현체:
 * - JpaMissingPetBoardAdapter: JPA 기반 구현체
 * - 다른 DB로 변경 시 새로운 어댑터를 만들고 @Primary를 옮기면 됩니다.
 */
public interface MissingPetBoardRepository {

    MissingPetBoard save(MissingPetBoard board);

    MissingPetBoard saveAndFlush(MissingPetBoard board);

    Optional<MissingPetBoard> findById(Long id);

    boolean existsById(Long id);

    void flush();

    void delete(MissingPetBoard board);

    void deleteById(Long id);

    List<MissingPetBoard> findAllByOrderByCreatedAtDesc();

    List<MissingPetBoard> findByStatusOrderByCreatedAtDesc(MissingPetStatus status);

    Optional<MissingPetBoard> findByIdWithUser(Long id);

    /**
     * 사용자별 게시글 조회 (삭제되지 않은 것만, 최신순) - 작성자도 활성 상태여야 함
     */
    List<MissingPetBoard> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user);

    /**
     * 페이징 지원 - 전체 조회
     */
    Page<MissingPetBoard> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * 페이징 지원 - 상태별 조회
     */
    Page<MissingPetBoard> findByStatusOrderByCreatedAtDesc(MissingPetStatus status, Pageable pageable);

    /**
     * 홈 실종 추천용 - 실종일 최신순 후보 조회
     */
    Page<MissingPetBoard> findHomeCandidatesByStatusOrderByLostDateDesc(MissingPetStatus status, Pageable pageable);

    /**
     * 홈 실종 추천용 - 좌표 바운딩 박스 내 실종일 최신순 후보 조회
     */
    Page<MissingPetBoard> findHomeCandidatesInBoundingBox(
            MissingPetStatus status,
            BigDecimal minLat,
            BigDecimal maxLat,
            BigDecimal minLng,
            BigDecimal maxLng,
            Pageable pageable);

    /**
     * [리팩토링] Admin 페이징 - Specification 기반 DB 레벨 필터링 (status, deleted, q)
     */
    Page<MissingPetBoard> findAll(Specification<MissingPetBoard> spec, Pageable pageable);

    /**
     * [리팩토링] 게시글 작성자 ID만 조회 (프로젝션) - startMissingPetChat 등 경량 조회용
     */
    Optional<Long> findUserIdByIdx(Long idx);
}
