package com.linkup.Petory.domain.care.event;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.user.entity.UserStatus;
import com.linkup.Petory.domain.user.event.UserSanctionAppliedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserSanctionCareEventListener {

    private final CareRequestRepository careRequestRepository;

    /**
     * BANNED 사용자의 OPEN 케어 요청을 CANCELLED로 전환한다.
     * SUSPENDED는 상태 변경 없이 조회 시점 필터(u.status='ACTIVE')로 처리하므로 이 리스너 대상에서 제외.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUserSanctionApplied(UserSanctionAppliedEvent event) {
        if (event.status() != UserStatus.BANNED) {
            return;
        }
        try {
            List<CareRequest> openCares = careRequestRepository.findOpenByUserId(event.userId());
            for (CareRequest care : openCares) {
                care.setStatus(CareRequestStatus.CANCELLED);
                careRequestRepository.save(care);
                log.info("BANNED 사용자 OPEN 케어 취소: careId={}, userId={}", care.getIdx(), event.userId());
            }
            if (!openCares.isEmpty()) {
                log.info("BANNED 사용자 케어 취소 완료: userId={}, 취소건수={}", event.userId(), openCares.size());
            }
        } catch (Exception e) {
            log.error("BANNED 사용자 케어 취소 처리 실패 (관리자 수동 재처리 필요): userId={}, error={}",
                    event.userId(), e.getMessage(), e);
        }
    }
}
