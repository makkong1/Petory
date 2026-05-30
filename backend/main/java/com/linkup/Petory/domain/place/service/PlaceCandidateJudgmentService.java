package com.linkup.Petory.domain.place.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.place.entity.*;
import com.linkup.Petory.domain.place.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlaceCandidateJudgmentService {

    private final PlaceCandidateRepository candidateRepo;
    private final PlaceRepository placeRepo;
    private final NameQualityChecker nameChecker;
    private final PublicDataMatcher publicDataMatcher;
    private final ObjectMapper objectMapper;

    private static final Pattern ALT_BUSINESS_PATTERN = Pattern.compile(
        "(?:B?\\d+층|지하\\s*\\d+층)\\s+([가-힣a-zA-Z0-9]+(?:\\s[가-힣a-zA-Z0-9]+)?)\\s*$"
    );

    @Transactional
    public void judge(PlaceCandidate candidate) {
        Map<String, Object> bd = new LinkedHashMap<>();
        String rawName = candidate.getRawName() != null ? candidate.getRawName().trim() : null;
        String rawAddress = candidate.getRawAddress();
        Double lat = candidate.getLat();
        Double lng = candidate.getLng();

        // === Gate 1: Hard Reject + Risk Flag ===
        NameQualityChecker.NameCheckResult nameResult = nameChecker.check(rawName);
        if (nameResult == NameQualityChecker.NameCheckResult.HARD_REJECT) {
            doReject(candidate, "hard_reject:name", bd); return;
        }
        boolean hasAddress = StringUtils.hasText(rawAddress);
        boolean hasCoords = lat != null && lng != null;
        if (!hasAddress && !hasCoords) {
            doReject(candidate, "hard_reject:no_address_no_coords", bd); return;
        }
        boolean riskFlag = (nameResult == NameQualityChecker.NameCheckResult.SOFT_RISK);
        bd.put("risk_flag", riskFlag);

        // === Gate 2: Strong Match (risk_flag 있어도 통과 가능) ===
        Optional<PublicDataMatcher.MatchResult> strong =
            publicDataMatcher.findStrongMatch(rawName, rawAddress, lat, lng);
        if (strong.isPresent()) {
            doAutoApprove(candidate, 0.9, strong.get().getLocationServiceId(),
                "strong_match:public_data", bd); return;
        }
        // Path B: self-trust
        if (!riskFlag && nameChecker.isGoodQuality(rawName) && hasAddress && hasCoords) {
            int dup = candidateRepo.countByRawNameAndRawAddress(rawName, rawAddress);
            int src = candidateRepo.countDistinctSourcesByRawNameAndAddress(rawName, rawAddress);
            if (dup >= 3 || src >= 2) {
                doAutoApprove(candidate, 0.9, null, "strong_match:self_trust", bd); return;
            }
        }

        // === Gate 3: Scoring ===
        double score = 0.0;

        double nq = nameChecker.isGoodQuality(rawName) ? 0.1 : 0.0;
        bd.put("name_quality", nq); score += nq;

        double addrScore = hasAddress ? 0.2 : 0.0;
        bd.put("road_address", addrScore); score += addrScore;

        double coordScore = hasCoords ? 0.1 : 0.0;
        bd.put("coord_exists", coordScore); score += coordScore;

        boolean nameInAddr = hasAddress && rawName != null && rawAddress.contains(rawName);
        double niaScore = nameInAddr ? 0.2 : 0.0;
        bd.put("name_in_address", niaScore); score += niaScore;

        boolean altBiz = detectAltBusiness(rawName, rawAddress);
        double altPenalty = altBiz ? -0.3 : 0.0;
        bd.put("alt_business_detected", altPenalty); score += altPenalty;

        Optional<PublicDataMatcher.MatchResult> medium =
            publicDataMatcher.findMediumMatch(rawName, rawAddress, lat, lng);
        double medScore = medium.isPresent() ? 0.3 : 0.0;
        bd.put("public_medium_match", medScore); score += medScore;

        int dup = candidateRepo.countByRawNameAndRawAddress(
            rawName != null ? rawName : "", rawAddress != null ? rawAddress : "");
        double dupBoost = Math.min(Math.log(dup + 1) * 0.1, 0.2);
        bd.put("duplicate_boost", dupBoost); score += dupBoost;

        score = Math.round(score * 1000.0) / 1000.0;
        bd.put("total", score);

        // === Gate 4: Threshold ===
        candidate.setConfidenceScore(score);

        boolean mediumNameOk = !medium.isPresent() || medium.get().getNameSimilarity() >= 0.6;
        boolean canAutoApprove = score >= 0.6 && nq > 0 && !altBiz && !riskFlag && mediumNameOk;

        if (canAutoApprove) {
            Long lsId = medium.map(PublicDataMatcher.MatchResult::getLocationServiceId).orElse(null);
            doAutoApprove(candidate, score, lsId, "threshold_passed", bd);
        } else if (score >= 0.3) {
            doNeedsReview(candidate, "score_below_auto_threshold", bd);
        } else {
            doReject(candidate, "score_too_low", bd);
        }
    }

    private void doAutoApprove(PlaceCandidate c, double score, Long lsId,
                                String reason, Map<String, Object> bd) {
        bd.put("gate", score == 0.9 ? "GATE2_STRONG_MATCH" : "GATE4_THRESHOLD");
        bd.put("decision", "AUTO_APPROVED");

        Place place = placeRepo.save(Place.builder()
            .name(c.getRawName()).address(c.getRawAddress())
            .lat(c.getLat()).lng(c.getLng())
            .status(PlaceStatus.PENDING)
            .primarySource(c.getCollectedFrom())
            .confidence(score)
            .build());

        c.setDecisionStatus(CandidateDecisionStatus.AUTO_APPROVED);
        c.setConfidenceScore(score);
        c.setDecisionReason(reason);
        c.setScoreBreakdown(toJson(bd));
        c.setMatchedPlaceId(place.getId());
        c.setMatchedLocationserviceId(lsId);
        candidateRepo.save(c);
        log.info("[Judgment] AUTO_APPROVED id={} name={} reason={}", c.getId(), c.getRawName(), reason);
    }

    private void doNeedsReview(PlaceCandidate c, String reason, Map<String, Object> bd) {
        bd.put("decision", "NEEDS_REVIEW");
        c.setDecisionStatus(CandidateDecisionStatus.NEEDS_REVIEW);
        c.setDecisionReason(reason);
        c.setScoreBreakdown(toJson(bd));
        candidateRepo.save(c);
    }

    private void doReject(PlaceCandidate c, String reason, Map<String, Object> bd) {
        bd.put("decision", "REJECTED");
        c.setDecisionStatus(CandidateDecisionStatus.REJECTED);
        c.setDecisionReason(reason);
        c.setRejectionReason(reason);
        c.setScoreBreakdown(toJson(bd));
        candidateRepo.save(c);
    }

    private boolean detectAltBusiness(String rawName, String rawAddress) {
        if (!StringUtils.hasText(rawAddress) || !StringUtils.hasText(rawName)) return false;
        Matcher m = ALT_BUSINESS_PATTERN.matcher(rawAddress.trim());
        if (m.find()) {
            String detected = m.group(1).trim();
            return !detected.equals(rawName.trim());
        }
        return false;
    }

    private String toJson(Map<String, Object> map) {
        try { return objectMapper.writeValueAsString(map); }
        catch (Exception e) { return "{}"; }
    }
}
