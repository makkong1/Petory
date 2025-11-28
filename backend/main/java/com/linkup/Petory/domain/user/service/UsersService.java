package com.linkup.Petory.domain.user.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.user.converter.UsersConverter;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.dto.UserPageResponseDTO;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UsersService {

    private final UsersRepository usersRepository;
    private final UsersConverter usersConverter;
    private final PasswordEncoder passwordEncoder;

    // 전체 조회
    public List<UsersDTO> getAllUsers() {
        return usersConverter.toDTOList(usersRepository.findAll());
    }

    // 전체 조회 (페이징 지원)
    @Transactional(readOnly = true)
    public UserPageResponseDTO getAllUsersWithPaging(int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Users> userPage = usersRepository.findAll(pageable);

        if (userPage.isEmpty()) {
            return UserPageResponseDTO.builder()
                    .users(List.of())
                    .totalCount(0)
                    .totalPages(0)
                    .currentPage(page)
                    .pageSize(size)
                    .hasNext(false)
                    .hasPrevious(false)
                    .build();
        }

        List<UsersDTO> userDTOs = usersConverter.toDTOList(userPage.getContent());

        return UserPageResponseDTO.builder()
                .users(userDTOs)
                .totalCount(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .currentPage(page)
                .pageSize(size)
                .hasNext(userPage.hasNext())
                .hasPrevious(userPage.hasPrevious())
                .build();
    }

    // 단일 조회
    public UsersDTO getUser(long idx) {
        Users user = usersRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return usersConverter.toDTO(user);
    }

    // username으로 조회
    public UsersDTO getUserByUsername(String username) {
        Users user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
        return usersConverter.toDTO(user);
    }

    // id로 조회 (로그인용)
    public UsersDTO getUserById(String id) {
        Users user = usersRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        return usersConverter.toDTO(user);
    }

    // 생성
    public UsersDTO createUser(UsersDTO dto) {
        Users user = usersConverter.toEntity(dto);

        // 비밀번호 암호화
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        Users saved = usersRepository.save(user);
        return usersConverter.toDTO(saved);
    }

    // 수정
    public UsersDTO updateUser(long idx, UsersDTO dto) {
        Users user = usersRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 기본 정보 업데이트
        if (dto.getId() != null)
            user.setId(dto.getId());
        if (dto.getUsername() != null)
            user.setUsername(dto.getUsername());
        if (dto.getEmail() != null)
            user.setEmail(dto.getEmail());
        if (dto.getRole() != null) {
            user.setRole(Enum.valueOf(com.linkup.Petory.domain.user.entity.Role.class, dto.getRole()));
        }
        if (dto.getLocation() != null)
            user.setLocation(dto.getLocation());
        if (dto.getPetInfo() != null)
            user.setPetInfo(dto.getPetInfo());

        // 비밀번호 업데이트 (값이 있을 때만)
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        Users updated = usersRepository.save(user);
        return usersConverter.toDTO(updated);
    }

    // 탈퇴
    public void deleteUser(long idx) {
        usersRepository.deleteById(idx);
    }
}
