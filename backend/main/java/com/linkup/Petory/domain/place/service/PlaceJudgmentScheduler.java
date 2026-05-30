package com.linkup.Petory.domain.place.service;

import com.linkup.Petory.domain.place.entity.CandidateDecisionStatus;
import com.linkup.Petory.domain.place.entity.PlaceCandidate;
import com.linkup.Petory.domain.place.repository.PlaceCandidateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlaceJudgmentScheduler {

    private final PlaceCandidateRepository candidateRepo;
    private final PlaceCandidateJudgmentService judgmentService;

    /** 5분마다 PENDING 후보를 일괄 판정. fixedDelay로 앞 배치 완료 후 다음 시작 보장. */
    @Scheduled(fixedDelayString = "${place.judgment.delay-ms:300000}")
    public void runJudgment() {
        List<PlaceCandidate> pending = candidateRepo.findByDecisionStatus(CandidateDecisionStatus.PENDING);
        if (pending.isEmpty()) return;
        log.info("[PlaceJudgmentScheduler] 판정 시작 count={}", pending.size());
        int ok = 0, err = 0;
        for (PlaceCandidate c : pending) {
            try { judgmentService.judge(c); ok++; }
            catch (Exception e) { log.error("[PlaceJudgmentScheduler] 판정 실패 id={}", c.getId(), e); err++; }
        }
        log.info("[PlaceJudgmentScheduler] 완료 ok={} err={}", ok, err);
    }
}
