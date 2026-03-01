package com.linkup.Petory.domain.board.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.user.entity.Users;

import lombok.RequiredArgsConstructor;

/**
 * BoardRepository의 JPA 구현체(어댑터)입니다.
 * 
 * 이 클래스는 Spring Data JPA를 사용하여 BoardRepository 인터페이스를 구현합니다.
 * 나중에 다른 DB나 DBMS로 변경할 경우, 이 어댑터와 유사한 새 클래스를 만들고
 * 
 * @Primary 어노테이션을 옮기면 됩니다.
 * 
 *          예시:
 *          - MyBatis로 변경: MyBatisBoardAdapter 생성 후 @Primary 이동
 *          - MongoDB로 변경: MongoBoardAdapter 생성 후 @Primary 이동
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaBoardAdapter implements BoardRepository {

    private final SpringDataJpaBoardRepository jpaRepository;

    @Override
    public Board save(Board board) {
        return jpaRepository.save(board);
    }

    @Override
    public Board saveAndFlush(Board board) {
        return jpaRepository.saveAndFlush(board);
    }

    @Override
    public List<Board> saveAll(List<Board> boards) {
        return jpaRepository.saveAll(boards);
    }

    @Override
    public Optional<Board> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public Optional<Board> findByIdWithUser(Long id) {
        return jpaRepository.findByIdWithUser(id);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public List<Board> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public void delete(Board board) {
        jpaRepository.delete(board);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<Board> findAll(Specification<Board> spec) {
        return jpaRepository.findAll(spec);
    }

    @Override
    public Page<Board> findAll(Specification<Board> spec, Pageable pageable) {
        return jpaRepository.findAll(spec, pageable);
    }

    @Override
    public List<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc() {
        return jpaRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc();
    }

    @Override
    public Page<Board> findAllByIsDeletedFalseOrderByCreatedAtDesc(Pageable pageable) {
        return jpaRepository.findAllByIsDeletedFalseOrderByCreatedAtDesc(pageable);
    }

    @Override
    public List<Board> findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(String category) {
        return jpaRepository.findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(category);
    }

    @Override
    public Page<Board> findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(String category, Pageable pageable) {
        return jpaRepository.findByCategoryAndIsDeletedFalseOrderByCreatedAtDesc(category, pageable);
    }

    @Override
    public List<Board> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user) {
        return jpaRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
    }

    @Override
    public Page<Board> searchByNicknameWithPaging(String nickname, Pageable pageable) {
        return jpaRepository.searchByNicknameWithPaging(nickname, pageable);
    }

    @Override
    public Page<Board> searchByKeywordWithPaging(String keyword, Pageable pageable) {
        return jpaRepository.searchByKeywordWithPaging(keyword, pageable);
    }

    @Override
    public List<Board> findByCategoryAndCreatedAtBetween(String category, LocalDateTime start, LocalDateTime end) {
        return jpaRepository.findByCategoryAndCreatedAtBetween(category, start, end);
    }

    @Override
    public long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end) {
        return jpaRepository.countByCreatedAtBetween(start, end);
    }

    @Override
    public List<Board> findAllByIsDeletedFalseForAdmin() {
        return jpaRepository.findAllByIsDeletedFalseForAdmin();
    }

    @Override
    public List<Board> findAllForAdmin() {
        return jpaRepository.findAllForAdmin();
    }
}

