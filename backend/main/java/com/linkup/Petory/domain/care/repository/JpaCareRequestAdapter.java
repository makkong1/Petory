package com.linkup.Petory.domain.care.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.user.entity.Users;

import lombok.RequiredArgsConstructor;

/**
 * CareRequestRepository의 JPA 구현체(어댑터)입니다.
 * 
 * 이 클래스는 Spring Data JPA를 사용하여 CareRequestRepository 인터페이스를 구현합니다.
 * 나중에 다른 DB나 DBMS로 변경할 경우, 이 어댑터와 유사한 새 클래스를 만들고
 * 
 * @Primary 어노테이션을 옮기면 됩니다.
 * 
 *          예시:
 *          - MyBatis로 변경: MyBatisCareRequestAdapter 생성 후 @Primary 이동
 *          - MongoDB로 변경: MongoCareRequestAdapter 생성 후 @Primary 이동
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaCareRequestAdapter implements CareRequestRepository {

    private final SpringDataJpaCareRequestRepository jpaRepository;

    @Override
    public CareRequest save(CareRequest careRequest) {
        return jpaRepository.save(careRequest);
    }

    @Override
    public Optional<CareRequest> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public CareRequest getReferenceById(Long id) {
        return jpaRepository.getReferenceById(id);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<CareRequest> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user) {
        return jpaRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
    }

    @Override
    public List<CareRequest> findAllActiveRequests() {
        return jpaRepository.findAllActiveRequests();
    }

    @Override
    public List<CareRequest> findByStatusAndIsDeletedFalse(CareRequestStatus status) {
        return jpaRepository.findByStatusAndIsDeletedFalse(status);
    }

    @Override
    public List<CareRequest> findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCaseAndIsDeletedFalse(
            String titleKeyword,
            String descKeyword) {
        String keyword = titleKeyword != null ? titleKeyword : descKeyword;
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        List<Long> ids = jpaRepository.findIdxByFulltextKeyword(keyword);
        if (ids.isEmpty()) {
            return List.of();
        }
        List<CareRequest> loaded = jpaRepository.findByIdxInWithAssociations(ids);
        Map<Long, CareRequest> byId = loaded.stream()
                .collect(Collectors.toMap(CareRequest::getIdx, Function.identity(), (a, b) -> a));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<CareRequest> findByDateBeforeAndStatusIn(
            LocalDateTime now,
            List<CareRequestStatus> statuses) {
        return jpaRepository.findByDateBeforeAndStatusIn(now, statuses);
    }

    @Override
    public Optional<CareRequest> findByIdWithUser(Long idx) {
        return jpaRepository.findByIdWithUser(idx);
    }

    @Override
    public Optional<CareRequest> findByIdWithApplications(Long idx) {
        return jpaRepository.findByIdWithApplications(idx);
    }

    @Override
    public long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end) {
        return jpaRepository.countByCreatedAtBetween(start, end);
    }

    @Override
    @Deprecated
    public long countByDateBetweenAndStatus(LocalDateTime start, LocalDateTime end, CareRequestStatus status) {
        return jpaRepository.countByDateBetweenAndStatus(start, end, status);
    }

    @Override
    public long countByCompletedAtBetween(LocalDateTime start, LocalDateTime end) {
        return jpaRepository.countByCompletedAtBetween(start, end);
    }

    @Override
    public Page<CareRequest> findAllActiveRequestsWithPaging(String location, Pageable pageable) {
        return jpaRepository.findAllActiveRequestsWithPaging(location, pageable);
    }

    @Override
    public Page<CareRequest> findByStatusAndIsDeletedFalseWithPaging(CareRequestStatus status, String location, Pageable pageable) {
        return jpaRepository.findByStatusAndIsDeletedFalseWithPaging(status, location, pageable);
    }

    @Override
    public Page<CareRequest> searchWithPaging(String keyword, Pageable pageable) {
        Page<CareRequest> raw = jpaRepository.searchWithPaging(keyword, pageable);
        if (raw.isEmpty()) {
            return raw;
        }
        List<Long> ids = raw.getContent().stream()
                .map(CareRequest::getIdx)
                .collect(Collectors.toList());
        List<CareRequest> hydrated = jpaRepository.findByIdxInWithAssociations(ids);
        Map<Long, CareRequest> byId = hydrated.stream()
                .collect(Collectors.toMap(CareRequest::getIdx, Function.identity(), (a, b) -> a));
        List<CareRequest> ordered = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        return new PageImpl<>(ordered, pageable, raw.getTotalElements());
    }

    @Override
    public List<CareRequest> findNearby(double lat, double lng, double radiusKm, int limit) {
        return jpaRepository.findNearbyCareRequests(lat, lng, radiusKm, limit);
    }
}

