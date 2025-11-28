package com.linkup.Petory.domain.user.converter;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.user.dto.UsersDTO;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.entity.Users.UserStatus;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UsersConverter {

    private final SocialUserConverter socialUserConverter;

    // 단일 DTO 변환 (password 제외 - 보안상 이유)
    public UsersDTO toDTO(Users user) {
        return UsersDTO.builder()
                .idx(user.getIdx())
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                // password는 응답에 포함하지 않음
                .role(user.getRole().name())
                .location(user.getLocation())
                .petInfo(user.getPetInfo())
                .socialUsers(user.getSocialUsers() != null ? user.getSocialUsers().stream()
                        .map(socialUserConverter::toDTO)
                        .collect(Collectors.toList())
                        : null)
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .warningCount(user.getWarningCount())
                .suspendedUntil(user.getSuspendedUntil())
                .isDeleted(user.getIsDeleted())
                .deletedAt(user.getDeletedAt())
                .build();
    }

    // DTO → Entity (password는 Service에서 별도 처리)
    public Users toEntity(UsersDTO dto) {
        Users user = Users.builder()
                .idx(dto.getIdx())
                .id(dto.getId())
                .username(dto.getUsername())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                // password는 Service에서 암호화 후 설정
                .role(Role.valueOf(dto.getRole()))
                .location(dto.getLocation())
                .petInfo(dto.getPetInfo())
                .build();

        if (dto.getSocialUsers() != null) {
            user.setSocialUsers(dto.getSocialUsers().stream()
                    .map(suDto -> socialUserConverter.toEntity(suDto, user))
                    .collect(Collectors.toList()));
        }

        return user;
    }

    // 리스트 변환
    public List<UsersDTO> toDTOList(List<Users> users) {
        return users.stream().map(this::toDTO).collect(Collectors.toList());
    }

    public List<Users> toEntityList(List<UsersDTO> dtos) {
        return dtos.stream().map(this::toEntity).collect(Collectors.toList());
    }
}
