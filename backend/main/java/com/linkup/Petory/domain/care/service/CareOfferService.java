package com.linkup.Petory.domain.care.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.care.converter.CareApplicationConverter;
import com.linkup.Petory.domain.care.dto.CareApplicationDTO;
import com.linkup.Petory.domain.care.dto.CareProviderDTO;
import com.linkup.Petory.domain.care.repository.CareReviewRepository;
import com.linkup.Petory.domain.care.entity.CareApplication;
import com.linkup.Petory.domain.care.entity.CareApplicationStatus;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.exception.CareApplicationNotFoundException;
import com.linkup.Petory.domain.care.exception.CareConflictException;
import com.linkup.Petory.domain.care.exception.CareForbiddenException;
import com.linkup.Petory.domain.care.exception.CareRequestNotFoundException;
import com.linkup.Petory.domain.care.repository.CareApplicationRepository;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.notification.entity.NotificationType;
import com.linkup.Petory.domain.notification.service.NotificationService;
import com.linkup.Petory.domain.payment.service.PetCoinEscrowService;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.UserNotFoundException;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.user.util.SanctionGuard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 케어 계약 체결. 요청자가 제공자에게 제안하고, 제공자가 수락하면 계약이 성립한다.
 *
 * <p>예전에는 이 일을 채팅방이 했다. 양쪽이 같은 "거래 확정" 버튼을 각자 누르고, 둘 다 눌렀으면
 * 성립하는 대칭 구조였다({@code ConversationService.confirmCareDeal}). 그 구조는 세 가지를
 * 떠안았다.
 * <ul>
 *   <li>동의가 두 개라 서로 다른 금액에 대한 동의일 수 있었다 — 확정할 때마다 기존 동의를 현재
 *       금액과 대조해 무효화하는 로직이 필요했다.</li>
 *   <li>계약 상태(확정 플래그)가 채팅 참여자 행에 살았다 — 방을 나가면 계약 동의가 사라졌고,
 *       care 도메인만 봐서는 계약이 어디까지 갔는지 알 수 없었다.</li>
 *   <li>계약의 식별자가 채팅방이라, 방 타입이 어긋나면 계약 경로 전체가 조용히 멈췄다.</li>
 * </ul>
 *
 * <p>제안/수락은 동의가 한 번뿐이라 위 셋이 전부 사라진다. 계약은 care 안에만 있고, 채팅방은
 * 계약을 모르는 순수 대화 수단이 된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CareOfferService {

    private final CareRequestRepository careRequestRepository;
    private final CareApplicationRepository careApplicationRepository;
    private final UsersRepository usersRepository;
    private final CareApplicationConverter careApplicationConverter;
    private final PetCoinEscrowService petCoinEscrowService;
    private final NotificationService notificationService;
    private final CareReviewRepository careReviewRepository;

    /**
     * 요청자가 제공자 한 명에게 케어를 제안한다.
     *
     * <p>여러 명에게 동시에 제안할 수 있다. 먼저 수락한 사람이 계약자가 되고 나머지 제안은
     * 그때 거절 처리된다 — 요청자가 한 명씩 답을 기다리지 않아도 된다.
     *
     * <p>같은 제공자에게 두 번 제안하면 기존 제안을 그대로 돌려준다. 재시도로 같은 요청이 두 번
     * 도착해도 결과가 같다(테이블에도 {@code UNIQUE(care_request_idx, provider_idx)} 가 있다).
     */
    @Transactional
    public CareApplicationDTO offerTo(Long careRequestIdx, Long providerId, Long currentUserId) {
        CareRequest request = careRequestRepository.findByIdWithApplications(careRequestIdx)
                .orElseThrow(CareRequestNotFoundException::new);

        if (Boolean.TRUE.equals(request.getIsDeleted())) {
            throw new CareRequestNotFoundException();
        }
        if (!request.getUser().getIdx().equals(currentUserId)) {
            throw CareForbiddenException.ownRequestOnly();
        }
        if (request.getStatus() != CareRequestStatus.OPEN) {
            throw CareConflictException.requestNotOpen(request.getStatus());
        }
        if (providerId.equals(currentUserId)) {
            throw CareForbiddenException.cannotOfferToSelf();
        }

        SanctionGuard.check(request.getUser(), CareForbiddenException::sanctioned);

        Users provider = usersRepository.findById(providerId)
                .orElseThrow(UserNotFoundException::new);
        if (provider.getRole() != Role.SERVICE_PROVIDER) {
            throw CareForbiddenException.providerRoleRequired();
        }
        SanctionGuard.check(provider, CareForbiddenException::sanctioned);

        CareApplication offer = findOfferTo(request, providerId);
        if (offer != null && isLive(offer)) {
            // 살아 있는 제안이 이미 있으면 그대로 돌려준다(재시도 안전).
            return careApplicationConverter.toDTO(offer);
        }

        if (offer != null) {
            // 거절·철회로 끝난 제안이 남아 있다. UNIQUE(care_request_idx, provider_idx) 때문에
            // 새 행을 만들 수 없으므로 그 행을 새 금액으로 되살린다.
            // 이 분기가 없으면 한 번 거절당한 제공자에게는 영영 다시 제안할 수 없는데,
            // 화면에는 "제안을 보냈습니다"가 떠서 보낸 줄로 착각하게 된다.
            offer.reopen(request.getOfferedCoins());
            careApplicationRepository.saveAndFlush(offer);
            log.info("케어 제안 재전송: careRequestIdx={}, providerId={}, amount={}",
                    request.getIdx(), providerId, request.getOfferedCoins());
        } else {
            offer = careApplicationRepository.saveAndFlush(CareApplication.builder()
                    .careRequest(request)
                    .provider(provider)
                    .status(CareApplicationStatus.PENDING)
                    .offeredCoins(request.getOfferedCoins())
                    .build());
            log.info("케어 제안 생성: careRequestIdx={}, providerId={}, amount={}",
                    request.getIdx(), providerId, request.getOfferedCoins());
        }

        notificationService.createNotification(
                providerId,
                NotificationType.CARE_OFFER_RECEIVED,
                "케어 제안이 도착했습니다",
                String.format("%s님이 \"%s\" 케어를 %,d 코인에 맡기고 싶어 합니다.",
                        request.getUser().getUsername(), request.getTitle(),
                        request.getOfferedCoins() == null ? 0 : request.getOfferedCoins()),
                request.getIdx(),
                "CARE_REQUEST");

        return careApplicationConverter.toDTO(offer);
    }

    /**
     * 제공자가 제안을 수락한다. 여기서 계약이 성립한다 — 지원 승인 + 요청 진행중 전이 +
     * 에스크로 지급 대상 배정이 한 트랜잭션에서 일어난다.
     *
     * <p>왜 요청 행을 잠그는가: 여러 명에게 제안이 나가 있으면 두 제공자가 동시에 수락할 수 있다.
     * 둘 다 "아직 OPEN 이네"를 읽고 각자 배정으로 넘어가면 지급 대상이 덮어써진다. 읽고 판단해서
     * 쓰는 구간이라 원자적 UPDATE 로 대체되지 않아 행 락으로 직렬화한다.
     */
    @Transactional
    public CareApplicationDTO acceptOffer(Long applicationIdx, Long currentUserId) {
        CareApplication offer = careApplicationRepository.findById(applicationIdx)
                .orElseThrow(CareApplicationNotFoundException::new);

        if (!offer.getProvider().getIdx().equals(currentUserId)) {
            throw CareForbiddenException.offerReceiverOnly();
        }
        if (offer.getStatus() != CareApplicationStatus.PENDING) {
            throw CareConflictException.offerNotPending(offer.getStatus());
        }

        // 잠근 인스턴스로 다시 읽는다. 같은 영속성 컨텍스트라 offer.getCareRequest() 와 동일
        // 객체가 되고, 아래 상태 전이·배정이 전부 이 락 안에서 일어난다.
        CareRequest request = careRequestRepository
                .findByIdForUpdate(offer.getCareRequest().getIdx())
                .orElseThrow(CareRequestNotFoundException::new);

        if (Boolean.TRUE.equals(request.getIsDeleted())) {
            throw new CareRequestNotFoundException();
        }
        if (request.getStatus() != CareRequestStatus.OPEN) {
            throw CareConflictException.requestNotOpen(request.getStatus());
        }

        Users requester = request.getUser();
        Users provider = offer.getProvider();
        SanctionGuard.check(requester, CareForbiddenException::sanctioned);
        SanctionGuard.check(provider, CareForbiddenException::sanctioned);

        // 제안을 보낸 뒤 요청자가 금액을 바꿨으면, 제공자가 화면에서 본 금액과 실제로 성립할
        // 계약이 어긋난다. 수락을 거절하고 요청자가 새 금액으로 다시 제안하게 한다.
        // (제안 시점 금액이 없는 옛 지원은 대조 대상이 아니다 — V18 참고.)
        if (offer.getOfferedCoins() != null
                && !offer.getOfferedCoins().equals(request.getOfferedCoins())) {
            throw CareConflictException.offeredAmountChanged(request.getOfferedCoins());
        }

        offer.accept();

        // 같은 요청의 나머지 제안은 선정되지 않았다. PENDING 으로 두면 그 제공자들은 계속
        // 답을 기다리는 줄 알게 된다.
        if (request.getApplications() != null) {
            for (CareApplication other : request.getApplications()) {
                if (!other.getIdx().equals(offer.getIdx())
                        && other.getStatus() == CareApplicationStatus.PENDING) {
                    other.reject();
                    careApplicationRepository.saveAndFlush(other);
                }
            }
        }

        request.transitionTo(CareRequestStatus.IN_PROGRESS);
        careRequestRepository.save(request);

        // 코인은 요청 등록 시 이미 에스크로에 잡혀 있다. 여기서 하는 일은 지급 대상을 배정하는
        // 것뿐이고, 잔액이 모자라 깨지는 일은 없다.
        //
        // 실패해도 예외는 그대로 전파한다. 예전 확정 코드는 이 호출을 try/catch 로 삼키고
        // "확정은 진행한다"는 주석을 달아뒀는데, REQUIRED 로 같은 트랜잭션에 합류하므로 실패가
        // rollback-only 를 남겼다 — 삼켜도 바깥 커밋에서 터질 뿐, 의도한 "배정 없는 확정"은
        // 애초에 만들어질 수 없었다. 전파의 실효는 롤백이 아니라 원인을 남기는 것이다.
        petCoinEscrowService.assignProvider(request, provider, offer, offer.getOfferedCoins());

        log.info("케어 제안 수락 — 계약 성립: careRequestIdx={}, applicationIdx={}, providerId={}, amount={}",
                request.getIdx(), offer.getIdx(), provider.getIdx(), request.getOfferedCoins());

        notificationService.createNotification(
                requester.getIdx(),
                NotificationType.CARE_OFFER_ACCEPTED,
                "케어 제안이 수락되었습니다",
                String.format("%s님이 \"%s\" 케어를 맡기로 했습니다. 케어가 시작됩니다.",
                        provider.getUsername(), request.getTitle()),
                request.getIdx(),
                "CARE_REQUEST");

        return careApplicationConverter.toDTO(offer);
    }

    /**
     * 제공자가 제안을 거절한다. 요청은 OPEN 그대로라 요청자가 다른 사람에게 다시 제안할 수 있다.
     */
    @Transactional
    public CareApplicationDTO rejectOffer(Long applicationIdx, Long currentUserId) {
        CareApplication offer = careApplicationRepository.findById(applicationIdx)
                .orElseThrow(CareApplicationNotFoundException::new);

        if (!offer.getProvider().getIdx().equals(currentUserId)) {
            throw CareForbiddenException.offerReceiverOnly();
        }
        if (offer.getStatus() != CareApplicationStatus.PENDING) {
            throw CareConflictException.offerNotPending(offer.getStatus());
        }

        offer.reject();
        careApplicationRepository.saveAndFlush(offer);

        log.info("케어 제안 거절: applicationIdx={}, providerId={}", applicationIdx, currentUserId);

        CareRequest rejected = offer.getCareRequest();
        notificationService.createNotification(
                rejected.getUser().getIdx(),
                NotificationType.CARE_OFFER_REJECTED,
                "케어 제안이 거절되었습니다",
                String.format("%s님이 \"%s\" 케어 제안을 거절했습니다. 다른 제공자에게 제안할 수 있습니다.",
                        offer.getProvider().getUsername(), rejected.getTitle()),
                rejected.getIdx(),
                "CARE_REQUEST");

        return careApplicationConverter.toDTO(offer);
    }

    /**
     * 두 사람 사이에 살아 있는 제안. 채팅방이 계약을 모르게 되면서 필요해진 조회다 —
     * 방에서 "제안 수락/거절"이나 "이행 완료 확인"을 띄우려면 방이 아니라 두 당사자로 찾는다.
     */
    @Transactional(readOnly = true)
    public List<CareApplicationDTO> findLiveOffersBetween(Long currentUserId, Long otherUserId) {
        return careApplicationRepository.findLiveOffersBetween(currentUserId, otherUserId)
                .stream()
                .map(careApplicationConverter::toDTO)
                .toList();
    }

    /**
     * 이 요청에 제안할 수 있는 제공자 목록. 요청자가 먼저 움직이는 유일한 경로다.
     *
     * <p>그전에는 <b>제공자가 먼저 문의해 와야만</b> 요청자가 제안할 수 있었다. 등록해놓고
     * 기다리는 것 말고 할 수 있는 게 없었고, 아무도 안 오면 거기서 끝이었다.
     *
     * <p>지역만 맞추고 <b>정렬하지 않는다.</b> 평점·완료건수로 줄 세우면 리뷰가 없는 신규
     * 제공자가 영영 안 뽑혀 상위 몇 명에게만 일이 몰린다 — 두 수치는 화면에 표시만 하고,
     * 정렬 규칙은 쏠림이 실제로 관측된 뒤에 넣는다.
     *
     * <p>집계는 배치로 한 번씩만 돈다(평점 1문 + 완료건수 1문). 1인당 한 번씩 부르면 목록
     * 길이만큼 N+1 이 된다.
     */
    @Transactional(readOnly = true)
    public List<CareProviderDTO> findCandidates(Long careRequestIdx, Long currentUserId) {
        CareRequest request = careRequestRepository.findByIdWithApplications(careRequestIdx)
                .orElseThrow(CareRequestNotFoundException::new);
        if (Boolean.TRUE.equals(request.getIsDeleted())) {
            throw new CareRequestNotFoundException();
        }
        if (!request.getUser().getIdx().equals(currentUserId)) {
            throw CareForbiddenException.ownRequestOnly();
        }

        // 지역 기준은 요청자의 프로필이 아니라 케어가 실제로 일어나는 곳(요청 주소)이다.
        String area = request.getAddress();

        List<Users> candidates = usersRepository.findActiveServiceProviders(currentUserId).stream()
                .filter(u -> CareProviderLocation.sameArea(area, u.getLocation()))
                .toList();

        if (candidates.isEmpty()) {
            return List.of();
        }

        List<Long> ids = candidates.stream().map(Users::getIdx).toList();

        Map<Long, double[]> ratingByProvider = new HashMap<>();
        for (Object[] row : careReviewRepository.aggregateByRevieweeIdxs(ids)) {
            ratingByProvider.put((Long) row[0],
                    new double[] { ((Number) row[1]).doubleValue(), ((Number) row[2]).doubleValue() });
        }

        Map<Long, Long> completedByProvider = new HashMap<>();
        for (Object[] row : careApplicationRepository.countCompletedByProviderIdxs(ids)) {
            completedByProvider.put((Long) row[0], ((Number) row[1]).longValue());
        }

        Set<Long> alreadyOffered = request.getApplications() == null ? Set.of()
                : request.getApplications().stream()
                        .filter(this::isLive)
                        .map(app -> app.getProvider().getIdx())
                        .collect(Collectors.toSet());

        return candidates.stream()
                .map(u -> {
                    double[] rating = ratingByProvider.get(u.getIdx());
                    return CareProviderDTO.builder()
                            .idx(u.getIdx())
                            .username(u.getUsername())
                            .nickname(u.getNickname())
                            .location(u.getLocation())
                            .averageRating(rating == null ? null : rating[0])
                            .reviewCount(rating == null ? 0 : (int) rating[1])
                            .completedCareCount(completedByProvider.getOrDefault(u.getIdx(), 0L))
                            .alreadyOffered(alreadyOffered.contains(u.getIdx()))
                            .build();
                })
                .toList();
    }

    private boolean isLive(CareApplication offer) {
        return offer.getStatus() == CareApplicationStatus.PENDING
                || offer.getStatus() == CareApplicationStatus.ACCEPTED;
    }

    /** 이 제공자에게 보낸 제안. 상태와 무관하게 찾는다 — 끝난 제안도 되살릴 대상이기 때문이다. */
    private CareApplication findOfferTo(CareRequest request, Long providerId) {
        if (request.getApplications() == null) {
            return null;
        }
        return request.getApplications().stream()
                .filter(app -> app.getProvider().getIdx().equals(providerId))
                .findFirst()
                .orElse(null);
    }
}
