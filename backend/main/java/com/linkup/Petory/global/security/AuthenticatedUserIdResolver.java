package com.linkup.Petory.global.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.UnauthenticatedException;
import com.linkup.Petory.domain.user.exception.UserNotFoundException;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;

/**
 * REST 인증 주체({@link Authentication#getName()})는 로그인용 {@link Users#getId()} 문자열이다.
 * DB FK·채팅 참여자 등에 쓰이는 숫자 PK는 {@link Users#getIdx()}이므로 여기서 변환한다.
 */
@Component
@RequiredArgsConstructor
public class AuthenticatedUserIdResolver {

    private final UsersRepository usersRepository;

    public long requireCurrentUserIdx() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new UnauthenticatedException("인증되지 않은 사용자입니다.");
        }
        String loginId = authentication.getName();
        Users user = usersRepository.findByIdString(loginId)
                .orElseThrow(UserNotFoundException::new);
        return user.getIdx();
    }
}
