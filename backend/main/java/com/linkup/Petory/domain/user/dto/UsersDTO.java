package com.linkup.Petory.domain.user.dto;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsersDTO {
    private Long idx;
    private String id;
    private String username;
    private String email;
    private String phone;
    private String password; // 요청 시에만 사용, 응답에서는 제외됨
    private String role;
    private String location;
    private String petInfo;
    private List<SocialUserDTO> socialUsers;
    private List<PetDTO> pets; // 등록한 애완동물 목록
    
    // 제재 관련 필드
    private String status; // ACTIVE, SUSPENDED, BANNED
    private Integer warningCount;
    private LocalDateTime suspendedUntil;
    
    // 소프트 삭제 관련 필드
    private Boolean isDeleted;
    private LocalDateTime deletedAt;
}