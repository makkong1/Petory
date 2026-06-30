package com.linkup.Petory.global.security;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;

public class CustomUserDetails implements UserDetails {

    private final Long idx;
    private final String loginId;
    private final String password;
    private final Role role;
    private final Boolean emailVerified;
    private final UserStatus status;
    private final LocalDateTime suspendedUntil;
    private final Collection<? extends GrantedAuthority> authorities;

    private CustomUserDetails(Long idx, String loginId, String password,
            Role role, Boolean emailVerified, UserStatus status, LocalDateTime suspendedUntil) {
        this.idx = idx;
        this.loginId = loginId;
        this.password = password;
        this.role = role;
        this.emailVerified = emailVerified;
        this.status = status;
        this.suspendedUntil = suspendedUntil;
        this.authorities = Collections.singleton(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    public static CustomUserDetails from(Users user) {
        return new CustomUserDetails(
                user.getIdx(),
                user.getId(),
                user.getPassword(),
                user.getRole(),
                user.getEmailVerified(),
                user.getStatus(),
                user.getSuspendedUntil());
    }

    public Long getIdx() {
        return idx;
    }

    public String getLoginId() {
        return loginId;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEmailVerified() {
        return Boolean.TRUE.equals(emailVerified);
    }

    public boolean isAdmin() {
        return role == Role.ADMIN || role == Role.MASTER;
    }

    public boolean isMaster() {
        return role == Role.MASTER;
    }

    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.BANNED;
    }

    /** status=SUSPENDED이고 suspendedUntil이 현재 시각 이후인 경우 true */
    public boolean isCurrentlySuspended() {
        return status == UserStatus.SUSPENDED
                && suspendedUntil != null
                && LocalDateTime.now().isBefore(suspendedUntil);
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE;
    }
}
