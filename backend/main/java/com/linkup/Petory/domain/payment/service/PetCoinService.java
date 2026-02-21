package com.linkup.Petory.domain.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.payment.dto.PetCoinTransactionDetailDTO;
import com.linkup.Petory.domain.payment.entity.PetCoinTransaction;
import com.linkup.Petory.domain.payment.entity.TransactionStatus;
import com.linkup.Petory.domain.payment.entity.TransactionType;
import com.linkup.Petory.domain.payment.repository.PetCoinEscrowRepository;
import com.linkup.Petory.domain.payment.repository.PetCoinTransactionRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 펫코인 서비스
 * 역할: 코인 충전, 차감, 지급, 환불 등 모든 코인 거래를 처리합니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PetCoinService {

        private final UsersRepository usersRepository;
        private final PetCoinTransactionRepository transactionRepository;
        private final PetCoinEscrowRepository escrowRepository;
        private final CareRequestRepository careRequestRepository;

        /**
         * 코인 충전 (관리자 지급 또는 테스트 충전)
         * 
         * @param user        충전할 사용자
         * @param amount      충전할 코인 수
         * @param description 거래 설명
         * @return 생성된 거래 내역
         */
        @Transactional
        public PetCoinTransaction chargeCoins(Users user, Integer amount, String description) {
                if (amount <= 0) {
                        throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
                }

                // [리팩토링] findById → findByIdForUpdate (비관적 락, Race Condition 방지)
                Users currentUser = usersRepository.findByIdForUpdate(user.getIdx())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                Integer balanceBefore = currentUser.getPetCoinBalance();
                Integer balanceAfter = balanceBefore + amount;

                // 잔액 업데이트
                currentUser.setPetCoinBalance(balanceAfter);
                usersRepository.save(currentUser);

                // 거래 내역 기록
                PetCoinTransaction transaction = PetCoinTransaction.builder()
                                .user(currentUser)
                                .transactionType(TransactionType.CHARGE)
                                .amount(amount)
                                .balanceBefore(balanceBefore)
                                .balanceAfter(balanceAfter)
                                .description(description != null ? description : "코인 충전")
                                .status(TransactionStatus.COMPLETED)
                                .build();

                PetCoinTransaction saved = transactionRepository.save(transaction);

                log.info("코인 충전 완료: userId={}, amount={}, balanceBefore={}, balanceAfter={}",
                                currentUser.getIdx(), amount, balanceBefore, balanceAfter);

                return saved;
        }

        /**
         * 코인 차감 (에스크로로 이동)
         * 
         * @param user        차감할 사용자
         * @param amount      차감할 코인 수
         * @param relatedType 관련 엔티티 타입
         * @param relatedIdx  관련 엔티티 ID
         * @param description 거래 설명
         * @return 생성된 거래 내역
         */
        @Transactional
        public PetCoinTransaction deductCoins(Users user, Integer amount, String relatedType,
                        Long relatedIdx, String description) {
                if (amount <= 0) {
                        throw new IllegalArgumentException("차감 금액은 0보다 커야 합니다.");
                }

                // [리팩토링] findById → findByIdForUpdate (비관적 락, Race Condition 방지)
                Users currentUser = usersRepository.findByIdForUpdate(user.getIdx())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                Integer balanceBefore = currentUser.getPetCoinBalance();

                // 잔액 확인
                if (balanceBefore < amount) {
                        throw new IllegalStateException(
                                        String.format("잔액이 부족합니다. 현재 잔액: %d, 필요 금액: %d", balanceBefore, amount));
                }

                Integer balanceAfter = balanceBefore - amount;

                // 잔액 업데이트
                currentUser.setPetCoinBalance(balanceAfter);
                usersRepository.save(currentUser);

                // 거래 내역 기록
                PetCoinTransaction transaction = PetCoinTransaction.builder()
                                .user(currentUser)
                                .transactionType(TransactionType.DEDUCT)
                                .amount(amount)
                                .balanceBefore(balanceBefore)
                                .balanceAfter(balanceAfter)
                                .relatedType(relatedType)
                                .relatedIdx(relatedIdx)
                                .description(description != null ? description : "코인 차감 (에스크로)")
                                .status(TransactionStatus.COMPLETED)
                                .build();

                PetCoinTransaction saved = transactionRepository.save(transaction);

                log.info("코인 차감 완료: userId={}, amount={}, balanceBefore={}, balanceAfter={}, relatedType={}, relatedIdx={}",
                                currentUser.getIdx(), amount, balanceBefore, balanceAfter, relatedType, relatedIdx);

                return saved;
        }

        /**
         * 코인 지급 (에스크로에서 제공자에게)
         * 
         * @param user        지급받을 사용자
         * @param amount      지급할 코인 수
         * @param relatedType 관련 엔티티 타입
         * @param relatedIdx  관련 엔티티 ID
         * @param description 거래 설명
         * @return 생성된 거래 내역
         */
        @Transactional
        public PetCoinTransaction payoutCoins(Users user, Integer amount, String relatedType,
                        Long relatedIdx, String description) {
                if (amount <= 0) {
                        throw new IllegalArgumentException("지급 금액은 0보다 커야 합니다.");
                }

                // [리팩토링] findById → findByIdForUpdate (비관적 락, Race Condition 방지)
                Users currentUser = usersRepository.findByIdForUpdate(user.getIdx())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                Integer balanceBefore = currentUser.getPetCoinBalance();
                Integer balanceAfter = balanceBefore + amount;

                // 잔액 업데이트
                currentUser.setPetCoinBalance(balanceAfter);
                usersRepository.save(currentUser);

                // 거래 내역 기록
                PetCoinTransaction transaction = PetCoinTransaction.builder()
                                .user(currentUser)
                                .transactionType(TransactionType.PAYOUT)
                                .amount(amount)
                                .balanceBefore(balanceBefore)
                                .balanceAfter(balanceAfter)
                                .relatedType(relatedType)
                                .relatedIdx(relatedIdx)
                                .description(description != null ? description : "코인 지급")
                                .status(TransactionStatus.COMPLETED)
                                .build();

                PetCoinTransaction saved = transactionRepository.save(transaction);

                log.info("코인 지급 완료: userId={}, amount={}, balanceBefore={}, balanceAfter={}, relatedType={}, relatedIdx={}",
                                currentUser.getIdx(), amount, balanceBefore, balanceAfter, relatedType, relatedIdx);

                return saved;
        }

        /**
         * 코인 환불
         * 
         * @param user        환불받을 사용자
         * @param amount      환불할 코인 수
         * @param relatedType 관련 엔티티 타입
         * @param relatedIdx  관련 엔티티 ID
         * @param description 거래 설명
         * @return 생성된 거래 내역
         */
        @Transactional
        public PetCoinTransaction refundCoins(Users user, Integer amount, String relatedType,
                        Long relatedIdx, String description) {
                if (amount <= 0) {
                        throw new IllegalArgumentException("환불 금액은 0보다 커야 합니다.");
                }

                // [리팩토링] findById → findByIdForUpdate (비관적 락, Race Condition 방지)
                Users currentUser = usersRepository.findByIdForUpdate(user.getIdx())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                Integer balanceBefore = currentUser.getPetCoinBalance();
                Integer balanceAfter = balanceBefore + amount;

                // 잔액 업데이트
                currentUser.setPetCoinBalance(balanceAfter);
                usersRepository.save(currentUser);

                // 거래 내역 기록
                PetCoinTransaction transaction = PetCoinTransaction.builder()
                                .user(currentUser)
                                .transactionType(TransactionType.REFUND)
                                .amount(amount)
                                .balanceBefore(balanceBefore)
                                .balanceAfter(balanceAfter)
                                .relatedType(relatedType)
                                .relatedIdx(relatedIdx)
                                .description(description != null ? description : "코인 환불")
                                .status(TransactionStatus.COMPLETED)
                                .build();

                PetCoinTransaction saved = transactionRepository.save(transaction);

                log.info("코인 환불 완료: userId={}, amount={}, balanceBefore={}, balanceAfter={}, relatedType={}, relatedIdx={}",
                                currentUser.getIdx(), amount, balanceBefore, balanceAfter, relatedType, relatedIdx);

                return saved;
        }

        /**
         * 사용자 코인 잔액 조회
         * [리팩토링] findById 재조회 제거 → user.getPetCoinBalance() 직접 반환 (Controller
         * getCurrentUser 전달 시 추가 쿼리 없음)
         */
        @Transactional(readOnly = true)
        public Integer getBalance(Users user) {
                return user.getPetCoinBalance();
        }

        /**
         * 거래 상세 조회 (상대방 정보 포함)
         * - 본인 거래만 조회 가능
         * - CARE_REQUEST 관련 거래 시 상대방(requester/provider) 정보 포함
         */
        @Transactional(readOnly = true)
        public PetCoinTransactionDetailDTO getTransactionDetail(Long transactionIdx, Users currentUser) {
                PetCoinTransaction transaction = transactionRepository.findById(transactionIdx)
                                .orElseThrow(() -> new RuntimeException("거래 내역을 찾을 수 없습니다."));

                if (!transaction.getUser().getIdx().equals(currentUser.getIdx())) {
                        throw new RuntimeException("본인의 거래 내역만 조회할 수 있습니다.");
                }

                PetCoinTransactionDetailDTO dto = PetCoinTransactionDetailDTO.builder()
                                .idx(transaction.getIdx())
                                .userId(transaction.getUser().getIdx())
                                .transactionType(transaction.getTransactionType())
                                .amount(transaction.getAmount())
                                .balanceBefore(transaction.getBalanceBefore())
                                .balanceAfter(transaction.getBalanceAfter())
                                .relatedType(transaction.getRelatedType())
                                .relatedIdx(transaction.getRelatedIdx())
                                .description(transaction.getDescription())
                                .status(transaction.getStatus())
                                .createdAt(transaction.getCreatedAt())
                                .updatedAt(transaction.getUpdatedAt())
                                .build();

                // CARE_REQUEST 관련 거래: 에스크로에서 상대방 정보 조회
                if ("CARE_REQUEST".equals(transaction.getRelatedType()) && transaction.getRelatedIdx() != null) {
                        escrowRepository.findByCareRequestIdx(transaction.getRelatedIdx())
                                        .ifPresent(escrow -> {
                                                Users counterparty = null;
                                                if (TransactionType.DEDUCT.equals(transaction.getTransactionType())
                                                                || TransactionType.REFUND.equals(
                                                                                transaction.getTransactionType())) {
                                                        counterparty = escrow.getProvider();
                                                } else if (TransactionType.PAYOUT
                                                                .equals(transaction.getTransactionType())) {
                                                        counterparty = escrow.getRequester();
                                                }
                                                if (counterparty != null) {
                                                        dto.setCounterpartyUserId(counterparty.getIdx());
                                                        dto.setCounterpartyUsername(
                                                                        counterparty.getNickname() != null
                                                                                        ? counterparty.getNickname()
                                                                                        : counterparty.getUsername());
                                                }
                                                careRequestRepository.findById(transaction.getRelatedIdx())
                                                                .map(CareRequest::getTitle)
                                                                .ifPresent(dto::setRelatedTitle);
                                        });
                }

                return dto;
        }
}
