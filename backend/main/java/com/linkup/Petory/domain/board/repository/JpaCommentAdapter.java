package com.linkup.Petory.domain.board.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.board.entity.Board;
import com.linkup.Petory.domain.board.entity.Comment;
import com.linkup.Petory.domain.user.entity.Users;

import lombok.RequiredArgsConstructor;

/**
 * CommentRepository의 JPA 구현체(어댑터)입니다.
 * 
 * 이 클래스는 Spring Data JPA를 사용하여 CommentRepository 인터페이스를 구현합니다.
 * 나중에 다른 DB나 DBMS로 변경할 경우, 이 어댑터와 유사한 새 클래스를 만들고
 * 
 * @Primary 어노테이션을 옮기면 됩니다.
 * 
 *          예시:
 *          - MyBatis로 변경: MyBatisCommentAdapter 생성 후 @Primary 이동
 *          - MongoDB로 변경: MongoCommentAdapter 생성 후 @Primary 이동
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaCommentAdapter implements CommentRepository {

    private final SpringDataJpaCommentRepository jpaRepository;

    @Override
    public Comment save(Comment comment) {
        return jpaRepository.save(comment);
    }

    @Override
    public Optional<Comment> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public boolean existsById(Long id) {
        return jpaRepository.existsById(id);
    }

    @Override
    public void delete(Comment comment) {
        jpaRepository.delete(comment);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<Comment> findByBoardOrderByCreatedAtAsc(Board board) {
        return jpaRepository.findByBoardOrderByCreatedAtAsc(board);
    }

    @Override
    public List<Comment> findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(Board board) {
        return jpaRepository.findByBoardAndIsDeletedFalseOrderByCreatedAtAsc(board);
    }

    @Override
    public List<Comment> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(Users user) {
        return jpaRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
    }

    @Override
    public List<Object[]> countByBoardsAndIsDeletedFalse(List<Long> boardIds) {
        return jpaRepository.countByBoardsAndIsDeletedFalse(boardIds);
    }

    @Override
    public List<Comment> findByBoardAndIsDeletedFalseForAdmin(Board board) {
        return jpaRepository.findByBoardAndIsDeletedFalseForAdmin(board);
    }

    @Override
    public Page<Comment> findByBoardIdAndIsDeletedFalseOrderByCreatedAtAsc(Long boardId, Pageable pageable) {
        return jpaRepository.findByBoardIdAndIsDeletedFalseOrderByCreatedAtAsc(boardId, pageable);
    }
}

