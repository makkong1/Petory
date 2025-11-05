package com.linkup.Petory.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.entity.Users;

@Repository
public interface UsersRepository extends JpaRepository<Users, Long> {

    Optional<Users> findByUsername(String username);

    // 로그인용 아이디(String 타입 id 필드)로 조회
    // Spring Data JPA는 필드명을 기반으로 처리하므로,
    // 엔티티의 id 필드(String 타입)를 조회함
    Optional<Users> findById(String id);

    // Refresh Token으로 사용자 조회
    Optional<Users> findByRefreshToken(String refreshToken);
}
