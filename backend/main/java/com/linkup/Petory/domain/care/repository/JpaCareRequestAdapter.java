package com.linkup.Petory.domain.care.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
    public List<CareRequest> saveAll(List<CareRequest> careRequests) {
        return jpaRepository.saveAll(careRequests);
    }

    @Override
    public Optional<CareRequest> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(CareRequest careRequest) {
        jpaRepository.delete(careRequest);
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
    public List<CareRequest> findByUser_LocationContaining(String location) {
        return jpaRepository.findByUser_LocationContaining(location);
    }

    @Override
    public List<CareRequest> findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCaseAndIsDeletedFalse(
            String titleKeyword,
            String descKeyword) {
        return jpaRepository.findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCaseAndIsDeletedFalse(
                titleKeyword, descKeyword);
    }

    @Override
    public List<CareRequest> findByDateBeforeAndStatusIn(
            LocalDateTime now,
            List<CareRequestStatus> statuses) {
        return jpaRepository.findByDateBeforeAndStatusIn(now, statuses);
    }

    @Override
    public Optional<CareRequest> findByIdWithPet(Long idx) {
        return jpaRepository.findByIdWithPet(idx);
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
    public long countByDateBetweenAndStatus(LocalDateTime start, LocalDateTime end, CareRequestStatus status) {
        return jpaRepository.countByDateBetweenAndStatus(start, end, status);
    }
}

