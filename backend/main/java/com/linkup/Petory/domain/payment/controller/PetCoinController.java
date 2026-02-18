package com.linkup.Petory.domain.payment.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.payment.converter.PetCoinTransactionConverter;
import com.linkup.Petory.domain.payment.dto.PetCoinBalanceResponse;
import com.linkup.Petory.domain.payment.dto.PetCoinChargeRequest;
import com.linkup.Petory.domain.payment.dto.PetCoinTransactionDTO;
import com.linkup.Petory.domain.payment.dto.PetCoinTransactionDetailDTO;
import com.linkup.Petory.domain.payment.entity.PetCoinTransaction;
import com.linkup.Petory.domain.payment.repository.PetCoinTransactionRepository;
import com.linkup.Petory.domain.payment.service.PetCoinService;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 일반 사용자용 펫코인 컨트롤러
 * 
 * 주의: 현재는 실제 결제 시스템(PG) 연동 없이 테스트 충전 기능을 사용합니다.
 * 실제 운영 시에는 이 충전 엔드포인트를 PG 연동으로 대체하거나,
 * 별도의 결제 서비스로 분리하여 구현해야 합니다.
 */
@Slf4j
@RestController
@RequestMapping("/api/payment")
@RequiredArgsConstructor
public class PetCoinController {

        private final PetCoinService petCoinService;
        private final UsersRepository usersRepository;
        private final PetCoinTransactionRepository transactionRepository;
        private final PetCoinTransactionConverter transactionConverter;

        /**
         * 현재 사용자 코인 잔액 조회
         */
        @GetMapping("/balance")
        public ResponseEntity<PetCoinBalanceResponse> getMyBalance() {
                Users user = getCurrentUser();
                Integer balance = petCoinService.getBalance(user);
                return ResponseEntity.ok(new PetCoinBalanceResponse(user.getIdx(), balance));
        }

        /**
         * 현재 사용자 거래 내역 조회 (DB 페이징)
         * [리팩토링] 메모리 페이징 → DB 페이징, Page 응답 형식
         */
        @GetMapping("/transactions")
        public ResponseEntity<Page<PetCoinTransactionDTO>> getMyTransactions(
                        @PageableDefault(size = 20) Pageable pageable) {
                Users user = getCurrentUser();
                Page<PetCoinTransaction> transactions = transactionRepository
                                .findByUserOrderByCreatedAtDesc(user, pageable);
                return ResponseEntity.ok(transactions.map(transactionConverter::toDTO));
        }

        /**
         * 거래 상세 조회 (상대방 정보 포함)
         * GET /api/payment/transactions/{id}
         */
        @GetMapping("/transactions/{id}")
        public ResponseEntity<PetCoinTransactionDetailDTO> getTransactionDetail(@PathVariable Long id) {
                Users user = getCurrentUser();
                PetCoinTransactionDetailDTO detail = petCoinService.getTransactionDetail(id, user);
                return ResponseEntity.ok(detail);
        }

        /**
         * 코인 충전
         * 
         * 현재는 실제 결제 시스템 연동 없이 시뮬레이션으로 처리합니다.
         * 실제 운영 시에는 이 메서드를 PG 연동으로 대체하거나,
         * 별도의 결제 서비스로 분리하여 구현해야 합니다.
         */
        @PostMapping("/charge")
        public ResponseEntity<PetCoinTransactionDTO> chargeCoins(
                        @RequestBody PetCoinChargeRequest request) {
                Users user = getCurrentUser();

                if (request.amount() == null || request.amount() <= 0) {
                        throw new IllegalArgumentException("충전 금액은 0보다 커야 합니다.");
                }

                PetCoinTransaction transaction = petCoinService.chargeCoins(
                                user,
                                request.amount(),
                                request.description() != null ? request.description() : "코인 충전");

                log.info("코인 충전 완료: userId={}, amount={}", user.getIdx(), request.amount());

                return ResponseEntity.ok(transactionConverter.toDTO(transaction));
        }

        /**
         * 현재 로그인한 사용자 조회 (요청당 1회만 조회)
         * [리팩토링] getCurrentUserId + findById → getCurrentUser 1회 조회로 User 중복 조회 제거
         */
        private Users getCurrentUser() {
                Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || authentication.getPrincipal() == null) {
                        throw new RuntimeException("인증이 필요합니다.");
                }
                String userId = authentication.getName();
                return usersRepository.findByIdString(userId)
                                .orElseThrow(() -> new RuntimeException("User not found"));
        }
}
