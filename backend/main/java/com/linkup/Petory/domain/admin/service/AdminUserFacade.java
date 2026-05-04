// backend/main/java/com/linkup/Petory/domain/admin/service/AdminUserFacade.java
package com.linkup.Petory.domain.admin.service;

import com.linkup.Petory.domain.user.converter.UsersConverter;
import com.linkup.Petory.domain.user.dto.UserPageResponseDTO;
import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.UserNotFoundException;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.user.service.UsersService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserFacade {

    private final UsersRepository usersRepository;
    private final UsersConverter usersConverter;
    private final UsersService usersService;
    private final PasswordEncoder passwordEncoder;
    private final AdminAuditService auditService;

    public UserPageResponseDTO getUsers(String role, String status, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Users> userPage = usersRepository.findAllForAdmin(role, status, keyword, pageable);
        return UserPageResponseDTO.builder()
                .users(usersConverter.toDTOList(userPage.getContent()))
                .totalCount(userPage.getTotalElements())
                .totalPages(userPage.getTotalPages())
                .currentPage(page)
                .pageSize(size)
                .hasNext(userPage.hasNext())
                .hasPrevious(userPage.hasPrevious())
                .build();
    }

    public UsersDTO getUser(Long id) {
        return usersService.getUser(id);
    }

    @Transactional
    public UsersDTO updateStatus(Long targetId, UsersDTO dto, Long adminIdx) {
        UsersDTO result = usersService.updateUserStatus(targetId, dto);
        auditService.log(adminIdx, "USER_STATUS_UPDATE", "USER", targetId,
                "status=" + dto.getStatus());
        return result;
    }

    @Transactional
    public void deleteUser(Long targetId, Long adminIdx) {
        Role role = usersRepository.findRoleByIdx(targetId)
                .orElseThrow(UserNotFoundException::new);
        if (role == Role.ADMIN || role == Role.MASTER) {
            throw new IllegalArgumentException("관리자 계정 삭제는 별도 엔드포인트를 사용해주세요.");
        }
        usersService.deleteUser(targetId);
        auditService.log(adminIdx, "USER_DELETE", "USER", targetId, null);
    }

    @Transactional
    public UsersDTO restoreUser(Long targetId, Long adminIdx) {
        UsersDTO result = usersService.restoreUser(targetId);
        auditService.log(adminIdx, "USER_RESTORE", "USER", targetId, null);
        return result;
    }

    public List<UsersDTO> getAdminUsers() {
        return usersConverter.toDTOList(
                usersRepository.findAllForAdmin("ADMIN", null, null, Pageable.unpaged()).getContent()
        );
    }

    @Transactional
    public UsersDTO createAdminUser(UsersDTO dto, Long masterIdx) {
        if (!"ADMIN".equals(dto.getRole())) {
            throw new IllegalArgumentException("ADMIN 역할만 지정할 수 있습니다.");
        }
        if (dto.getPassword() == null || dto.getPassword().isEmpty()) {
            throw new IllegalArgumentException("비밀번호는 필수입니다.");
        }
        usersRepository.findByIdString(dto.getId()).ifPresent(u -> {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        });
        usersRepository.findByUsername(dto.getUsername()).ifPresent(u -> {
            throw new IllegalArgumentException("이미 존재하는 사용자명입니다.");
        });
        Users user = usersConverter.toEntity(dto);
        user.setRole(Role.ADMIN);
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        Users saved;
        try {
            saved = usersRepository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("이미 존재하는 아이디 또는 사용자명입니다.");
        }
        log.info("MASTER({}) ADMIN 계정 생성: username={}", masterIdx, saved.getUsername());
        auditService.log(masterIdx, "ADMIN_CREATE", "USER", saved.getIdx(), "username=" + saved.getUsername());
        return usersConverter.toDTO(saved);
    }

    @Transactional
    public UsersDTO promoteToAdmin(Long targetId, Long masterIdx) {
        Users user = usersRepository.findById(targetId).orElseThrow(UserNotFoundException::new);
        if (user.getRole() == Role.MASTER) {
            throw new IllegalArgumentException("MASTER 권한은 변경할 수 없습니다.");
        }
        if (user.getRole() == Role.ADMIN) {
            return usersConverter.toDTO(user);
        }
        user.setRole(Role.ADMIN);
        Users updated = usersRepository.save(user);
        auditService.log(masterIdx, "USER_PROMOTE_ADMIN", "USER", targetId, null);
        return usersConverter.toDTO(updated);
    }

    @Transactional
    public void deleteAdminUser(Long targetId, Long masterIdx) {
        Users user = usersRepository.findById(targetId).orElseThrow(UserNotFoundException::new);
        if (user.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("ADMIN 계정만 이 엔드포인트로 삭제할 수 있습니다.");
        }
        user.setIsDeleted(true);
        user.setDeletedAt(java.time.LocalDateTime.now());
        user.setRefreshToken(null);
        user.setRefreshExpiration(null);
        usersRepository.save(user);
        log.warn("MASTER({}) ADMIN 계정 삭제: userId={}", masterIdx, targetId);
        auditService.log(masterIdx, "ADMIN_DELETE", "USER", targetId, "username=" + user.getUsername());
    }

    @Transactional
    public void changeAdminPassword(Long targetId, String newPassword, Long masterIdx) {
        Users user = usersRepository.findById(targetId).orElseThrow(UserNotFoundException::new);
        if (user.getRole() != Role.ADMIN) {
            throw new IllegalArgumentException("ADMIN 계정만 비밀번호를 변경할 수 있습니다.");
        }
        if (newPassword == null || newPassword.isEmpty()) {
            throw new IllegalArgumentException("새 비밀번호는 필수입니다.");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        usersRepository.save(user);
        auditService.log(masterIdx, "ADMIN_PASSWORD_CHANGE", "USER", targetId, null);
    }
}
