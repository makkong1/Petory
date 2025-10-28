package com.linkup.Petory.service;

import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.converter.UsersConverter;
import com.linkup.Petory.dto.UsersDTO;
import com.linkup.Petory.entity.Users;
import com.linkup.Petory.repository.UsersRepository;

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

    // 단일 조회
    public UsersDTO getUser(Long idx) {
        Users user = usersRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("User not found"));
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
    public UsersDTO updateUser(Long idx, UsersDTO dto) {
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
            user.setRole(Enum.valueOf(com.linkup.Petory.entity.Role.class, dto.getRole()));
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

    // 삭제
    public void deleteUser(Long idx) {
        usersRepository.deleteById(idx);
    }
}
