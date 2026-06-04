package com.linkup.Petory.domain.meetup.service;

import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.SliceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.linkup.Petory.domain.chat.service.ConversationService;
import com.linkup.Petory.domain.meetup.annotation.Timed;
import com.linkup.Petory.domain.meetup.converter.MeetupConverter;
import com.linkup.Petory.domain.meetup.converter.MeetupParticipantsConverter;
import com.linkup.Petory.domain.meetup.dto.MeetupDTO;
import com.linkup.Petory.domain.meetup.dto.MeetupHistoryDTO;
import com.linkup.Petory.domain.meetup.dto.MeetupParticipantsDTO;
import com.linkup.Petory.domain.meetup.entity.Meetup;
import com.linkup.Petory.domain.meetup.entity.MeetupParticipants;
import com.linkup.Petory.domain.meetup.entity.MeetupStatus;
import com.linkup.Petory.domain.meetup.event.MeetupCreatedEvent;
import com.linkup.Petory.domain.meetup.exception.MeetupConflictException;
import com.linkup.Petory.domain.meetup.exception.MeetupForbiddenException;
import com.linkup.Petory.domain.meetup.exception.MeetupNotFoundException;
import com.linkup.Petory.domain.meetup.exception.MeetupParticipantNotFoundException;
import com.linkup.Petory.domain.meetup.exception.MeetupValidationException;
import com.linkup.Petory.domain.meetup.repository.MeetupParticipantsRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupRepository;
import com.linkup.Petory.domain.user.entity.Role;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException;
import com.linkup.Petory.domain.user.exception.UserNotFoundException;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.global.exception.ApiException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetupService {

    /** 근처 모임 네이티브 조회 상한 (과다 로드 방지) */
    public static final int DEFAULT_NEARBY_MAX_RESULTS = 500;

    /** 위치·키워드·주최자별 목록 조회 상한 (OOM 방지, 풀 페이징 전환 전 임시 상한) */
    public static final int MAX_LIST_SIZE = 500;

    private final MeetupRepository meetupRepository;
    private final MeetupParticipantsRepository meetupParticipantsRepository;
    private final UsersRepository usersRepository;
    private final MeetupConverter converter;
    private final MeetupParticipantsConverter participantsConverter;
    private final ConversationService conversationService;
    private final ApplicationEventPublisher eventPublisher;

    @PersistenceContext
    private EntityManager entityManager;

    // 모임 생성
    @Transactional
    public MeetupDTO createMeetup(MeetupDTO meetupDTO, String userId) {
        // userId로 사용자 찾기 (id 필드 사용)
        Users organizer = usersRepository.findByIdString(userId)
                .orElseThrow(UserNotFoundException::new);

        // 이메일 인증 확인
        if (organizer.getEmailVerified() == null || !organizer.getEmailVerified()) {
            throw new EmailVerificationRequiredException(
                    "모임 생성을 위해 이메일 인증이 필요합니다.",
                    com.linkup.Petory.domain.user.entity.EmailVerificationPurpose.MEETUP);
        }

        // 날짜 검증
        if (meetupDTO.getDate() != null && meetupDTO.getDate().isBefore(LocalDateTime.now())) {
            throw MeetupValidationException.dateMustBeFuture();
        }

        Integer maxParticipantsOrDefault = meetupDTO.getMaxParticipants();
        if (maxParticipantsOrDefault == null) {
            maxParticipantsOrDefault = 10;
        }

        Meetup meetup = Meetup.builder()
                .title(meetupDTO.getTitle())
                .description(meetupDTO.getDescription())
                .location(meetupDTO.getLocation())
                .latitude(meetupDTO.getLatitude())
                .longitude(meetupDTO.getLongitude())
                .date(meetupDTO.getDate())
                .organizer(organizer)
                .maxParticipants(maxParticipantsOrDefault)
                .currentParticipants(1)
                .status(MeetupStatus.RECRUITING)
                .build();

        Meetup savedMeetup = meetupRepository.save(meetup);

        // 주최자를 자동으로 참가자에 추가
        MeetupParticipants organizerParticipant = MeetupParticipants.builder()
                .meetup(savedMeetup)
                .user(organizer)
                .joinedAt(LocalDateTime.now())
                .build();
        meetupParticipantsRepository.save(organizerParticipant);

        // 모임 생성 완료 이벤트 발행 (트랜잭션 커밋 후 비동기로 채팅방 생성 처리)
        // 핵심 도메인(모임)과 파생 도메인(채팅방) 분리: 채팅방 생성 실패가 모임 생성까지 롤백하지 않음
        // TransactionSynchronization을 사용하여 트랜잭션 커밋 후 이벤트 발행 보장
        // "트랜잭션이 성공적으로 commit되면 그때 이 코드를 실행해줘" 이란 의미
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eventPublisher.publishEvent(new MeetupCreatedEvent(
                        MeetupService.this,
                        savedMeetup.getIdx(),
                        organizer.getIdx(),
                        savedMeetup.getTitle()));
            }
        });

        log.info("모임 생성 완료: meetupIdx={}, organizer={}", savedMeetup.getIdx(), userId);
        return converter.toDTO(savedMeetup);
    }

    // 모임 수정
    // [FIX] userId 파라미터 추가 후 주최자 검증 — 기존 인증만 되면 누구나 수정 가능하던 보안 이슈 수정
    @Transactional
    public MeetupDTO updateMeetup(Long meetupIdx, MeetupDTO meetupDTO, String userId) {
        // [리팩토링] findByIdWithDetails(참가자 전체 FETCH) → findByIdWithOrganizer(주최자만 FETCH)
        // updateMeetup에는 organizer 확인만 필요하므로 참가자 FETCH 불필요
        Meetup meetup = meetupRepository.findByIdWithOrganizer(meetupIdx)
                .orElseThrow(MeetupNotFoundException::new);

        Users currentUser = usersRepository.findByIdString(userId)
                .orElseThrow(UserNotFoundException::new);

        boolean isOrganizer = meetup.getOrganizer().getIdx().equals(currentUser.getIdx());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MASTER;
        if (!isOrganizer && !isAdmin) {
            throw MeetupForbiddenException.notOrganizer();
        }

        if (meetupDTO.getTitle() != null) {
            meetup.setTitle(meetupDTO.getTitle());
        }

        if (meetupDTO.getDescription() != null) {
            meetup.setDescription(meetupDTO.getDescription());
        }
        if (meetupDTO.getLocation() != null) {
            meetup.setLocation(meetupDTO.getLocation());
        }
        if (meetupDTO.getLatitude() != null) {
            meetup.setLatitude(meetupDTO.getLatitude());
        }
        if (meetupDTO.getLongitude() != null) {
            meetup.setLongitude(meetupDTO.getLongitude());
        }
        if (meetupDTO.getDate() != null) {
            if (meetupDTO.getDate().isBefore(LocalDateTime.now())) {
                throw MeetupValidationException.dateMustBeFuture();
            }
            meetup.setDate(meetupDTO.getDate());
        }
        if (meetupDTO.getMaxParticipants() != null) {
            int newMax = meetupDTO.getMaxParticipants();
            if (newMax < 1) {
                throw MeetupValidationException.invalidMaxParticipants();
            }
            if (newMax < meetup.getCurrentParticipants()) {
                throw MeetupValidationException.maxBelowCurrent();
            }
            meetup.setMaxParticipants(newMax);
        }

        Meetup savedMeetup = meetupRepository.save(meetup);
        return converter.toDTO(savedMeetup);
    }

    // 모임 삭제 (소프트 삭제)
    // [FIX] userId 파라미터 추가 후 주최자 검증 — 기존 인증만 되면 누구나 삭제 가능하던 보안 이슈 수정
    @Transactional
    public void deleteMeetup(Long meetupIdx, String userId) {
        Meetup meetup = meetupRepository.findByIdWithOrganizer(meetupIdx)
                .orElseThrow(MeetupNotFoundException::new);

        Users currentUser = usersRepository.findByIdString(userId)
                .orElseThrow(UserNotFoundException::new);

        boolean isOrganizer = meetup.getOrganizer().getIdx().equals(currentUser.getIdx());
        boolean isAdmin = currentUser.getRole() == Role.ADMIN || currentUser.getRole() == Role.MASTER;
        if (!isOrganizer && !isAdmin) {
            throw MeetupForbiddenException.notOrganizer();
        }

        meetup.softDelete();
        meetupRepository.save(meetup);

        log.info("모임 소프트 삭제 완료: meetupIdx={}", meetupIdx);
    }

    // 관리자용 모임 삭제 (사용자 검증 불필요)
    @Transactional
    public void deleteMeetupForAdmin(Long meetupIdx) {
        Meetup meetup = meetupRepository.findById(meetupIdx)
                .orElseThrow(MeetupNotFoundException::new);
        meetup.softDelete();
        meetupRepository.save(meetup);
        log.info("관리자 소프트 삭제: meetupIdx={}", meetupIdx);
    }

    // 모든 모임 조회 (소프트 삭제 제외)
    /** @deprecated 컨트롤러는 {@link #getAllMeetups(Pageable)}를 사용하세요. */
    @Deprecated(forRemoval = true)
    @Timed("getAllMeetups")
    public List<MeetupDTO> getAllMeetups() {
        List<Meetup> meetups = meetupRepository.findAllNotDeleted(); // 원래는 여기에서 n+1문제가 발생했지만 join fetch로 해결
        return converter.toDTOList(meetups);
    }

    /**
     * 모임 목록 페이징 (일반 사용자 API용)
     */
    @Timed("getAllMeetupsPaged")
    public Page<MeetupDTO> getAllMeetups(Pageable pageable) {
        return meetupRepository.findAllNotDeleted(pageable).map(converter::toDTO);
    }

    public Page<MeetupDTO> getMeetupsForAdmin(String status, String keyword, Pageable pageable) {
        return meetupRepository.findAllForAdmin(status, keyword, pageable).map(converter::toDTO);
    }

    // 특정 모임 조회 (참가자 목록 포함) - JOIN FETCH로 N+1 문제 해결
    public MeetupDTO getMeetupById(Long meetupIdx) {
        Meetup meetup = meetupRepository.findByIdWithDetails(meetupIdx)
                .orElseThrow(MeetupNotFoundException::new);
        return converter.toDTO(meetup);
    }

    /**
     * 반경 기반 모임 조회 (마커 표시용)
     * 네이티브로 ID·정렬·LIMIT 조회 후, 주최자는 IN + JOIN FETCH로 한 번에 로딩 (organizer N+1 방지).
     */
    @Timed("getNearbyMeetups")
    public List<MeetupDTO> getNearbyMeetups(Double lat, Double lng, Double radiusKm, int maxResults) {
        LocalDateTime now = LocalDateTime.now();
        int limit = Math.min(Math.max(maxResults, 1), 1000);
        log.info("반경 기반 모임 조회 요청: lat={}, lng={}, radius={}km, limit={}, currentDate={}",
                lat, lng, radiusKm, limit, now);

        List<Long> ids = meetupRepository.findNearbyMeetupIds(lat, lng, radiusKm, now, limit);
        log.info("DB 근처 모임 ID 수: {}", ids.size());
        if (ids.isEmpty()) {
            return List.of();
        }

        List<Meetup> loaded = meetupRepository.findByIdxInWithOrganizer(ids);
        Map<Long, Meetup> byId = loaded.stream().collect(Collectors.toMap(Meetup::getIdx, m -> m));

        List<MeetupDTO> result = ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(converter::toDTO)
                .collect(Collectors.toList());
        log.info("최종 결과 모임 수: {}", result.size());

        for (int i = 0; i < Math.min(10, result.size()); i++) {
            MeetupDTO m = result.get(i);
            log.info("✅ 반경 내 모임: idx={}, title={}, date={}, lat={}, lng={}",
                    m.getIdx(), m.getTitle(), m.getDate(), m.getLatitude(), m.getLongitude());
        }

        return result;
    }

    // 특정 모임의 참가자 목록 조회 (존재·삭제 여부 먼저 확인)
    public List<MeetupParticipantsDTO> getMeetupParticipants(Long meetupIdx) {
        meetupRepository.findByIdWithOrganizer(meetupIdx)
                .orElseThrow(MeetupNotFoundException::new);
        return participantsConverter.toDTOList(
                meetupParticipantsRepository.findByMeetupIdxOrderByJoinedAtAsc(meetupIdx));
    }

    // 모임 참가 (원자적 UPDATE 쿼리 방식 - 권장)
    @Transactional
    public MeetupParticipantsDTO joinMeetup(Long meetupIdx, String userId) {
        // 비관적 락으로 조회 — 동시 참가 요청 직렬화 (TOCTOU 방지)
        Meetup meetup = meetupRepository.findByIdWithLock(meetupIdx)
                .orElseThrow(MeetupNotFoundException::new);

        // 사용자 확인
        Users user = usersRepository.findByIdString(userId)
                .orElseThrow(UserNotFoundException::new);

        // 이메일 인증 확인
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            log.warn("이메일 인증이 필요합니다. meetupIdx={}, userId={}", meetupIdx, userId);
            throw new EmailVerificationRequiredException(
                    "모임 참여를 위해 이메일 인증이 필요합니다.",
                    com.linkup.Petory.domain.user.entity.EmailVerificationPurpose.MEETUP);
        }

        Long userIdx = user.getIdx();

        // 이미 참가했는지 확인
        if (meetupParticipantsRepository.existsByMeetupIdxAndUserIdx(meetupIdx, userIdx)) {
            log.warn("이미 참가한 모임입니다. meetupIdx={}, userId={}", meetupIdx, userId);
            throw MeetupConflictException.alreadyJoined();
        }

        // 주최자가 아닌 경우에만 인원 증가 (원자적 UPDATE 쿼리)
        if (!meetup.getOrganizer().getIdx().equals(userIdx)) {
            // 원자적 UPDATE 쿼리로 조건부 증가 (RECRUITING 상태 + 인원 미달 조건 동시 체크)
            int updated = meetupRepository.incrementParticipantsIfAvailable(meetupIdx, MeetupStatus.RECRUITING);
            if (updated == 0) {
                // RECRUITING 상태가 아니면 모집 마감, 상태는 맞지만 인원이 찼으면 fullCapacity
                if (meetup.getStatus() != MeetupStatus.RECRUITING) {
                    log.warn("모집이 마감된 모임입니다. meetupIdx={}, userId={}, status={}",
                            meetupIdx, userId, meetup.getStatus());
                    throw MeetupConflictException.meetupNotRecruiting();
                }
                log.warn("모임 인원이 가득 찼습니다. meetupIdx={}, userId={}, 현재인원={}, 최대인원={}",
                        meetupIdx, userId, meetup.getCurrentParticipants(), meetup.getMaxParticipants());
                throw MeetupConflictException.fullCapacity();
            }
            // [리팩토링] findById 2회 호출 제거 → entityManager.refresh()로 영속성 컨텍스트 동기화
            entityManager.refresh(meetup);
        }

        // 참가자 추가
        MeetupParticipants participant = MeetupParticipants.builder()
                .meetup(meetup)
                .user(user)
                .joinedAt(LocalDateTime.now())
                .build();

        MeetupParticipants savedParticipant;
        try {
            savedParticipant = meetupParticipantsRepository.save(participant);
        } catch (DataIntegrityViolationException e) {
            meetupRepository.decrementParticipantsIfPositive(meetupIdx);
            log.warn("중복 참가 시도 감지 (PK 충돌): meetupIdx={}, userIdx={}", meetupIdx, userIdx);
            throw MeetupConflictException.alreadyJoined();
        }

        log.info("모임 참가 완료. meetupIdx={}, userId={}, 현재인원={}, 최대인원={}",
                meetupIdx, userId, meetup.getCurrentParticipants(), meetup.getMaxParticipants());

        return participantsConverter.toDTO(savedParticipant);
    }

    // 모임 참가 취소
    @Transactional
    public void cancelMeetupParticipation(Long meetupIdx, String userId) {
        // 모임 존재 확인 (organizer fetch로 N+1 방지)
        Meetup meetup = meetupRepository.findByIdWithOrganizer(meetupIdx)
                .orElseThrow(MeetupNotFoundException::new);

        // 사용자 확인 (userId는 Users의 id 필드, 문자열)
        Users user = usersRepository.findByIdString(userId)
                .orElseThrow(UserNotFoundException::new);

        Long userIdx = user.getIdx(); // Users의 idx 필드 (Long)

        // 주최자는 참가 취소 불가
        if (meetup.getOrganizer().getIdx().equals(userIdx)) {
            throw MeetupForbiddenException.organizerCannotCancel();
        }

        // 참가자 확인
        MeetupParticipants participant = meetupParticipantsRepository
                .findByMeetupIdxAndUserIdx(meetupIdx, userIdx)
                .orElseThrow(MeetupParticipantNotFoundException::new);

        // 참가자 삭제
        meetupParticipantsRepository.delete(participant);

        // [FIX] 원자적 UPDATE 쿼리로 감소 — 기존 read-modify-write(Math.max)는 동시 취소 시 카운트 불일치 위험
        meetupRepository.decrementParticipantsIfPositive(meetupIdx);

        // 채팅방에서도 자동으로 나가기 (채팅 실패가 참가 취소를 막으면 안 됨)
        try {
            conversationService.leaveMeetupChat(meetupIdx, userIdx);
            log.info("채팅방에서 나가기 완료: meetupIdx={}, userIdx={}", meetupIdx, userIdx);
        } catch (ApiException e) {
            log.warn("채팅방 나가기 실패 (비즈니스): meetupIdx={}, userIdx={}, error={}", meetupIdx, userIdx, e.getMessage());
        } catch (Exception e) {
            log.error("채팅방 나가기 예상치 못한 오류: meetupIdx={}, userIdx={}", meetupIdx, userIdx, e);
        }

        log.info("모임 참가 취소 완료: meetupIdx={}, userId={}, userIdx={}", meetupIdx, userId, userIdx);
    }

    // 사용자가 특정 모임에 참가했는지 확인
    // [리팩토링] findByIdString(Users 전체 로딩) → findIdxByIdString(idx 스칼라만 조회)
    public boolean isUserParticipating(Long meetupIdx, String userId) {
        Long userIdx = usersRepository.findIdxByIdString(userId)
                .orElseThrow(UserNotFoundException::new);
        return meetupParticipantsRepository.existsByMeetupIdxAndUserIdx(meetupIdx, userIdx);
    }

    /**
     * 사용자의 모임 히스토리 조회.
     * participants → meetup → organizer를 fetch join한 단일 목록 조회를 사용해 카드 변환 중 N+1을
     * 막는다.
     */
    public List<MeetupHistoryDTO> getMeetupHistory(Long userIdx) {
        List<MeetupParticipants> histories = meetupParticipantsRepository.findByUserIdxOrderByJoinedAtDesc(userIdx);
        return histories.stream()
                .map(this::toHistoryDTO)
                .collect(Collectors.toList());
    }

    /**
     * 로그인 사용자의 모임 히스토리 좋아요 상태 변경.
     * 이미 참가/주최자로 기록된 모임에만 표시할 수 있으므로 별도 동시성 제어 없이 단건 행만 갱신한다.
     */
    @Transactional
    public MeetupHistoryDTO updateMyMeetupLike(Long meetupIdx, String userId, boolean liked) {
        Long userIdx = usersRepository.findIdxByIdString(userId)
                .orElseThrow(UserNotFoundException::new);
        MeetupParticipants participant = meetupParticipantsRepository
                .findByMeetupIdxAndUserIdxWithDetails(meetupIdx, userIdx)
                .orElseThrow(MeetupParticipantNotFoundException::new);

        participant.setLiked(liked);
        MeetupParticipants saved = meetupParticipantsRepository.save(participant);
        return toHistoryDTO(saved);
    }

    public MeetupHistoryDTO getMyMeetupParticipation(Long meetupIdx, String userId) {
        Long userIdx = usersRepository.findIdxByIdString(userId)
                .orElseThrow(UserNotFoundException::new);
        return meetupParticipantsRepository.findByMeetupIdxAndUserIdxWithDetails(meetupIdx, userIdx)
                .map(this::toHistoryDTO)
                .orElse(null);
    }

    private MeetupHistoryDTO toHistoryDTO(MeetupParticipants participant) {
        Meetup meetup = participant.getMeetup();
        Users organizer = meetup.getOrganizer();
        Long organizerIdx = organizer != null ? organizer.getIdx() : null;
        boolean isOrganizer = organizerIdx != null && organizerIdx.equals(participant.getUser().getIdx());

        return MeetupHistoryDTO.builder()
                .meetupIdx(meetup.getIdx())
                .title(meetup.getTitle())
                .location(meetup.getLocation())
                .date(meetup.getDate())
                .status(meetup.getStatus() != null ? meetup.getStatus().name() : null)
                .organizerIdx(organizerIdx)
                .organizerName(organizer != null ? organizer.getUsername() : null)
                .joinedAt(participant.getJoinedAt())
                .participationRole(isOrganizer ? "ORGANIZER" : "PARTICIPANT")
                .liked(Boolean.TRUE.equals(participant.getLiked()))
                .build();
    }

    // 지역별 모임 조회
    @Timed("getMeetupsByLocation")
    public List<MeetupDTO> getMeetupsByLocation(Double minLat, Double maxLat, Double minLng, Double maxLng) {
        List<Meetup> meetups = meetupRepository.findByLocationRange(minLat, maxLat, minLng, maxLng);
        return converter.toDTOList(meetups.size() > MAX_LIST_SIZE ? meetups.subList(0, MAX_LIST_SIZE) : meetups);
    }

    // 키워드로 모임 검색
    @Timed("searchMeetupsByKeyword")
    public List<MeetupDTO> searchMeetupsByKeyword(String keyword) {
        List<Meetup> meetups = meetupRepository.findByKeyword(keyword);
        return converter.toDTOList(meetups.size() > MAX_LIST_SIZE ? meetups.subList(0, MAX_LIST_SIZE) : meetups);
    }

    /**
     * @deprecated 컨트롤러는 {@link #getAvailableMeetups(Pageable)}를 사용하세요.
     *             이 메서드는 Pageable.unpaged()로 전량 조회합니다.
     */
    @Deprecated
    @Timed("getAvailableMeetups")
    public List<MeetupDTO> getAvailableMeetups() {
        List<Meetup> meetups = meetupRepository.findAvailableMeetups(LocalDateTime.now(), MeetupStatus.RECRUITING,
                Pageable.unpaged());
        return converter.toDTOList(meetups);
    }

    /**
     * 참여 가능한 모임 슬라이스 (count 쿼리 없이 다음 페이지 여부만)
     */
    @SuppressWarnings("null")
    @Timed("getAvailableMeetupsPaged")
    public Slice<MeetupDTO> getAvailableMeetups(Pageable pageable) {
        List<Meetup> list = meetupRepository.findAvailableMeetups(LocalDateTime.now(), MeetupStatus.RECRUITING,
                pageable);
        List<MeetupDTO> dtos = converter.toDTOList(list);
        boolean hasNext = pageable.isPaged() && list.size() == pageable.getPageSize();
        return new SliceImpl<>(dtos, pageable, hasNext);
    }

    /**
     * 홈 화면 모임 추천.
     * score = 0.4 * distScore + 0.4 * urgencyScore + 0.2 * capacityScore
     */
    public List<MeetupDTO> getHomeMeetups(Double lat, Double lng, int size) {
        Pageable fallbackPage = org.springframework.data.domain.PageRequest.of(
                0, size, org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.ASC, "date"));

        if (lat == null || lng == null) {
            return getAvailableMeetups(fallbackPage).getContent();
        }

        List<MeetupDTO> candidates = getNearbyMeetups(lat, lng, 50.0, size * 3);
        if (candidates.isEmpty()) {
            return getAvailableMeetups(fallbackPage).getContent();
        }

        long nowMs = System.currentTimeMillis();

        List<MeetupDTO> scored = candidates.stream()
                .filter(m -> "RECRUITING".equals(m.getStatus()))
                .map(m -> {
                    double distKm = haversineKm(lat, lng,
                            m.getLatitude() != null ? m.getLatitude() : lat,
                            m.getLongitude() != null ? m.getLongitude() : lng);
                    double distScore = Math.max(0, 1.0 - distKm / 50.0);

                    double daysUntil = m.getDate() != null
                            ? (m.getDate().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() - nowMs)
                                    / 86_400_000.0
                            : 30;
                    double urgencyScore = Math.max(0, 1.0 - daysUntil / 30.0);

                    Integer maxParticipants = m.getMaxParticipants();
                    int max = (maxParticipants != null && maxParticipants > 0) ? maxParticipants : 1;
                    Integer currentParticipants = m.getCurrentParticipants();
                    int cur = currentParticipants != null ? currentParticipants : 0;
                    double capacityScore = 1.0 - (double) cur / max;

                    double score = 0.4 * distScore + 0.4 * urgencyScore + 0.2 * capacityScore;
                    return new AbstractMap.SimpleEntry<>(m, score);
                })
                .sorted(Map.Entry.<MeetupDTO, Double>comparingByValue().reversed())
                .limit(size)
                .map(AbstractMap.SimpleEntry::getKey)
                .collect(Collectors.toList());

        return scored.isEmpty() ? getAvailableMeetups(fallbackPage).getContent() : scored;
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        final int R = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    // 주최자별 모임 조회
    public List<MeetupDTO> getMeetupsByOrganizer(Long organizerIdx) {
        List<Meetup> meetups = meetupRepository.findByOrganizerIdxOrderByCreatedAtDesc(organizerIdx);
        return converter.toDTOList(meetups.size() > MAX_LIST_SIZE ? meetups.subList(0, MAX_LIST_SIZE) : meetups);
    }

}
