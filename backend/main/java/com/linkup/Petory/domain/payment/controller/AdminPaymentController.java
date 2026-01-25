package com.linkup.Petory.domain.payment.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.payment.dto.PetCoinBalanceResponse;
import com.linkup.Petory.domain.payment.dto.PetCoinChargeRequest;
import com.linkup.Petory.domain.payment.dto.PetCoinTransactionDTO;
import com.linkup.Petory.domain.payment.entity.PetCoinTransaction;
import com.linkup.Petory.domain.payment.repository.SpringDataJpaPetCoinTransactionRepository;
import com.linkup.Petory.domain.payment.service.PetCoinService;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;

/**
 * 관리자용 펫코인 관리 컨트롤러
 * - ADMIN과 MASTER만 접근 가능
 * - 사용자에게 코인을 직접 지급할 수 있는 기능 제공
 */
@RestController
@RequestMapping("/api/admin/payment")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
public class AdminPaymentController {

        private final PetCoinService petCoinService;
        private final UsersRepository usersRepository;
        private final SpringDataJpaPetCoinTransactionRepository transactionRepository;
        private final com.linkup.Petory.domain.payment.converter.PetCoinTransactionConverter transactionConverter;

        /**
         * 관리자가 사용자에게 코인 지급
         */
        @PostMapping("/charge")
        public ResponseEntity<PetCoinTransactionDTO> chargeCoins(
                        @RequestBody PetCoinChargeRequest request) {
                if (request.getUserId() == null) {
                        throw new IllegalArgumentException("사용자 ID가 필요합니다.");
                }
                if (request.getAmount() == null || request.getAmount() <= 0) {
                        throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
                }

                Users user = usersRepository.findById(request.getUserId())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                PetCoinTransaction transaction = petCoinService.chargeCoins(
                                user,
                                request.getAmount(),
                                request.getDescription() != null ? request.getDescription() : "관리자 지급");

                return ResponseEntity.ok(transactionConverter.toDTO(transaction));
        }

        /**
         * 사용자 코인 잔액 조회 (관리자용)
         */
        @GetMapping("/balance/{userId}")
        public ResponseEntity<PetCoinBalanceResponse> getUserBalance(@PathVariable Long userId) {
                Users user = usersRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                Integer balance = petCoinService.getBalance(user);

                return ResponseEntity.ok(PetCoinBalanceResponse.builder()
                                .userId(userId)
                                .balance(balance)
                                .build());
        }

        /**
         * 사용자 거래 내역 조회 (관리자용, 페이징)
         */
        @GetMapping("/transactions/{userId}")
        public ResponseEntity<Page<PetCoinTransactionDTO>> getUserTransactions(
                        @PathVariable Long userId,
                        @PageableDefault(size = 20) Pageable pageable) {
                Users user = usersRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                Page<PetCoinTransaction> transactions = transactionRepository
                                .findByUserOrderByCreatedAtDesc(user, pageable);

                Page<PetCoinTransactionDTO> dtoPage = transactions.map(transactionConverter::toDTO);
                return ResponseEntity.ok(dtoPage);
        }
}
