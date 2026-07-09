package com.linkup.Petory.domain.user.service;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserDormantService {

    private static final long DORMANT_AFTER_YEARS = 1;

    private final UsersRepository usersRepository;

    /**
     * 1년간 미로그인(또는 가입 후 미로그인 상태로 1년 경과)한 활성 사용자를 휴면 전환한다.
     *
     * @return 업데이트된 행 수
     */
    @Transactional
    public int markDormantUsers() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = now.minusYears(DORMANT_AFTER_YEARS);
        int updated = usersRepository.markDormantUsers(cutoff, now);
        log.info("휴면 계정 전환: {}건, cutoff={}", updated, cutoff);
        return updated;
    }
}
