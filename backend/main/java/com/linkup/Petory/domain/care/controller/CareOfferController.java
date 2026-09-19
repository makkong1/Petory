package com.linkup.Petory.domain.care.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.care.dto.CareApplicationDTO;
import com.linkup.Petory.domain.care.dto.CareProviderSearchDTO;
import com.linkup.Petory.domain.care.service.CareOfferService;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;

import lombok.RequiredArgsConstructor;

/**
 * 케어 계약 체결 API. 요청자가 제안하고 제공자가 수락/거절한다.
 *
 * 예전에는 채팅방 API(`POST /api/conversations/{idx}/confirm-deal`)가 이 일을 했다.
 * 계약이 채팅방에 묶여 있어 방 타입이 어긋나면 경로 전체가 조용히 멈췄다 — 계약을 care 로
 * 옮기면서 엔드포인트도 같이 옮겼다.
 */
@RestController
@RequestMapping("/api/care-offers")
@RequiredArgsConstructor
public class CareOfferController {

    private final CareOfferService careOfferService;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    private Long getCurrentUserId() {
        return authenticatedUserIdResolver.requireCurrentUserIdx();
    }

    /** 요청자가 제공자에게 케어를 제안한다. 같은 제공자에게 두 번 보내면 기존 제안을 돌려준다. */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CareApplicationDTO> offer(
            @RequestParam("careRequestIdx") Long careRequestIdx,
            @RequestParam("providerIdx") Long providerIdx) {
        return ResponseEntity.ok(
                careOfferService.offerTo(careRequestIdx, providerIdx, getCurrentUserId()));
    }

    /** 제공자가 제안을 수락한다. 여기서 계약이 성립하고 에스크로 지급 대상이 배정된다. */
    @PostMapping("/{offerIdx}/accept")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CareApplicationDTO> accept(@PathVariable("offerIdx") Long offerIdx) {
        return ResponseEntity.ok(careOfferService.acceptOffer(offerIdx, getCurrentUserId()));
    }

    /** 제공자가 제안을 거절한다. 요청은 모집 중 그대로다. */
    @PostMapping("/{offerIdx}/reject")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CareApplicationDTO> reject(@PathVariable("offerIdx") Long offerIdx) {
        return ResponseEntity.ok(careOfferService.rejectOffer(offerIdx, getCurrentUserId()));
    }

    /**
     * 상대와 나 사이에 살아 있는 제안. 채팅방에서 계약 UI 를 띄울 때 쓴다 —
     * 방 자체는 계약을 모르므로 방 번호가 아니라 상대 사용자로 찾는다.
     */
    @GetMapping("/between")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<CareApplicationDTO>> between(
            @RequestParam("otherUserIdx") Long otherUserIdx) {
        return ResponseEntity.ok(
                careOfferService.findLiveOffersBetween(getCurrentUserId(), otherUserIdx));
    }

    /**
     * 이 요청에 제안할 수 있는 제공자 목록(요청자 전용).
     * 지역만 맞추고 정렬하지 않는다 — 평점·완료건수는 화면 표시용이다.
     */
    @GetMapping("/candidates")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CareProviderSearchDTO> candidates(
            @RequestParam("careRequestIdx") Long careRequestIdx) {
        return ResponseEntity.ok(
                careOfferService.findCandidates(careRequestIdx, getCurrentUserId()));
    }
}
