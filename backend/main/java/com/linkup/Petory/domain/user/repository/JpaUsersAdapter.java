package com.linkup.Petory.domain.user.repository;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import lombok.RequiredArgsConstructor;

/**
 * UsersRepository의 JPA 구현체(어댑터)입니다.
 * 
 * 이 클래스는 Spring Data JPA를 사용하여 UsersRepository 인터페이스를 구현합니다.
 * 나중에 다른 DB나 DBMS로 변경할 경우, 이 어댑터와 유사한 새 클래스를 만들고
 * 
 * @Primary 어노테이션을 옮기면 됩니다.
 * 
 *          예시:
 *          - MyBatis로 변경: MyBatisUsersAdapter 생성 후 @Primary 이동
 *          - MongoDB로 변경: MongoUsersAdapter 생성 후 @Primary 이동
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaUsersAdapter implements UsersRepository {

    private final SpringDataJpaUsersRepository jpaRepository;

    @Override
    public Users save(Users user) {
        return jpaRepository.save(user);
    }

    @Override
    public Optional<Users> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<Users> findAll() {
        return jpaRepository.findAll();
    }

    @Override
    public Page<Users> findAll(Pageable pageable) {
        return jpaRepository.findAll(pageable);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public Optional<Users> findByUsername(String username) {
        return jpaRepository.findByUsername(username);
    }

    @Override
    public Optional<Users> findByNickname(String nickname) {
        return jpaRepository.findByNickname(nickname);
    }

    @Override
    public Optional<Users> findByEmail(String email) {
        return jpaRepository.findByEmail(email);
    }

    @Override
    public Optional<Users> findByNicknameOrUsernameOrEmail(String nickname, String username, String email) {
        var list = jpaRepository.findByNicknameOrUsernameOrEmail(nickname, username, email, PageRequest.of(0, 1));
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<Users> findByIdString(String id) {
        return jpaRepository.findByIdString(id);
    }

    @Override
    public Optional<Long> findIdxByIdString(String id) {
        return jpaRepository.findIdxByIdString(id);
    }

    @Override
    public Optional<Users> findByRefreshToken(String refreshToken) {
        return jpaRepository.findByRefreshToken(refreshToken);
    }

    @Override
    public long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end) {
        return jpaRepository.countByCreatedAtBetween(start, end);
    }

    @Override
    public long countByLastLoginAtBetween(LocalDateTime start, LocalDateTime end) {
        return jpaRepository.countByLastLoginAtBetween(start, end);
    }

    @Override
    public int incrementWarningCount(Long userId) {
        return jpaRepository.incrementWarningCount(userId);
    }

    @Override
    public Optional<Users> findByIdForUpdate(Long idx) {
        return jpaRepository.findByIdForUpdate(idx);
    }

    @Override
    public Optional<Role> findRoleByIdx(Long idx) {
        return jpaRepository.findRoleByIdx(idx);
    }

    @Override
    public Optional<Users> findByIdWithPets(Long idx) {
        return jpaRepository.findByIdWithPets(idx);
    }

    @Override
    public Optional<Users> findByIdStringWithPets(String userId) {
        return jpaRepository.findByIdStringWithPets(userId);
    }

    @Override
    public Page<Users> findAllForAdmin(String role, String status, String keyword, Pageable pageable) {
        return jpaRepository.findAllForAdmin(role, status, keyword, pageable);
    }

    @Override
    public long countByRoleAndCreatedAtBetween(Role role, LocalDateTime start, LocalDateTime end) {
        return jpaRepository.countByRoleAndCreatedAtBetween(role, start, end);
    }
}
