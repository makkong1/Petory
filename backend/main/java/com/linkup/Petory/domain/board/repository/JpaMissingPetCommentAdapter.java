package com.linkup.Petory.domain.board.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetComment;
import com.linkup.Petory.domain.user.entity.Users;

import lombok.RequiredArgsConstructor;

/**
 * MissingPetCommentRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaMissingPetCommentAdapter implements MissingPetCommentRepository {

    private final SpringDataJpaMissingPetCommentRepository jpaRepository;

    @Override
    public MissingPetComment save(MissingPetComment comment) {
        return jpaRepository.save(comment);
    }

    @Override
    public MissingPetComment saveAndFlush(MissingPetComment comment) {
        return jpaRepository.saveAndFlush(comment);
    }

    @Override
    public Optional<MissingPetComment> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public List<MissingPetComment> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public void flush() {
        jpaRepository.flush();
    }

    @Override
    public void delete(MissingPetComment comment) {
        jpaRepository.delete(comment);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<MissingPetComment> findByBoardOrderByCreatedAtAsc(MissingPetBoard board) {
        return jpaRepository.findByBoardOrderByCreatedAtAsc(board);
    }

    @Override
    public List<MissingPetComment> findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(MissingPetBoard board) {
        return jpaRepository.findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(board);
    }

    @Override
    public List<MissingPetComment> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user) {
        return jpaRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
    }

    @Override
    public List<Object[]> countCommentsByBoardIds(List<Long> boardIds) {
        return jpaRepository.countCommentsByBoardIds(boardIds);
    }

    @Override
    public Page<MissingPetComment> findByBoardIdAndIsDeletedFalseOrderByCreatedAtAsc(Long boardId, Pageable pageable) {
        return jpaRepository.findByBoardIdAndIsDeletedFalseOrderByCreatedAtAsc(boardId, pageable);
    }
}

