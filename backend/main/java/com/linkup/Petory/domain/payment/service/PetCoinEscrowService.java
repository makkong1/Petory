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
import com.linkup.Petory.domain.user.exception.UserNotFoundException;
import com.linkup.Petory.domain.user.repository.UsersRepository;

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
    private final UsersRepository usersRepository;

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

        // ⚠️ 크로스-유저 데드락 방지: requester/provider 두 행을 idx 오름차순으로 먼저 선점한다.
        //    createEscrow는 deductCoins(requester)로 요청자 행에 X락을, escrow INSERT로 제공자 행에
        //    FK 공유락을 잡는다. 서로 역할이 뒤바뀐 두 거래(A→B, B→A)가 동시에 실행되면 락 순서가
        //    엇갈려 순환 대기(MySQL 1213 Deadlock)가 발생한다. 항상 낮은 idx부터 잠가 전역 락 순서를
        //    통일하면 모든 거래가 같은 순서로 대기해 데드락이 사라진다.
        lockUsersInOrder(requester, provider);

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

    /**
     * 두 사용자 행을 idx 오름차순으로 비관적 락(X) 선점한다. 전역 락 순서를 통일해 크로스-유저 데드락을 방지한다.
     */
    private void lockUsersInOrder(Users first, Users second) {
        long a = first.getIdx();
        long b = second.getIdx();
        long lo = Math.min(a, b);
        long hi = Math.max(a, b);
        usersRepository.findByIdForUpdate(lo).orElseThrow(UserNotFoundException::new);
        if (hi != lo) {
            usersRepository.findByIdForUpdate(hi).orElseThrow(UserNotFoundException::new);
        }
    }
}
