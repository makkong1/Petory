package com.linkup.Petory.domain.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.payment.entity.EscrowStatus;
import com.linkup.Petory.domain.payment.entity.PetCoinEscrow;
import com.linkup.Petory.domain.payment.repository.PetCoinEscrowRepository;
import com.linkup.Petory.domain.user.entity.Users;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

/**
 * 펫코인 에스크로 서비스
 * 역할: 거래 확정 시 코인을 임시 보관하고, 거래 완료 시 제공자에게 지급하거나 취소 시 환불합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetCoinEscrowService {

        private final PetCoinEscrowRepository escrowRepository;
        private final PetCoinService petCoinService;

        /**
         * 에스크로 생성 (거래 확정 시)
         * 
         * @param careRequest     펫케어 요청
         * @param careApplication 펫케어 지원 (거래 확정된 것)
         * @param requester       요청자
         * @param provider        제공자
         * @param amount          에스크로 금액 (코인 단위)
         * @return 생성된 에스크로
         */
        @Transactional
        public PetCoinEscrow createEscrow(CareRequest careRequest, CareApplication careApplication,
                        Users requester, Users provider, Integer amount) {
                if (amount == null || amount <= 0) {
                        throw new IllegalArgumentException("에스크로 금액은 0보다 커야 합니다.");
                }

                // 이미 에스크로가 있는지 확인
                escrowRepository.findByCareRequest(careRequest)
                                .ifPresent(existing -> {
                                        throw new IllegalStateException("이미 에스크로가 생성되어 있습니다.");
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
                PetCoinEscrow lockedEscrow = escrowRepository.findByIdForUpdate(escrow.getIdx())
                                .orElseThrow(() -> new RuntimeException("Escrow not found"));

                if (lockedEscrow.getStatus() != EscrowStatus.HOLD) {
                        throw new IllegalStateException("HOLD 상태의 에스크로만 지급할 수 있습니다.");
                }

                // 락으로 조회한 에스크로 사용
                escrow = lockedEscrow;

                // 제공자에게 코인 지급
                petCoinService.payoutCoins(
                                escrow.getProvider(),
                                escrow.getAmount(),
                                "CARE_REQUEST",
                                escrow.getCareRequest().getIdx(),
                                String.format("펫케어 거래 완료 - 요청 ID: %d", escrow.getCareRequest().getIdx()));

                // 에스크로 상태 변경
                escrow.setStatus(EscrowStatus.RELEASED);
                escrow.setReleasedAt(LocalDateTime.now());

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
                PetCoinEscrow lockedEscrow = escrowRepository.findByIdForUpdate(escrow.getIdx())
                                .orElseThrow(() -> new RuntimeException("Escrow not found"));

                if (lockedEscrow.getStatus() != EscrowStatus.HOLD) {
                        throw new IllegalStateException("HOLD 상태의 에스크로만 환불할 수 있습니다.");
                }

                // 락으로 조회한 에스크로 사용
                escrow = lockedEscrow;

                // 요청자에게 코인 환불
                petCoinService.refundCoins(
                                escrow.getRequester(),
                                escrow.getAmount(),
                                "CARE_REQUEST",
                                escrow.getCareRequest().getIdx(),
                                String.format("펫케어 거래 취소 - 요청 ID: %d", escrow.getCareRequest().getIdx()));

                // 에스크로 상태 변경
                escrow.setStatus(EscrowStatus.REFUNDED);
                escrow.setRefundedAt(LocalDateTime.now());

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
         * 비관적 락을 사용한 CareRequest로 에스크로 조회 (동시성 제어용)
         * 상태 변경 시 Race Condition 방지를 위해 사용
         */
        @Transactional
        public PetCoinEscrow findByCareRequestForUpdate(CareRequest careRequest) {
                return escrowRepository.findByCareRequestForUpdate(careRequest)
                                .orElse(null);
        }
}
