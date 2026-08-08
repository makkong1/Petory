package com.linkup.Petory.domain.care.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.linkup.Petory.global.security.CustomUserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.care.converter.CareRequestConverter;
import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.dto.CareRequestListView;
import com.linkup.Petory.domain.care.dto.CareRequestPageResponseDTO;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.entity.CareScheduleMode;
import com.linkup.Petory.domain.care.exception.CareForbiddenException;
import com.linkup.Petory.domain.care.exception.CareRequestNotFoundException;
import com.linkup.Petory.domain.care.exception.CareValidationException;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.payment.entity.EscrowStatus;
import com.linkup.Petory.domain.payment.entity.PetCoinEscrow;
import com.linkup.Petory.domain.payment.service.PetCoinEscrowService;
import com.linkup.Petory.domain.petRecommendation.event.CareRequestCreatedEvent;
import com.linkup.Petory.domain.user.entity.EmailVerificationPurpose;
import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException;
import com.linkup.Petory.domain.user.exception.PetNotFoundException;
import com.linkup.Petory.domain.user.exception.UserNotFoundException;
import com.linkup.Petory.domain.user.repository.PetRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.global.security.RoleConstants;

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
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 현재 사용자가 관리자(ADMIN 또는 MASTER)인지 확인
     */
    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth != null && auth.getPrincipal() instanceof CustomUserDetails ud)) return false;
        return ud.isAdmin();
    }

    /**
     * 반경 기반 근처 케어 요청 조회 (지도 표출용)
     */
    // [오버페칭 제거] 지도 목록은 projection(CareRequestListView)을 그대로 반환한다.
    // 기존엔 native로 엔티티를 읽어 컨버터가 작성자 전체·중첩 pet·applications(@BatchSize)까지 채웠다.
    @Transactional(readOnly = true)
    public List<CareRequestListView> getNearby(double lat, double lng, double radiusKm, int limit) {
        int effectiveLimit = Math.min(Math.max(limit, 1), 500);
        return careRequestRepository.findNearby(lat, lng, radiusKm, effectiveLimit);
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

        // 위치 필터링 — 페이징 API와 동일하게 접두사 일치만 허용 (LIKE '값%')
        if (location != null && !location.isEmpty()) {
            final String locPrefix = location;
            requests = requests.stream()
                    .filter(r -> {
                        String ul = r.getUser().getLocation();
                        return ul != null && ul.startsWith(locPrefix);
                    })
                    .collect(Collectors.toList());
        }

        return careRequestConverter.toDTOList(requests);
    }

    /**
     * 펫케어 요청 목록 조회 (페이징)
     */
    @Transactional(readOnly = true)
    public CareRequestPageResponseDTO getCareRequestsWithPaging(String status, String location, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CareRequest> requestPage;

        if (status != null && !status.equals("ALL")) {
            CareRequestStatus statusEnum = CareRequestStatus.valueOf(status);
            requestPage = careRequestRepository.findByStatusAndIsDeletedFalseWithPaging(
                    statusEnum, location, pageable);
        } else {
            requestPage = careRequestRepository.findAllActiveRequestsWithPaging(location, pageable);
        }

        List<CareRequestDTO> dtos = careRequestConverter.toDTOList(requestPage.getContent());
        return new CareRequestPageResponseDTO(
                dtos,
                requestPage.getTotalElements(),
                requestPage.getTotalPages(),
                requestPage.getNumber(),
                requestPage.getSize(),
                requestPage.hasNext(),
                requestPage.hasPrevious());
    }

    /**
     * 펫케어 요청 검색 (페이징)
     */
    @Transactional(readOnly = true)
    public CareRequestPageResponseDTO searchCareRequestsWithPaging(String keyword, int page, int size) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return getCareRequestsWithPaging(null, null, page, size);
        }
        // FULLTEXT 네이티브 쿼리에서 ORDER BY created_at 정렬 — Pageable Sort 중복 방지
        Pageable pageable = PageRequest.of(page, size);
        Page<CareRequest> requestPage = careRequestRepository.searchWithPaging(keyword.trim(), pageable);
        List<CareRequestDTO> dtos = careRequestConverter.toDTOList(requestPage.getContent());
        return new CareRequestPageResponseDTO(
                dtos,
                requestPage.getTotalElements(),
                requestPage.getTotalPages(),
                requestPage.getNumber(),
                requestPage.getSize(),
                requestPage.hasNext(),
                requestPage.hasPrevious());
    }

    // 단일 케어 요청 조회
    @Transactional(readOnly = true)
    public CareRequestDTO getCareRequest(Long idx) {
        CareRequest request = careRequestRepository.findByIdWithApplications(idx)
                .orElseThrow(() -> new CareRequestNotFoundException());
        if (Boolean.TRUE.equals(request.getIsDeleted())) {
            throw new CareRequestNotFoundException();
        }
        if (!isAdmin() && isSanctionedPreMatchRequest(request)) {
            throw new CareRequestNotFoundException();
        }

        return careRequestConverter.toDTO(request);
    }

    // 케어 요청 생성
    @Transactional
    public CareRequestDTO createCareRequest(CareRequestDTO dto) {
        // 사용자 조회 (1회만)
        Users user = usersRepository.findById(dto.getUserId())
                .orElseThrow(() -> new UserNotFoundException());

        // 제재 상태 확인
        if (user.isSanctioned()) {
            throw CareForbiddenException.sanctioned();
        }

        // 이메일 인증 확인
        if (!Boolean.TRUE.equals(user.getEmailVerified())) {
            log.debug("이메일 인증 미완료: userId={}", dto.getUserId());
            throw new EmailVerificationRequiredException(
                    "펫케어 서비스 이용을 위해 이메일 인증이 필요합니다.",
                    EmailVerificationPurpose.PET_CARE);
        }

        // 잔액은 아래 holdForRequest 에서 비관적 락 안에서 검사·차감한다.
        // 여기서 미리 보던 확인은 잡지 않는 확인이라 TOCTOU 였다 — 확인과 차감 사이에 잔액을
        // 다른 데 쓰면 확정 순간에 깨졌고, 제공자는 신청·채팅을 마친 뒤에야 그 사실을 알았다.

        CareScheduleMode mode = dto.getScheduleMode() != null ? dto.getScheduleMode() : CareScheduleMode.FIXED;

        CareRequest.CareRequestBuilder builder = CareRequest.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .date(dto.getDate())
                .scheduleMode(mode)
                .estimatedDurationMinutes(dto.getEstimatedDurationMinutes())
                .user(user)
                .status(CareRequestStatus.OPEN)
                .offeredCoins(dto.getOfferedCoins())
                // 지도 근처 조회(findNearby)는 latitude IS NOT NULL 이어야 함 — 프론트가 보낸 좌표를 반드시 저장
                .address(dto.getAddress())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude());

        // 펫 정보 설정 (선택사항)
        if (dto.getPetIdx() != null) {
            Pet pet = petRepository.findById(dto.getPetIdx())
                    .orElseThrow(() -> new PetNotFoundException());
            // 펫 소유자 확인
            if (!pet.getUser().getIdx().equals(user.getIdx())) {
                throw CareForbiddenException.petOwnerOnly();
            }
            builder.pet(pet);
        }

        CareRequest saved = careRequestRepository.save(builder.build());

        // 등록과 동시에 제시 금액을 에스크로에 잡는다. 잔액이 모자라면 여기서 실패하고 글도 남지 않는다
        // (같은 트랜잭션). "올라와 있는 요청은 지급이 보증된다"가 이걸로 성립한다.
        petCoinEscrowService.holdForRequest(saved, user, saved.getOfferedCoins());

        String petType = saved.getPet() != null ? saved.getPet().getPetType().name() : null;
        eventPublisher.publishEvent(new CareRequestCreatedEvent(
                this, user.getIdx(), saved.getIdx(),
                saved.getTitle() + " " + saved.getDescription(), petType));
        return careRequestConverter.toDTO(saved);
    }

    // 케어 요청 수정
    @Transactional
    public CareRequestDTO updateCareRequest(Long idx, CareRequestDTO dto, Long currentUserId) {
        CareRequest request = careRequestRepository.findByIdWithApplications(idx)
                .orElseThrow(() -> new CareRequestNotFoundException());

        // 작성자 확인 (관리자는 우회)
        if (!isAdmin() && !request.getUser().getIdx().equals(currentUserId)) {
            throw CareForbiddenException.ownRequestOnly();
        }

        // 모집 중일 때만 수정 가능. 관리자도 예외가 아니다 — 관리자가 우회할 수 있으면 가드가 아니다.
        // 이전에는 상태 가드가 없어서 이미 완료된 케어의 날짜·장소·펫을 사후에 바꿀 수 있었다.
        if (request.getStatus() != CareRequestStatus.OPEN) {
            throw new IllegalStateException(
                    "모집 중(OPEN)인 요청만 수정할 수 있습니다. 현재 상태: " + request.getStatus());
        }

        // 제시 금액 수정 — 목표 금액을 받아 차액만큼 추가 차감하거나 환불한다.
        // 증분(+3000)이 아니라 목표값(8000)이라, 같은 요청이 두 번 도착해도 두 번째는 차액이 0 이
        // 되어 아무 일도 일어나지 않는다. 멱등키 없이 재시도 안전한 이유가 이 표현 방식에 있다.
        if (dto.getOfferedCoins() != null
                && !dto.getOfferedCoins().equals(request.getOfferedCoins())) {
            petCoinEscrowService.changeAmount(request, request.getUser(), dto.getOfferedCoins());
            // 변경 시각을 남겨, 이 시각 이전에 이뤄진 거래 확정을 낡은 동의로 판별할 수 있게 한다.
            request.changeOfferedCoins(dto.getOfferedCoins());
        }

        if (dto.getTitle() != null) {
            request.setTitle(dto.getTitle());
        }
        if (dto.getDescription() != null) {
            request.setDescription(dto.getDescription());
        }
        if (dto.getDate() != null) {
            request.setDate(dto.getDate());
        }
        if (dto.getScheduleMode() != null) {
            request.setScheduleMode(dto.getScheduleMode());
        }
        if (dto.getEstimatedDurationMinutes() != null) {
            request.setEstimatedDurationMinutes(dto.getEstimatedDurationMinutes());
        }
        if (dto.getAddress() != null) {
            request.setAddress(dto.getAddress());
        }
        if (dto.getLatitude() != null) {
            request.setLatitude(dto.getLatitude());
        }
        if (dto.getLongitude() != null) {
            request.setLongitude(dto.getLongitude());
        }

        // 펫 정보 업데이트 (선택사항)
        if (dto.getPetIdx() != null) {
            Pet pet = petRepository.findById(dto.getPetIdx())
                    .orElseThrow(() -> new PetNotFoundException());
            // 펫 소유자 확인
            if (!pet.getUser().getIdx().equals(request.getUser().getIdx())) {
                throw CareForbiddenException.petOwnerOnly();
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
        CareRequest request = careRequestRepository.findByIdWithUser(idx)
                .orElseThrow(() -> new CareRequestNotFoundException());

        // 작성자 확인 (관리자는 우회)
        if (!isAdmin() && !request.getUser().getIdx().equals(currentUserId)) {
            throw CareForbiddenException.ownRequestOnly();
        }

        // 진행 중이거나 끝난 거래는 지울 수 없다. 상대가 있는 계약이고, 보관 중인 코인의
        // 귀속이 삭제로 사라지면 안 된다. 관리자도 예외가 아니다.
        if (request.getStatus() != CareRequestStatus.OPEN) {
            throw new IllegalStateException(
                    "모집 중(OPEN)인 요청만 삭제할 수 있습니다. 현재 상태: " + request.getStatus());
        }

        // 등록 시 잡아둔 코인을 돌려준다. 내부 코인은 플랫폼이 돌려주지 않으면 회수 수단이 없다.
        PetCoinEscrow escrow = petCoinEscrowService.findByCareRequest(request);
        if (escrow != null && escrow.getStatus() == EscrowStatus.HOLD) {
            petCoinEscrowService.refundToRequester(escrow);
            log.info("요청 삭제로 보관 코인 환불: careRequestIdx={}, escrowIdx={}, amount={}",
                    request.getIdx(), escrow.getIdx(), escrow.getAmount());
        }

        request.softDelete();
        careRequestRepository.save(request);
    }

    // 내 케어 요청 조회
    @Transactional(readOnly = true)
    public List<CareRequestDTO> getMyCareRequests(Long userId) {
        Users user = usersRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException());

        List<CareRequest> requests = careRequestRepository.findByUserAndIsDeletedFalseOrderByCreatedAtDesc(user);
        return careRequestConverter.toDTOList(requests);
    }

    // 상태 변경
    @Transactional
    public CareRequestDTO updateStatus(Long idx, String status, Long currentUserId) {
        CareRequest request = careRequestRepository.findByIdWithApplications(idx)
                .orElseThrow(() -> new CareRequestNotFoundException());

        if (Boolean.TRUE.equals(request.getIsDeleted())) {
            throw new CareRequestNotFoundException();
        }

        // 관리자는 권한 검증 우회
        if (!isAdmin()) {
            // 스케줄러 등 시스템 작업(CareRequestScheduler에서 currentUserId == null)은 검증 생략
            if (currentUserId != null) {
                boolean isRequester = request.getUser().getIdx().equals(currentUserId);
                boolean isAcceptedProvider = request.getApplications() != null
                        && request.getApplications().stream()
                                .anyMatch(app -> app.getStatus() == CareApplicationStatus.ACCEPTED
                                && app.getProvider().getIdx().equals(currentUserId));

                if (!isRequester && !isAcceptedProvider) {
                    throw CareForbiddenException.ownerOrApprovedProvider();
                }
            }
        }

        CareRequestStatus oldStatus = request.getStatus();
        CareRequestStatus newStatus = CareRequestStatus.valueOf(status);

        // 완료는 양쪽이 이행을 확인해야 성립한다(confirmCompletion). 이 경로로 들어오는 COMPLETED 는
        // 한쪽 의사만으로 정산을 일으키므로 막는다 — 예전에는 제공자가 혼자 눌러 돈을 가져갈 수 있었다.
        // 관리자만 남겨두는 이유는 분쟁 조정처럼 당사자 합의가 불가능한 경우가 있기 때문이다.
        if (newStatus == CareRequestStatus.COMPLETED && !isAdmin()) {
            throw CareForbiddenException.ownerOrApprovedProvider();
        }

        if (!isAdmin() && isSettlementStatus(newStatus) && hasSanctionedCareParty(request)) {
            throw CareForbiddenException.sanctioned();
        }

        request.transitionTo(newStatus);
        CareRequest updated = careRequestRepository.save(request);

        // 상태가 COMPLETED로 변경될 때 에스크로에서 제공자에게 코인 지급
        if (oldStatus != CareRequestStatus.COMPLETED && newStatus == CareRequestStatus.COMPLETED) {
            // 비관적 락은 releaseToProvider() 내부에서만 수행 — 여기서 이중 락 잡으면 같은 TX 내에서
            // rollback-only 마킹 타이밍 충돌로 UnexpectedRollbackException 발생
            PetCoinEscrow escrow = petCoinEscrowService.findByCareRequest(request);
            if (escrow != null && escrow.getStatus() == EscrowStatus.HOLD) {
                // 여기서 밑에 메서드에서 락을 획득
                petCoinEscrowService.releaseToProvider(escrow);
                log.info("거래 완료 시 제공자에게 코인 지급 완료: careRequestIdx={}, escrowIdx={}, amount={}",
                        request.getIdx(), escrow.getIdx(), escrow.getAmount());
            } else {
                log.warn("에스크로를 찾을 수 없거나 이미 처리됨: careRequestIdx={}", request.getIdx());
            }
        }

        // 상태가 CANCELLED로 변경될 때 에스크로에서 요청자에게 코인 환불
        if (newStatus == CareRequestStatus.CANCELLED) {
            PetCoinEscrow escrow = petCoinEscrowService.findByCareRequest(request);
            if (escrow != null && escrow.getStatus() == EscrowStatus.HOLD) {
                petCoinEscrowService.refundToRequester(escrow);
                log.info("거래 취소 시 요청자에게 코인 환불 완료: careRequestIdx={}, escrowIdx={}, amount={}",
                        request.getIdx(), escrow.getIdx(), escrow.getAmount());
            } else {
                log.warn("에스크로를 찾을 수 없거나 이미 처리됨: careRequestIdx={}", request.getIdx());
            }
        }

        return careRequestConverter.toDTO(updated);
    }

    /**
     * 이행 완료 확인. 요청자와 제공자가 각자 한 번씩 누르고, 양쪽이 모두 확인해야 정산된다.
     *
     * 왜 한쪽으로는 안 되는가: 예전에는 updateStatus 를 요청자 "또는" 승인된 제공자 아무나
     * 호출할 수 있었고 COMPLETED 가 되는 순간 에스크로가 지급됐다. 제공자가 혼자 완료를 눌러
     * 요청자 동의 없이 돈을 가져갈 수 있는 구조였다.
     *
     * 왜 비관적 락인가: 양쪽이 동시에 누르면 둘 다 "상대도 확인했나"를 읽고 각자 정산으로 넘어갈
     * 수 있다. 읽고 판단해서 쓰는 구간이라 원자적 UPDATE 로는 대체되지 않아 행 락으로 직렬화한다.
     */
    @Transactional
    public CareRequestDTO confirmCompletion(Long idx, Long currentUserId) {
        CareRequest request = careRequestRepository.findByIdForUpdate(idx)
                .orElseThrow(() -> new CareRequestNotFoundException());

        if (Boolean.TRUE.equals(request.getIsDeleted())) {
            throw new CareRequestNotFoundException();
        }

        if (request.getStatus() != CareRequestStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "진행 중(IN_PROGRESS)인 케어만 완료 확인할 수 있습니다. 현재 상태: " + request.getStatus());
        }

        boolean isRequester = request.getUser().getIdx().equals(currentUserId);
        boolean isAcceptedProvider = request.getApplications() != null
                && request.getApplications().stream()
                        .anyMatch(app -> app.getStatus() == CareApplicationStatus.ACCEPTED
                                && app.getProvider().getIdx().equals(currentUserId));

        if (!isRequester && !isAcceptedProvider) {
            throw CareForbiddenException.ownerOrApprovedProvider();
        }

        if (hasSanctionedCareParty(request)) {
            throw CareForbiddenException.sanctioned();
        }

        // 이미 확인한 쪽이 다시 눌러도 시각을 덮어쓰지 않는다(재시도 안전).
        request.confirmCompletionBy(isRequester);

        if (request.isBothCompletionConfirmed()) {
            request.transitionTo(CareRequestStatus.COMPLETED);
            PetCoinEscrow escrow = petCoinEscrowService.findByCareRequest(request);
            if (escrow != null && escrow.getStatus() == EscrowStatus.HOLD) {
                petCoinEscrowService.releaseToProvider(escrow);
                log.info("양쪽 확인 완료 — 제공자에게 코인 지급: careRequestIdx={}, escrowIdx={}, amount={}",
                        request.getIdx(), escrow.getIdx(), escrow.getAmount());
            } else {
                log.warn("양쪽 확인 완료되었으나 지급할 에스크로가 없음: careRequestIdx={}", request.getIdx());
            }
        } else {
            log.info("한쪽 이행 완료 확인 — 상대 확인 대기: careRequestIdx={}, byRequester={}",
                    request.getIdx(), isRequester);
        }

        return careRequestConverter.toDTO(careRequestRepository.save(request));
    }

    private boolean isSanctionedPreMatchRequest(CareRequest request) {
        return request.getUser().isSanctioned()
                && (request.getStatus() == CareRequestStatus.OPEN
                || request.getStatus() == CareRequestStatus.CANCELLED);
    }

    private boolean isSettlementStatus(CareRequestStatus status) {
        return status == CareRequestStatus.COMPLETED || status == CareRequestStatus.CANCELLED;
    }

    private boolean hasSanctionedCareParty(CareRequest request) {
        if (request.getUser().isSanctioned()) {
            return true;
        }
        if (request.getApplications() == null) {
            return false;
        }
        return request.getApplications().stream()
                .filter(app -> app.getStatus() == CareApplicationStatus.ACCEPTED)
                .anyMatch(app -> app.getProvider().isSanctioned());
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

    // 케어 요청 복구 (관리자용)
    @Transactional
    public CareRequestDTO restoreForAdmin(Long id) {
        CareRequest request = careRequestRepository.findById(id)
                .orElseThrow(CareRequestNotFoundException::new);
        request.restore();
        return careRequestConverter.toDTO(careRequestRepository.save(request));
    }
}
