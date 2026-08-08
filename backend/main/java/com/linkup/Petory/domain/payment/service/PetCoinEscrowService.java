package com.linkup.Petory.domain.payment.service;

import java.math.BigDecimal;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.payment.entity.EscrowStatus;
import com.linkup.Petory.domain.payment.entity.PetCoinEscrow;
import com.linkup.Petory.domain.payment.exception.PaymentConflictException;
import com.linkup.Petory.domain.payment.exception.PaymentValidationException;
import com.linkup.Petory.domain.payment.exception.PetCoinEscrowNotFoundException;
import com.linkup.Petory.domain.payment.event.PaymentRecordedEvent;
import com.linkup.Petory.domain.payment.repository.PetCoinEscrowRepository;
import com.linkup.Petory.domain.user.entity.Users;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 펫코인 에스크로 서비스 역할: 거래 확정 시 코인을 임시 보관하고, 거래 완료 시 제공자에게 지급하거나 취소 시 환불합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetCoinEscrowService {

    private final PetCoinEscrowRepository escrowRepository;
    private final PetCoinService petCoinService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 에스크로 생성 (거래 확정 시)
     *
     * @param careRequest 펫케어 요청
     * @param careApplication 펫케어 지원 (거래 확정된 것)
     * @param requester 요청자
     * @param provider 제공자
     * @param amount 에스크로 금액 (코인 단위)
     * @return 생성된 에스크로
     */
    /**
     * 요청 등록 시 에스크로 생성. 이 시점에는 제공자가 없으므로 지급 대상 없이 금액만 보관한다.
     *
     * 왜 확정이 아니라 등록에서 잡는가: 확정 시 차감이면 제공자가 신청하고 채팅까지 마친 뒤에야
     * 요청자의 잔액 부족이 드러난다. 등록에서 잡아야 "올라와 있는 요청은 지급이 보증된다"가 성립한다.
     */
    @Transactional
    public PetCoinEscrow holdForRequest(CareRequest careRequest, Users requester, Integer amount) {
        if (amount == null || amount <= 0) {
            throw PaymentValidationException.escrowAmountInvalid();
        }

        escrowRepository.findByCareRequest(careRequest)
                .ifPresent(existing -> {
                    throw PaymentConflictException.escrowAlreadyExists();
                });

        petCoinService.deductCoins(
                requester,
                amount,
                "CARE_REQUEST",
                careRequest.getIdx(),
                String.format("펫케어 요청 등록 - 요청 ID: %d", careRequest.getIdx()));

        PetCoinEscrow saved = escrowRepository.save(PetCoinEscrow.builder()
                .careRequest(careRequest)
                .requester(requester)
                .amount(amount)
                .status(EscrowStatus.HOLD)
                .build());

        log.info("에스크로 보관 시작(상대 미정): escrowIdx={}, careRequestIdx={}, amount={}, requesterId={}",
                saved.getIdx(), careRequest.getIdx(), amount, requester.getIdx());

        return saved;
    }

    /**
     * 거래 확정 시 지급 대상 배정. 금액은 이 시점에 바뀌지 않는다 — 확정하는 쪽이 화면에서 본
     * 금액(expectedAmount)과 보관 금액이 다르면 거절한다.
     */
    @Transactional
    public PetCoinEscrow assignProvider(CareRequest careRequest, Users provider,
            CareApplication careApplication, Integer expectedAmount) {
        PetCoinEscrow escrow = escrowRepository.findByCareRequestForUpdate(careRequest)
                .orElseThrow(() -> new PetCoinEscrowNotFoundException());

        if (expectedAmount != null && !expectedAmount.equals(escrow.getAmount())) {
            throw PaymentConflictException.escrowAmountChanged(escrow.getAmount());
        }

        escrow.assignProvider(provider, careApplication);
        PetCoinEscrow saved = escrowRepository.save(escrow);

        log.info("에스크로 지급 대상 배정: escrowIdx={}, careRequestIdx={}, providerId={}, amount={}",
                saved.getIdx(), careRequest.getIdx(), provider.getIdx(), saved.getAmount());

        return saved;
    }

    /**
     * 보관 금액을 목표 금액으로 맞춘다. 차액만큼 추가 차감하거나 환불한다.
     *
     * 증분이 아니라 목표 금액을 받기 때문에 같은 요청이 두 번 도착해도 두 번째는 차액이 0 이 되어
     * 아무 일도 일어나지 않는다 — 별도 멱등키 없이 재시도 안전하다.
     */
    @Transactional
    public PetCoinEscrow changeAmount(CareRequest careRequest, Users requester, int newAmount) {
        if (newAmount <= 0) {
            throw PaymentValidationException.escrowAmountInvalid();
        }

        PetCoinEscrow escrow = escrowRepository.findByCareRequestForUpdate(careRequest)
                .orElseThrow(() -> new PetCoinEscrowNotFoundException());

        int diff = newAmount - escrow.getAmount();
        if (diff == 0) {
            return escrow;
        }

        if (diff > 0) {
            petCoinService.deductCoins(requester, diff, "CARE_REQUEST", careRequest.getIdx(),
                    String.format("펫케어 제시 금액 인상 정산 - 요청 ID: %d", careRequest.getIdx()));
        } else {
            petCoinService.refundCoins(requester, -diff, "CARE_REQUEST", careRequest.getIdx(),
                    String.format("펫케어 제시 금액 인하 정산 - 요청 ID: %d", careRequest.getIdx()));
        }

        escrow.changeAmount(newAmount);
        log.info("에스크로 금액 변경: escrowIdx={}, careRequestIdx={}, {} -> {}",
                escrow.getIdx(), careRequest.getIdx(), escrow.getAmount() - diff, newAmount);

        return escrowRepository.save(escrow);
    }

    @Transactional
    public PetCoinEscrow createEscrow(CareRequest careRequest, CareApplication careApplication,
            Users requester, Users provider, Integer amount) {
        if (amount == null || amount <= 0) {
            throw PaymentValidationException.escrowAmountInvalid();
        }

        // 이미 에스크로가 있는지 확인
        escrowRepository.findByCareRequest(careRequest)
                .ifPresent(existing -> {
                    throw PaymentConflictException.escrowAlreadyExists();
                });

        // 요청자 코인 차감
        petCoinService.deductCoins(
                requester,
                amount,
                "CARE_REQUEST",
                careRequest.getIdx(),
                String.format("펫케어 거래 확정 - 요청 ID: %d", careRequest.getIdx()));

        // 에스크로 생성
        PetCoinEscrow escrow = PetCoinEscrow.builder()
                .careRequest(careRequest)
                .careApplication(careApplication)
                .requester(requester)
                .provider(provider)
                .amount(amount)
                .status(EscrowStatus.HOLD)
                .build();

        PetCoinEscrow saved = escrowRepository.save(escrow);

        log.info("에스크로 생성 완료: escrowIdx={}, careRequestIdx={}, amount={}, requesterId={}, providerId={}",
                saved.getIdx(), careRequest.getIdx(), amount, requester.getIdx(), provider.getIdx());

        return saved;
    }

    /**
     * 에스크로에서 제공자에게 지급 (거래 완료 시)
     *
     * @param escrow 에스크로
     * @return 업데이트된 에스크로
     */
    @Transactional
    public PetCoinEscrow releaseToProvider(PetCoinEscrow escrow) {
        // 비관적 락으로 에스크로 조회 (Race Condition 방지)
        escrow = escrowRepository.findByIdForUpdate(escrow.getIdx())
                .orElseThrow(() -> new PetCoinEscrowNotFoundException());

        escrow.release();

        // 제공자에게 코인 지급
        petCoinService.payoutCoins(
                escrow.getProvider(),
                escrow.getAmount(),
                "CARE_REQUEST",
                escrow.getCareRequest().getIdx(),
                String.format("펫케어 거래 완료 - 요청 ID: %d", escrow.getCareRequest().getIdx()));

        // 통계 집계는 결제 트랜잭션 커밋 후 비동기 처리 (실패해도 코인 지급은 롤백되지 않음)
        eventPublisher.publishEvent(new PaymentRecordedEvent(BigDecimal.valueOf(escrow.getAmount())));

        PetCoinEscrow saved = escrowRepository.save(escrow);

        log.info("에스크로 지급 완료: escrowIdx={}, careRequestIdx={}, amount={}, providerId={}",
                saved.getIdx(), escrow.getCareRequest().getIdx(), escrow.getAmount(),
                escrow.getProvider().getIdx());

        return saved;
    }

    /**
     * 에스크로에서 요청자에게 환불 (거래 취소 시)
     *
     * @param escrow 에스크로
     * @return 업데이트된 에스크로
     */
    @Transactional
    public PetCoinEscrow refundToRequester(PetCoinEscrow escrow) {
        // 비관적 락으로 에스크로 조회 (Race Condition 방지)
        escrow = escrowRepository.findByIdForUpdate(escrow.getIdx())
                .orElseThrow(() -> new PetCoinEscrowNotFoundException());

        escrow.refund();

        // 요청자에게 코인 환불
        petCoinService.refundCoins(
                escrow.getRequester(),
                escrow.getAmount(),
                "CARE_REQUEST",
                escrow.getCareRequest().getIdx(),
                String.format("펫케어 거래 취소 - 요청 ID: %d", escrow.getCareRequest().getIdx()));

        PetCoinEscrow saved = escrowRepository.save(escrow);

        log.info("에스크로 환불 완료: escrowIdx={}, careRequestIdx={}, amount={}, requesterId={}",
                saved.getIdx(), escrow.getCareRequest().getIdx(), escrow.getAmount(),
                escrow.getRequester().getIdx());

        return saved;
    }

    /**
     * CareRequest로 에스크로 조회
     */
    @Transactional(readOnly = true)
    public PetCoinEscrow findByCareRequest(CareRequest careRequest) {
        return escrowRepository.findByCareRequest(careRequest)
                .orElse(null);
    }

    /**
     * 비관적 락을 사용한 CareRequest로 에스크로 조회 (동시성 제어용) 상태 변경 시 Race Condition 방지를 위해
     * 사용
     */
    @Transactional
    public PetCoinEscrow findByCareRequestForUpdate(CareRequest careRequest) {
        return escrowRepository.findByCareRequestForUpdate(careRequest)
                .orElse(null);
    }
}
