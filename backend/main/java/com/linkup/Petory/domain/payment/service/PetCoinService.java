package com.linkup.Petory.domain.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.payment.entity.PetCoinTransaction;
import com.linkup.Petory.domain.payment.entity.TransactionStatus;
import com.linkup.Petory.domain.payment.entity.TransactionType;
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

                // 사용자 최신 정보 조회
                Users currentUser = usersRepository.findById(user.getIdx())
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

                // 사용자 최신 정보 조회
                Users currentUser = usersRepository.findById(user.getIdx())
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

                // 사용자 최신 정보 조회
                Users currentUser = usersRepository.findById(user.getIdx())
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

                // 사용자 최신 정보 조회
                Users currentUser = usersRepository.findById(user.getIdx())
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
         */
        @Transactional(readOnly = true)
        public Integer getBalance(Users user) {
                Users currentUser = usersRepository.findById(user.getIdx())
                                .orElseThrow(() -> new RuntimeException("User not found"));
                return currentUser.getPetCoinBalance();
        }
}
