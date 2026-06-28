package com.linkup.Petory.domain.user.service;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.global.security.CustomUserDetails;

import lombok.RequiredArgsConstructor;

/** Spring Security UserDetailsService 구현체. 로그인 ID로 사용자를 조회해 인증에 사용한다. */
@Service
@RequiredArgsConstructor
public class UsersDetailsServiceImpl implements UserDetailsService {

    private final UsersRepository usersRepository;

    @Override
    public UserDetails loadUserByUsername(String id) throws UsernameNotFoundException {
        Users user = usersRepository.findActiveByIdString(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));

        if (user.getStatus() == UserStatus.SUSPENDED
                && (user.getSuspendedUntil() == null || !user.getSuspendedUntil().isAfter(LocalDateTime.now()))) {
            user.activate();
            user = usersRepository.save(user);
        }

        return CustomUserDetails.from(user);
    }
}
