package com.linkup.Petory.domain.care.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.care.converter.CareRequestConverter;
import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.user.entity.EmailVerificationPurpose;
import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException;
import com.linkup.Petory.domain.user.repository.PetRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.payment.service.PetCoinEscrowService;
import com.linkup.Petory.domain.payment.entity.PetCoinEscrow;
import com.linkup.Petory.domain.payment.entity.EscrowStatus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CareRequestService {

    private final CareRequestRepository careRequestRepository;
    private final UsersRepository usersRepository;
    private final PetRepository petRepository;
    private final CareRequestConverter careRequestConverter;
    private final PetCoinEscrowService petCoinEscrowService;

    /**
     * 현재 사용자가 관리자(ADMIN 또는 MASTER)인지 확인
     */
    private boolean isAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority -> authority.equals("ROLE_ADMIN") || authority.equals("ROLE_MASTER"));
    }

    // 전체 케어 요청 조회 (필터링 포함) - 작성자도 활성 상태여야 함
    @Transactional(readOnly = true)
    public List<CareRequestDTO> getAllCareRequests(String status, String location) {
        // 작성자 상태 체크가 포함된 쿼리 사용
        List<CareRequest> requests;
        if (status != null && !status.equals("ALL")) {
            CareRequestStatus statusEnum = CareRequestStatus.valueOf(status);
            requests = careRequestRepository.findByStatusAndIsDeletedFalse(statusEnum);
        } else {
            requests = careRequestRepository.findAllActiveRequests();
        }

        // 위치 필터링 (추후 구현)
        if (location != null && !location.isEmpty()) {
            requests = requests.stream()
                    .filter(r -> r.getUser().getLocation() != null &&
                            r.getUser().getLocation().contains(location))
                    .collect(Collectors.toList());
        }

        return careRequestConverter.toDTOList(requests);
    }

    // 단일 케어 요청 조회
    @Transactional(readOnly = true)
    public CareRequestDTO getCareRequest(Long idx) {
        CareRequest request = careRequestRepository.findByIdWithPet(idx)
                .orElseThrow(() -> new RuntimeException("CareRequest not found"));
        if (Boolean.TRUE.equals(request.getIsDeleted())) {
            throw new RuntimeException("CareRequest not found");
        }

        return careRequestConverter.toDTO(request);
    }

    // 케어 요청 생성
    @Transactional
    public CareRequestDTO createCareRequest(CareRequestDTO dto) {
        // 사용자 조회 (1회만)
        Users user = usersRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 이메일 인증 확인
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            log.debug("이메일 인증 미완료: userId={}", dto.getUserId());
            throw new EmailVerificationRequiredException(
                    "펫케어 서비스 이용을 위해 이메일 인증이 필요합니다.",
                    EmailVerificationPurpose.PET_CARE);
        }

        // 제공 코인 유효성 검증
        if (dto.getOfferedCoins() == null || dto.getOfferedCoins() <= 0) {
            throw new RuntimeException("제공 코인은 0보다 커야 합니다.");
        }

        // 사용자 잔액 확인
        if (user.getPetCoinBalance() < dto.getOfferedCoins()) {
            throw new RuntimeException("펫코인 잔액이 부족합니다.");
        }

        CareRequest.CareRequestBuilder builder = CareRequest.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .date(dto.getDate())
                .user(user)
                .status(CareRequestStatus.OPEN)
                .offeredCoins(dto.getOfferedCoins());

        // 펫 정보 설정 (선택사항)
        if (dto.getPetIdx() != null) {
            Pet pet = petRepository.findById(dto.getPetIdx())
                    .orElseThrow(() -> new RuntimeException("Pet not found"));
            // 펫 소유자 확인
            if (!pet.getUser().getIdx().equals(user.getIdx())) {
                throw new RuntimeException("펫 소유자만 펫 정보를 연결할 수 있습니다.");
            }
            builder.pet(pet);
        }

        CareRequest saved = careRequestRepository.save(builder.build());
        return careRequestConverter.toDTO(saved);
    }

    // 케어 요청 수정
    @Transactional
    public CareRequestDTO updateCareRequest(Long idx, CareRequestDTO dto, Long currentUserId) {
        CareRequest request = careRequestRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("CareRequest not found"));

        // 작성자 확인 (관리자는 우회)
        if (!isAdmin() && !request.getUser().getIdx().equals(currentUserId)) {
            throw new RuntimeException("본인의 케어 요청만 수정할 수 있습니다.");
        }

        if (dto.getTitle() != null)
            request.setTitle(dto.getTitle());
        if (dto.getDescription() != null)
            request.setDescription(dto.getDescription());
        if (dto.getDate() != null)
            request.setDate(dto.getDate());

        // 펫 정보 업데이트 (선택사항)
        if (dto.getPetIdx() != null) {
            Pet pet = petRepository.findById(dto.getPetIdx())
                    .orElseThrow(() -> new RuntimeException("Pet not found"));
            // 펫 소유자 확인
            if (!pet.getUser().getIdx().equals(request.getUser().getIdx())) {
                throw new RuntimeException("펫 소유자만 펫 정보를 연결할 수 있습니다.");
            }
            request.setPet(pet);
        } else if (dto.getPetIdx() == null && request.getPet() != null) {
            // petIdx가 null로 전달되면 펫 연결 해제
            request.setPet(null);
        }

        CareRequest updated = careRequestRepository.save(request);
        return careRequestConverter.toDTO(updated);
    }

    // 케어 요청 삭제
    @Transactional
    public void deleteCareRequest(Long idx, Long currentUserId) {
        CareRequest request = careRequestRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("CareRequest not found"));

        // 작성자 확인 (관리자는 우회)
        if (!isAdmin() && !request.getUser().getIdx().equals(currentUserId)) {
            throw new RuntimeException("본인의 케어 요청만 삭제할 수 있습니다.");
        }

        request.setIsDeleted(true);
        request.setDeletedAt(java.time.LocalDateTime.now());
        careRequestRepository.save(request);
    }

    // 내 케어 요청 조회
    @Transactional(readOnly = true)
    public List<CareRequestDTO> getMyCareRequests(Long userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<CareRequest> requests = careRequestRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
        return careRequestConverter.toDTOList(requests);
    }

    // 상태 변경
    @Transactional
    public CareRequestDTO updateStatus(Long idx, String status, Long currentUserId) {
        CareRequest request = careRequestRepository.findByIdWithApplications(idx)
                .orElseThrow(() -> new RuntimeException("CareRequest not found"));

        // 관리자는 권한 검증 우회
        if (!isAdmin()) {
            // 작성자 또는 승인된 제공자만 상태 변경 가능
            boolean isRequester = request.getUser().getIdx().equals(currentUserId);
            boolean isAcceptedProvider = request.getApplications() != null &&
                    request.getApplications().stream()
                            .anyMatch(app -> app.getStatus() == CareApplicationStatus.ACCEPTED
                                    && app.getProvider().getIdx().equals(currentUserId));

            if (!isRequester && !isAcceptedProvider) {
                throw new RuntimeException("작성자 또는 승인된 제공자만 상태를 변경할 수 있습니다.");
            }
        }

        CareRequestStatus oldStatus = request.getStatus();
        CareRequestStatus newStatus = CareRequestStatus.valueOf(status);

        request.setStatus(newStatus);
        CareRequest updated = careRequestRepository.save(request);

        // 상태가 COMPLETED로 변경될 때 에스크로에서 제공자에게 코인 지급
        if (oldStatus != CareRequestStatus.COMPLETED && newStatus == CareRequestStatus.COMPLETED) {
            // 비관적 락으로 에스크로 조회 (동시 요청 시 중복 지급 방지)
            PetCoinEscrow escrow = petCoinEscrowService.findByCareRequestForUpdate(request);
            if (escrow != null && escrow.getStatus() == EscrowStatus.HOLD) {
                try {
                    petCoinEscrowService.releaseToProvider(escrow);
                    log.info("거래 완료 시 제공자에게 코인 지급 완료: careRequestIdx={}, escrowIdx={}, amount={}",
                            request.getIdx(), escrow.getIdx(), escrow.getAmount());
                } catch (Exception e) {
                    log.error("거래 완료 시 제공자에게 코인 지급 실패: careRequestIdx={}, error={}",
                            request.getIdx(), e.getMessage(), e);
                    // 코인 지급 실패 시 상태 변경 롤백
                    throw new RuntimeException("코인 지급 처리 중 오류가 발생했습니다.", e);
                }
            } else {
                log.warn("에스크로를 찾을 수 없거나 이미 처리됨: careRequestIdx={}", request.getIdx());
            }
        }

        // 상태가 CANCELLED로 변경될 때 에스크로에서 요청자에게 코인 환불
        if (newStatus == CareRequestStatus.CANCELLED) {
            // 비관적 락으로 에스크로 조회 (동시 요청 시 중복 환불 방지)
            PetCoinEscrow escrow = petCoinEscrowService.findByCareRequestForUpdate(request);
            if (escrow != null && escrow.getStatus() == EscrowStatus.HOLD) {
                try {
                    petCoinEscrowService.refundToRequester(escrow);
                    log.info("거래 취소 시 요청자에게 코인 환불 완료: careRequestIdx={}, escrowIdx={}, amount={}",
                            request.getIdx(), escrow.getIdx(), escrow.getAmount());
                } catch (Exception e) {
                    log.error("거래 취소 시 요청자에게 코인 환불 실패: careRequestIdx={}, error={}",
                            request.getIdx(), e.getMessage(), e);
                    // 환불 실패 시 상태 변경 롤백
                    throw new RuntimeException("환불 처리 중 오류가 발생했습니다.", e);
                }
            } else {
                log.warn("에스크로를 찾을 수 없거나 이미 처리됨: careRequestIdx={}", request.getIdx());
            }
        }

        return careRequestConverter.toDTO(updated);
    }

    // 검색 기능
    @Transactional(readOnly = true)
    public List<CareRequestDTO> searchCareRequests(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllCareRequests(null, null);
        }

        List<CareRequest> requests = careRequestRepository
                .findByTitleContainingIgnoreCaseOrDescriptionContainingIgnoreCaseAndIsDeletedFalse(
                        keyword.trim(), keyword.trim());

        return careRequestConverter.toDTOList(requests);
    }
}
