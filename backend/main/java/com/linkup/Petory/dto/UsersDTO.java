package com.linkup.Petory.dto;

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
    private String role;
    private String location;
    private String petInfo;
    private List<SocialUserDTO> socialUsers;
}