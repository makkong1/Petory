package com.linkup.Petory.domain.user.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.report.entity.ReportActionType;
import com.linkup.Petory.domain.user.entity.UserSanction;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.event.UserSanctionAppliedEvent;
import com.linkup.Petory.domain.user.exception.UserNotFoundException;
import com.linkup.Petory.domain.user.repository.UserSanctionRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class UserSanctionService {

    private final UserSanctionRepository sanctionRepository;
    private final UsersRepository usersRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final int WARNING_THRESHOLD = 3; // 경고 3회 시 자동 이용제한
    private static final int AUTO_SUSPENSION_DAYS = 3; // 자동 이용제한 기간 (일)

    /**
     * 경고 추가
     * 경고 3회 도달 시 자동으로 이용제한 3일 적용
     */
    @SuppressWarnings("null")
    @Transactional
    public UserSanction addWarning(Long userId, String reason, Long adminId, Long reportId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        Users admin = adminId != null ? usersRepository.findById(adminId).orElse(null) : null;

        // 경고 추가
        UserSanction warning = UserSanction.builder()
                .user(user)
                .sanctionType(UserSanction.SanctionType.WARNING)
                .reason(reason)
                .durationDays(null) // 경고는 기간 없음
                .startsAt(LocalDateTime.now())
                .endsAt(null)
                .admin(admin)
                .reportIdx(reportId)
                .build();

        sanctionRepository.save(warning);

        // 경고 횟수 원자적 증가 (동시성 문제 해결)
        usersRepository.incrementWarningCount(userId);

        // 업데이트된 사용자 정보 다시 조회
        user = usersRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        // 경고 3회 이상이면 자동 이용제한
        if (user.getWarningCount() >= WARNING_THRESHOLD) {
            log.info("유저 {} 경고 {}회 도달, 자동 이용제한 {}일 적용", userId, user.getWarningCount(), AUTO_SUSPENSION_DAYS);
            addSuspension(userId,
                    String.format("경고 %d회 누적으로 인한 자동 이용제한", user.getWarningCount()),
                    adminId,
                    reportId,
                    AUTO_SUSPENSION_DAYS);
        }

        return warning;
    }

    /**
     * 이용제한 추가 (일시적)
     */
    @SuppressWarnings("null")
    @Transactional
    public UserSanction addSuspension(Long userId, String reason, Long adminId, Long reportId, int days) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        Users admin = adminId != null ? usersRepository.findById(adminId).orElse(null) : null;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endsAt = now.plusDays(days);

        UserSanction suspension = UserSanction.builder()
                .user(user)
                .sanctionType(UserSanction.SanctionType.SUSPENSION)
                .reason(reason)
                .durationDays(days)
                .startsAt(now)
                .endsAt(endsAt)
                .admin(admin)
                .reportIdx(reportId)
                .build();

        sanctionRepository.save(suspension);

        user.suspend(endsAt);
        clearRefreshToken(user);
        usersRepository.save(user);

        eventPublisher.publishEvent(new UserSanctionAppliedEvent(user.getIdx(), UserStatus.SUSPENDED, endsAt));
        log.info("제재 이벤트 발행(SUSPENDED): userId={}, until={}", user.getIdx(), endsAt);

        return suspension;
    }

    /**
     * 영구 차단
     */
    @SuppressWarnings("null")
    @Transactional
    public UserSanction addBan(Long userId, String reason, Long adminId, Long reportId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        Users admin = adminId != null ? usersRepository.findById(adminId).orElse(null) : null;

        UserSanction ban = UserSanction.builder()
                .user(user)
                .sanctionType(UserSanction.SanctionType.BAN)
                .reason(reason)
                .durationDays(null) // 영구
                .startsAt(LocalDateTime.now())
                .endsAt(null) // 영구
                .admin(admin)
                .reportIdx(reportId)
                .build();

        sanctionRepository.save(ban);

        user.ban();
        clearRefreshToken(user);
        usersRepository.save(user);

        eventPublisher.publishEvent(new UserSanctionAppliedEvent(user.getIdx(), UserStatus.BANNED, null));
        log.info("제재 이벤트 발행(BANNED): userId={}", user.getIdx());

        return ban;
    }

    /**
     * 제재 해제 (관리자 수동)
     */
    @Transactional
    public void releaseSanction(Long userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);

        user.activate();
        usersRepository.save(user);
    }

    /**
     * 만료된 이용제한 자동 해제 (스케줄러에서 호출)
     */
    @Transactional
    public void releaseExpiredSuspensions() {
        List<UserSanction> expired = sanctionRepository.findExpiredSuspensions(LocalDateTime.now());

        for (UserSanction sanction : expired) {
            Users user = sanction.getUser();
            // 다른 활성 제재가 있는지 확인
            List<UserSanction> activeSanctions = sanctionRepository.findActiveSanctionsByUserId(
                    user.getIdx(), LocalDateTime.now());

            boolean hasActiveBan = activeSanctions.stream()
                    .anyMatch(s -> s.getSanctionType() == UserSanction.SanctionType.BAN);

            if (hasActiveBan) {
                // 영구 차단이 있으면 그대로 유지
                continue;
            }

            boolean hasActiveSuspension = activeSanctions.stream()
                    .anyMatch(s -> s.getSanctionType() == UserSanction.SanctionType.SUSPENSION
                            && s.getEndsAt() != null && s.getEndsAt().isAfter(LocalDateTime.now()));

            if (!hasActiveSuspension) {
                user.activate();
                usersRepository.save(user);
                log.info("유저 {} 이용제한 자동 해제", user.getIdx());
            }
        }
    }

    /**
     * 유저의 제재 이력 조회
     */
    public List<UserSanction> getUserSanctions(Long userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(UserNotFoundException::new);
        return sanctionRepository.findByUserOrderByCreatedAtDesc(user);
    }

    /**
     * 신고 처리 시 자동 제재 적용
     */
    @Transactional
    public void applySanctionFromReport(Long userId, ReportActionType actionType, String reason, Long adminId,
            Long reportId) {
        switch (actionType) {
            case WARN_USER -> addWarning(userId, reason, adminId, reportId);
            case SUSPEND_USER -> addSuspension(userId, reason, adminId, reportId, AUTO_SUSPENSION_DAYS);
            default -> {
                // NONE, DELETE_CONTENT, OTHER는 제재 없음
            }
        }
    }

    private void clearRefreshToken(Users user) {
        user.setRefreshToken(null);
        user.setRefreshExpiration(null);
    }
}
