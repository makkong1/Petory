package com.linkup.Petory.domain.payment.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.payment.dto.PetCoinBalanceResponse;
import com.linkup.Petory.domain.payment.dto.PetCoinChargeRequest;
import com.linkup.Petory.domain.payment.dto.PetCoinTransactionDTO;
import com.linkup.Petory.domain.payment.entity.PetCoinTransaction;
import com.linkup.Petory.domain.payment.repository.PetCoinTransactionRepository;
import com.linkup.Petory.domain.payment.service.PetCoinService;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 일반 사용자용 펫코인 컨트롤러
 * - 개발/테스트 환경에서만 활성화되는 테스트 충전 기능 포함
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PetCoinController {

        private final PetCoinService petCoinService;
        private final UsersRepository usersRepository;
        private final PetCoinTransactionRepository transactionRepository;
        private final com.linkup.Petory.domain.payment.converter.PetCoinTransactionConverter transactionConverter;

        @Value("${spring.profiles.active:prod}")
        private String activeProfile;

        /**
         * 현재 사용자 코인 잔액 조회
         */
        @GetMapping("/balance")
        public ResponseEntity<PetCoinBalanceResponse> getMyBalance() {
                Long userId = getCurrentUserId();
                Users user = usersRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                Integer balance = petCoinService.getBalance(user);

                return ResponseEntity.ok(PetCoinBalanceResponse.builder()
                                .userId(userId)
                                .balance(balance)
                                .build());
        }

        /**
         * 현재 사용자 거래 내역 조회
         */
        @GetMapping("/transactions")
        public ResponseEntity<List<PetCoinTransactionDTO>> getMyTransactions(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
                Long userId = getCurrentUserId();
                Users user = usersRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                List<PetCoinTransaction> transactions = transactionRepository
                                .findByUserOrderByCreatedAtDesc(user);

                // 페이징 처리 (간단한 방식)
                int start = page * size;
                int end = Math.min(start + size, transactions.size());
                List<PetCoinTransaction> pagedTransactions = transactions.subList(
                                Math.min(start, transactions.size()),
                                end);

                List<PetCoinTransactionDTO> dtos = pagedTransactions.stream()
                                .map(transactionConverter::toDTO)
                                .collect(Collectors.toList());

                return ResponseEntity.ok(dtos);
        }

        /**
         * 테스트 코인 충전 (개발/테스트 환경에서만 활성화)
         * 프로덕션 환경에서는 비활성화되어야 함
         */
        @PostMapping("/charge")
        public ResponseEntity<PetCoinTransactionDTO> chargeCoins(
                        @RequestBody PetCoinChargeRequest request) {
                // 프로덕션 환경에서는 비활성화
                if ("prod".equals(activeProfile)) {
                        throw new IllegalStateException("프로덕션 환경에서는 테스트 충전 기능을 사용할 수 없습니다.");
                }

                Long userId = getCurrentUserId();
                Users user = usersRepository.findById(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                if (request.getAmount() == null || request.getAmount() <= 0) {
                        throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
                }

                PetCoinTransaction transaction = petCoinService.chargeCoins(
                                user,
                                request.getAmount(),
                                request.getDescription() != null ? request.getDescription() : "테스트 충전");

                log.info("테스트 코인 충전: userId={}, amount={}", userId, request.getAmount());

                return ResponseEntity.ok(transactionConverter.toDTO(transaction));
        }

        /**
         * 현재 로그인한 사용자 ID 조회
         */
        private Long getCurrentUserId() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || authentication.getPrincipal() == null) {
                        throw new RuntimeException("인증이 필요합니다.");
                }

                // JWT에서 사용자 ID 추출 (실제 구현에 맞게 수정 필요)
                // 예시: UserDetails에서 ID 추출
                String username = authentication.getName();
                Users user = usersRepository.findByUsername(username)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                return user.getIdx();
        }
}
