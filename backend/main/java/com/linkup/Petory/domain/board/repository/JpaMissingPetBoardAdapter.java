package com.linkup.Petory.domain.board.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.board.entity.MissingPetBoard;
import com.linkup.Petory.domain.board.entity.MissingPetStatus;
import com.linkup.Petory.domain.user.entity.Users;

import lombok.RequiredArgsConstructor;

/**
 * MissingPetBoardRepository의 JPA 구현체(어댑터)입니다.
 * 
 * 이 클래스는 Spring Data JPA를 사용하여 MissingPetBoardRepository 인터페이스를 구현합니다.
 * 나중에 다른 DB나 DBMS로 변경할 경우, 이 어댑터와 유사한 새 클래스를 만들고
 * 
 * @Primary 어노테이션을 옮기면 됩니다.
 * 
 *          예시:
 *          - MyBatis로 변경: MyBatisMissingPetBoardAdapter 생성 후 @Primary 이동
 *          - MongoDB로 변경: MongoMissingPetBoardAdapter 생성 후 @Primary 이동
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaMissingPetBoardAdapter implements MissingPetBoardRepository {

    private final SpringDataJpaMissingPetBoardRepository jpaRepository;

    @Override
    public MissingPetBoard save(MissingPetBoard board) {
        return jpaRepository.save(board);
    }

    @Override
    public MissingPetBoard saveAndFlush(MissingPetBoard board) {
        return jpaRepository.saveAndFlush(board);
    }

    @Override
    public Optional<MissingPetBoard> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public void flush() {
        jpaRepository.flush();
    }

    @Override
    public void delete(MissingPetBoard board) {
        jpaRepository.delete(board);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<MissingPetBoard> findAllByOrderByCreatedAtDesc() {
        return jpaRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    public List<MissingPetBoard> findByStatusOrderByCreatedAtDesc(MissingPetStatus status) {
        return jpaRepository.findByStatusOrderByCreatedAtDesc(status);
    }

    @Override
    public Optional<MissingPetBoard> findByIdWithUser(Long id) {
        return jpaRepository.findByIdWithUser(id);
    }

    @Override
    public List<MissingPetBoard> findAllWithCommentsByOrderByCreatedAtDesc() {
        return jpaRepository.findAllWithCommentsByOrderByCreatedAtDesc();
    }

    @Override
    public List<MissingPetBoard> findByStatusWithCommentsOrderByCreatedAtDesc(MissingPetStatus status) {
        return jpaRepository.findByStatusWithCommentsOrderByCreatedAtDesc(status);
    }

    @Override
    public Optional<MissingPetBoard> findByIdWithComments(Long id) {
        return jpaRepository.findByIdWithComments(id);
    }

    @Override
    public List<MissingPetBoard> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user) {
        return jpaRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
    }
}

