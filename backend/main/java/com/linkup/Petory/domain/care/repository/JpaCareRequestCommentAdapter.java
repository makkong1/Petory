package com.linkup.Petory.domain.care.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestComment;
import com.linkup.Petory.domain.user.entity.Users;

import lombok.RequiredArgsConstructor;

/**
 * CareRequestCommentRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaCareRequestCommentAdapter implements CareRequestCommentRepository {

    private final SpringDataJpaCareRequestCommentRepository jpaRepository;

    @Override
    public CareRequestComment save(CareRequestComment comment) {
        return jpaRepository.save(comment);
    }

    @Override
    public Optional<CareRequestComment> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(CareRequestComment comment) {
        jpaRepository.delete(comment);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<CareRequestComment> findByCareRequestOrderByCreatedAtAsc(CareRequest careRequest) {
        return jpaRepository.findByCareRequestOrderByCreatedAtAsc(careRequest);
    }

    @Override
    public List<CareRequestComment> findByCareRequestAndIsDeletedFalseOrderByCreatedAtAsc(CareRequest careRequest) {
        return jpaRepository.findByCareRequestAndIsDeletedFalseOrderByCreatedAtAsc(careRequest);
    }

    @Override
    public List<CareRequestComment> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user) {
        return jpaRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
    }
}

