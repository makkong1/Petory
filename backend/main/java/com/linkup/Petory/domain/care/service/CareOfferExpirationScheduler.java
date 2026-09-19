package com.linkup.Petory.domain.care.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.repository.CareApplicationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 답 없이 방치된 케어 제안을 정리한다.
 *
 * <p>제안을 받은 제공자가 수락도 거절도 하지 않으면 제안이 영원히 대기 상태로 남는다.
 * 요청자는 답을 기다리는 건지 잊힌 건지 알 수 없고, 제공자 목록에서 그 사람이 계속
 * "제안함"으로 잠겨 있어 다시 보낼 수도 없다.
 *
 * <p>만료시키면 요청자가 움직일 수 있다 — 알림을 받고, 같은 사람에게 다시 보내거나 다른
 * 사람을 고른다(끝난 제안은 {@code reopen} 으로 되살아난다).
 *
 * <p>만료는 <b>제안만</b> 건드린다. 케어 요청은 그대로 모집 중으로 둔다 — 제안이 만료됐다고
 * 요청까지 닫으면 요청자가 원하지 않은 취소가 된다(예정일 경과 처리는 CareRequestScheduler 몫).
 *
 * <p>실제 만료 처리는 {@link CareOfferService#expireOffer} 에 있다. 이 클래스 안에 두고
 * {@code this.} 로 부르면 AOP 프록시를 거치지 않아 트랜잭션이 걸리지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareOfferExpirationScheduler {

    /** 제안을 며칠까지 살려둘지. 짧으면 제공자가 확인할 틈이 없고, 길면 요청자가 묶인다. */
    static final int OFFER_TTL_DAYS = 3;

    private final CareApplicationRepository careApplicationRepository;
    private final CareOfferService careOfferService;

    /**
     * 매시간 5분에 실행. 하루 한 번이면 만료가 최대 24시간 늦어져 "3일"이 실제로는 4일이 된다.
     * 루프 전체를 한 트랜잭션으로 묶지 않는다 — 한 건이 실패하면 나머지까지 롤백된다.
     */
    @Scheduled(cron = "0 5 * * * ?")
    public void expireStaleOffers() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(OFFER_TTL_DAYS);
        List<CareApplication> stale = careApplicationRepository.findPendingOffersBefore(cutoff);
        if (stale.isEmpty()) {
            return;
        }

        int expired = 0;
        for (CareApplication offer : stale) {
            try {
                careOfferService.expireOffer(offer.getIdx(), OFFER_TTL_DAYS);
                expired++;
            } catch (Exception e) {
                log.warn("제안 만료 처리 실패: applicationIdx={}", offer.getIdx(), e);
            }
        }
        log.info("케어 제안 만료 처리: 대상={}, 성공={}, 기준={}", stale.size(), expired, cutoff);
    }
}
