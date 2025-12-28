package com.linkup.Petory.domain.user.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.user.converter.UsersConverter;
import com.linkup.Petory.domain.user.dto.PetDTO;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.dto.UserPageResponseDTO;
import com.linkup.Petory.domain.user.entity.EmailVerificationPurpose;
import com.linkup.Petory.domain.user.entity.UserStatus;
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
    private final PetService petService;
    private final EmailVerificationService emailVerificationService;

    /**
     * 전체 사용자 조회 (관리자용)
     * - AdminUserController에서 사용
     */
    public List<UsersDTO> getAllUsers() {
        return usersConverter.toDTOList(usersRepository.findAll());
    }

    /**
     * 전체 사용자 조회 (페이징 지원, 관리자용)
     * - AdminUserController에서 사용
     */
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

    // 단일 조회 (펫 정보 포함)
    public UsersDTO getUser(long idx) {
        return getUserWithPets(idx);
    }

    // username으로 조회
    public UsersDTO getUserByUsername(String username) {
        Users user = usersRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found with username: " + username));
        return usersConverter.toDTO(user);
    }

    // id로 조회 (로그인용)
    public UsersDTO getUserById(String id) {
        Users user = usersRepository.findByIdString(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
        return usersConverter.toDTO(user);
    }

    // 생성
    @Transactional
    public UsersDTO createUser(UsersDTO dto) {
        // 닉네임 필수 검증
        String nickname = dto.getNickname();
        if (nickname == null || nickname.trim().isEmpty()) {
            throw new RuntimeException("닉네임은 필수입니다.");
        }

        if (nickname.length() > 50) {
            throw new RuntimeException("닉네임은 50자 이하여야 합니다.");
        }

        // 닉네임 중복 검사
        usersRepository.findByNickname(nickname)
                .ifPresent(existingUser -> {
                    throw new RuntimeException("이미 사용 중인 닉네임입니다.");
                });

        // username 중복 검사
        usersRepository.findByUsername(dto.getUsername())
                .ifPresent(existingUser -> {
                    throw new RuntimeException("이미 사용 중인 사용자명입니다.");
                });

        // email 중복 검사
        usersRepository.findByEmail(dto.getEmail())
                .ifPresent(existingUser -> {
                    throw new RuntimeException("이미 사용 중인 이메일입니다.");
                });

        Users user = usersConverter.toEntity(dto);

        // 비밀번호 암호화
        if (dto.getPassword() != null && !dto.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(dto.getPassword()));
        } else {
            throw new RuntimeException("비밀번호는 필수입니다.");
        }

        // 일반 회원가입은 소셜 로그인 필드들은 null로 유지
        user.setProfileImage(null);
        user.setBirthDate(null);
        user.setGender(null);

        // 회원가입 전 이메일 인증 완료 여부 확인
        boolean preVerified = emailVerificationService.isPreRegistrationEmailVerified(dto.getEmail());
        user.setEmailVerified(preVerified); // 회원가입 전 인증 완료했으면 true, 아니면 false

        Users saved;
        try {
            saved = usersRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            // DB Unique 제약조건 위반 (Race Condition 발생 시)
            String errorMessage = e.getMessage();
            if (errorMessage != null) {
                if (errorMessage.contains("nickname") || errorMessage.contains("nick_name")) {
                    throw new RuntimeException("이미 사용 중인 닉네임입니다.");
                } else if (errorMessage.contains("username") || errorMessage.contains("user_name")) {
                    throw new RuntimeException("이미 사용 중인 사용자명입니다.");
                } else if (errorMessage.contains("email")) {
                    throw new RuntimeException("이미 사용 중인 이메일입니다.");
                } else if (errorMessage.contains("id")) {
                    throw new RuntimeException("이미 사용 중인 아이디입니다.");
                }
            }
            // 알 수 없는 제약조건 위반
            throw new RuntimeException("이미 사용 중인 정보가 있습니다. 다른 값을 사용해주세요.", e);
        }

        // 회원가입 전 이메일 인증을 완료한 경우 Redis에서 인증 상태 삭제
        if (preVerified) {
            emailVerificationService.removePreRegistrationVerification(dto.getEmail());
            log.info("회원가입 완료 및 이메일 인증 상태 적용: userId={}, email={}", saved.getId(), saved.getEmail());
        } else {
            // 이메일 인증 안 했으면 회원가입 후 인증 메일 발송
            try {
                emailVerificationService.sendVerificationEmail(saved.getId(), EmailVerificationPurpose.REGISTRATION);
                log.info("회원가입 후 이메일 인증 메일 발송: userId={}, email={}", saved.getId(), saved.getEmail());
            } catch (Exception e) {
                log.error("회원가입 이메일 인증 메일 발송 실패: userId={}, error={}", saved.getId(), e.getMessage(), e);
                // 이메일 발송 실패해도 회원가입은 성공으로 처리
            }
        }

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
        if (dto.getNickname() != null && !dto.getNickname().isEmpty()) {
            // 닉네임 중복 확인
            usersRepository.findByNickname(dto.getNickname())
                    .ifPresent(existingUser -> {
                        if (!existingUser.getId().equals(user.getId())) {
                            throw new RuntimeException("이미 사용 중인 닉네임입니다.");
                        }
                    });
            user.setNickname(dto.getNickname());
        }
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

    // 탈퇴 (소프트 삭제)
    public void deleteUser(long idx) {
        Users user = usersRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setIsDeleted(true);
        user.setDeletedAt(java.time.LocalDateTime.now());
        usersRepository.save(user);
    }

    /**
     * 계정 복구 (관리자용)
     * - AdminUserController에서 사용
     */
    public UsersDTO restoreUser(long idx) {
        Users user = usersRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setIsDeleted(false);
        user.setDeletedAt(null);
        Users restored = usersRepository.save(user);
        return usersConverter.toDTO(restored);
    }

    /**
     * 사용자 상태 관리 (관리자용)
     * - 상태, 경고 횟수, 정지 기간만 업데이트
     * - AdminUserController에서 사용
     */
    public UsersDTO updateUserStatus(long idx, UsersDTO dto) {
        Users user = usersRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 상태 업데이트
        if (dto.getStatus() != null) {
            user.setStatus(Enum.valueOf(UserStatus.class, dto.getStatus()));
        }

        // 경고 횟수 업데이트
        if (dto.getWarningCount() != null) {
            user.setWarningCount(dto.getWarningCount());
        }

        // 정지 기간 업데이트
        if (dto.getSuspendedUntil() != null) {
            user.setSuspendedUntil(dto.getSuspendedUntil());
        }

        // 역할 업데이트 (일반 사용자 → ADMIN 승격만)
        if (dto.getRole() != null) {
            // ADMIN/MASTER 역할 변경은 AdminUserManagementController에서만 가능
            if (!dto.getRole().equals("ADMIN") && !dto.getRole().equals("MASTER")) {
                user.setRole(Enum.valueOf(com.linkup.Petory.domain.user.entity.Role.class, dto.getRole()));
            }
        }

        Users updated = usersRepository.save(user);
        return usersConverter.toDTO(updated);
    }

    // ========== 일반 사용자용 프로필 관리 메서드 ==========

    /**
     * 자신의 프로필 조회 (펫 정보 포함)
     */
    @Transactional(readOnly = true)
    public UsersDTO getMyProfile(String userId) {
        Users user = usersRepository.findByIdString(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        UsersDTO userDTO = usersConverter.toDTO(user);

        // 펫 정보 추가
        try {
            List<PetDTO> pets = petService.getPetsByUserId(userId);
            userDTO.setPets(pets);
        } catch (Exception e) {
            // 펫 정보 조회 실패해도 사용자 정보는 반환
            log.warn("펫 정보 조회 실패: {}", e.getMessage());
            userDTO.setPets(List.of());
        }

        return userDTO;
    }

    /**
     * 사용자 프로필 조회 (펫 정보 포함, 관리자용)
     * - AdminUserController에서 사용
     */
    @Transactional(readOnly = true)
    public UsersDTO getUserWithPets(Long userIdx) {
        Users user = usersRepository.findById(userIdx)
                .orElseThrow(() -> new RuntimeException("User not found"));
        UsersDTO userDTO = usersConverter.toDTO(user);

        // 펫 정보 추가
        try {
            List<PetDTO> pets = petService.getPetsByUserIdx(userIdx);
            userDTO.setPets(pets);
        } catch (Exception e) {
            log.warn("펫 정보 조회 실패: {}", e.getMessage());
            userDTO.setPets(List.of());
        }

        return userDTO;
    }

    /**
     * 자신의 프로필 수정 (닉네임, 이메일, 전화번호, 위치, 펫 정보 등)
     * 역할, 상태 등은 수정 불가
     */
    public UsersDTO updateMyProfile(String userId, UsersDTO dto) {
        Users user = usersRepository.findByIdString(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 일반 사용자가 수정 가능한 필드만 업데이트
        if (dto.getUsername() != null && !dto.getUsername().isEmpty()) {
            // 닉네임 중복 확인
            usersRepository.findByUsername(dto.getUsername())
                    .ifPresent(existingUser -> {
                        if (!existingUser.getId().equals(userId)) {
                            throw new RuntimeException("이미 사용 중인 닉네임입니다.");
                        }
                    });
            user.setUsername(dto.getUsername());
        }
        if (dto.getEmail() != null && !dto.getEmail().isEmpty()) {
            // 이메일 중복 확인
            usersRepository.findByEmail(dto.getEmail())
                    .ifPresent(existingUser -> {
                        if (!existingUser.getId().equals(userId)) {
                            throw new RuntimeException("이미 사용 중인 이메일입니다.");
                        }
                    });
            user.setEmail(dto.getEmail());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }
        if (dto.getLocation() != null) {
            user.setLocation(dto.getLocation());
        }
        if (dto.getPetInfo() != null) {
            user.setPetInfo(dto.getPetInfo());
        }

        Users updated = usersRepository.save(user);
        return usersConverter.toDTO(updated);
    }

    /**
     * 비밀번호 변경
     */
    public void changePassword(String userId, String currentPassword, String newPassword) {
        // 이메일 인증 확인
        emailVerificationService.checkEmailVerification(userId);

        Users user = usersRepository.findByIdString(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new RuntimeException("현재 비밀번호가 일치하지 않습니다.");
        }

        // 새 비밀번호 설정
        user.setPassword(passwordEncoder.encode(newPassword));
        usersRepository.save(user);
    }

    /**
     * 닉네임 변경
     */
    public UsersDTO updateMyUsername(String userId, String newUsername) {
        Users user = usersRepository.findByIdString(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 닉네임 중복 확인
        usersRepository.findByUsername(newUsername)
                .ifPresent(existingUser -> {
                    if (!existingUser.getId().equals(userId)) {
                        throw new RuntimeException("이미 사용 중인 닉네임입니다.");
                    }
                });

        user.setUsername(newUsername);
        Users updated = usersRepository.save(user);
        return usersConverter.toDTO(updated);
    }

    /**
     * 닉네임 설정 (소셜 로그인 사용자용)
     */
    @Transactional
    public UsersDTO setNickname(String userId, String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            throw new IllegalArgumentException("닉네임을 입력해주세요.");
        }

        if (nickname.length() > 50) {
            throw new IllegalArgumentException("닉네임은 50자 이하여야 합니다.");
        }

        Users user = usersRepository.findByIdString(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 닉네임 중복 확인
        usersRepository.findByNickname(nickname)
                .ifPresent(existingUser -> {
                    if (!existingUser.getId().equals(userId)) {
                        throw new RuntimeException("이미 사용 중인 닉네임입니다.");
                    }
                });

        user.setNickname(nickname);
        Users updated = usersRepository.save(user);
        return usersConverter.toDTO(updated);
    }

    /**
     * 닉네임 중복 검사
     */
    public boolean checkNicknameAvailability(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            return false;
        }
        return usersRepository.findByNickname(nickname).isEmpty();
    }

    /**
     * 아이디 중복 검사
     */
    public boolean checkIdAvailability(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        return usersRepository.findByIdString(id).isEmpty();
    }
}
