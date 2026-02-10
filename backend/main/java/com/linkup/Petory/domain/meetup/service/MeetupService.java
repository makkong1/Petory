package com.linkup.Petory.domain.meetup.service;

import com.linkup.Petory.domain.chat.service.ConversationService;
import com.linkup.Petory.domain.meetup.converter.MeetupConverter;
import com.linkup.Petory.domain.meetup.converter.MeetupParticipantsConverter;
import com.linkup.Petory.domain.meetup.dto.MeetupDTO;
import com.linkup.Petory.domain.meetup.dto.MeetupParticipantsDTO;
import com.linkup.Petory.domain.meetup.entity.Meetup;
import com.linkup.Petory.domain.meetup.entity.MeetupParticipants;
import com.linkup.Petory.domain.meetup.repository.MeetupRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupParticipantsRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import com.linkup.Petory.domain.meetup.annotation.Timed;
import com.linkup.Petory.domain.meetup.event.MeetupCreatedEvent;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetupService {

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
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        // 이메일 인증 확인
        if (organizer.getEmailVerified() == null || !organizer.getEmailVerified()) {
            throw new EmailVerificationRequiredException(
                    "모임 생성을 위해 이메일 인증이 필요합니다.",
                    com.linkup.Petory.domain.user.entity.EmailVerificationPurpose.MEETUP);
        }

        // 날짜 검증
        if (meetupDTO.getDate() != null && meetupDTO.getDate().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("모임 일시는 현재 시간 이후여야 합니다.");
        }

        Meetup meetup = Meetup.builder()
                .title(meetupDTO.getTitle())
                .description(meetupDTO.getDescription())
                .location(meetupDTO.getLocation())
                .latitude(meetupDTO.getLatitude())
                .longitude(meetupDTO.getLongitude())
                .date(meetupDTO.getDate())
                .organizer(organizer)
                .maxParticipants(meetupDTO.getMaxParticipants() != null ? meetupDTO.getMaxParticipants() : 10)
                .currentParticipants(0)
                .status(com.linkup.Petory.domain.meetup.entity.MeetupStatus.RECRUITING)
                .build();

        Meetup savedMeetup = meetupRepository.save(meetup);

        // 주최자를 자동으로 참가자에 추가
        MeetupParticipants organizerParticipant = MeetupParticipants.builder()
                .meetup(savedMeetup)
                .user(organizer)
                .joinedAt(LocalDateTime.now())
                .build();
        meetupParticipantsRepository.save(organizerParticipant);

        // currentParticipants를 1로 설정 (주최자 포함)
        savedMeetup.setCurrentParticipants(1);
        meetupRepository.save(savedMeetup);

        // 모임 생성 완료 이벤트 발행 (트랜잭션 커밋 후 비동기로 채팅방 생성 처리)
        // 핵심 도메인(모임)과 파생 도메인(채팅방) 분리: 채팅방 생성 실패가 모임 생성까지 롤백하지 않음
        // TransactionSynchronization을 사용하여 트랜잭션 커밋 후 이벤트 발행 보장
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
    @Transactional
    public MeetupDTO updateMeetup(Long meetupIdx, MeetupDTO meetupDTO) {
        Meetup meetup = meetupRepository.findById(meetupIdx)
                .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));

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
            meetup.setDate(meetupDTO.getDate());
        }
        if (meetupDTO.getMaxParticipants() != null) {
            meetup.setMaxParticipants(meetupDTO.getMaxParticipants());
        }

        Meetup savedMeetup = meetupRepository.save(meetup);
        return converter.toDTO(savedMeetup);
    }

    // 모임 삭제 (소프트 삭제)
    @Transactional
    public void deleteMeetup(Long meetupIdx) {
        Meetup meetup = meetupRepository.findById(meetupIdx)
                .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));

        meetup.setIsDeleted(true);
        meetup.setDeletedAt(LocalDateTime.now());
        meetupRepository.save(meetup);

        log.info("모임 소프트 삭제 완료: meetupIdx={}", meetupIdx);
    }

    // 모든 모임 조회 (소프트 삭제 제외)
    @Timed("getAllMeetups")
    public List<MeetupDTO> getAllMeetups() {
        List<Meetup> meetups = meetupRepository.findAllNotDeleted(); // 원래는 여기에서 n+1문제가 발생했지만 join fetch로 해결
        return convertToDTOs(meetups);
    }

    // 특정 모임 조회 (참가자 목록 포함) - JOIN FETCH로 N+1 문제 해결
    public MeetupDTO getMeetupById(Long meetupIdx) {
        Meetup meetup = meetupRepository.findByIdWithDetails(meetupIdx)
                .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));
        return converter.toDTO(meetup);
    }

    // 반경 기반 모임 조회 (마커 표시용)
    // ✅ 리팩토링: 인메모리 필터링 제거 → DB 쿼리로 최적화
    @Timed("getNearbyMeetups")
    public List<MeetupDTO> getNearbyMeetups(Double lat, Double lng, Double radiusKm) {
        LocalDateTime now = LocalDateTime.now();
        log.info("반경 기반 모임 조회 요청: lat={}, lng={}, radius={}km, currentDate={}", lat, lng, radiusKm, now);

        List<Meetup> nearbyMeetups = meetupRepository.findNearbyMeetups(lat, lng, radiusKm, now);
        log.info("DB 쿼리 결과 모임 수: {}", nearbyMeetups.size());

        List<MeetupDTO> result = convertToDTOs(nearbyMeetups);
        log.info("최종 결과 모임 수: {}", result.size());

        // 반경 내 모임들의 정보 로그 (최대 10개)
        for (int i = 0; i < Math.min(10, nearbyMeetups.size()); i++) {
            Meetup meetup = nearbyMeetups.get(i);
            log.info("✅ 반경 내 모임: idx={}, title={}, date={}, lat={}, lng={}",
                    meetup.getIdx(), meetup.getTitle(), meetup.getDate(),
                    meetup.getLatitude(), meetup.getLongitude());
        }

        return result;
    }

    // 특정 모임의 참가자 목록 조회
    public List<MeetupParticipantsDTO> getMeetupParticipants(Long meetupIdx) {
        return convertToParticipantDTOs(
                meetupParticipantsRepository.findByMeetupIdxOrderByJoinedAtAsc(meetupIdx)
        );
    }

    // 모임 참가 (원자적 UPDATE 쿼리 방식 - 권장)
    @Transactional
    public MeetupParticipantsDTO joinMeetup(Long meetupIdx, String userId) {
        // 모임 조회 (Lock 없이)
        Meetup meetup = meetupRepository.findById(meetupIdx)
                .orElseThrow(() -> {
                    log.error("모임을 찾을 수 없습니다. meetupIdx={}, userId={}", meetupIdx, userId);
                    return new RuntimeException("모임을 찾을 수 없습니다.");
                });

        // 사용자 확인
        Users user = usersRepository.findByIdString(userId)
                .orElseThrow(() -> {
                    log.error("사용자를 찾을 수 없습니다. meetupIdx={}, userId={}", meetupIdx, userId);
                    return new RuntimeException("사용자를 찾을 수 없습니다.");
                });

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
            throw new RuntimeException("이미 참가한 모임입니다.");
        }

        // 주최자가 아닌 경우에만 인원 증가 (원자적 UPDATE 쿼리)
        if (!meetup.getOrganizer().getIdx().equals(userIdx)) {
            // 원자적 UPDATE 쿼리로 조건부 증가 (DB 레벨에서 체크 + 증가 동시 처리)
            int updated = meetupRepository.incrementParticipantsIfAvailable(meetupIdx);
            if (updated == 0) {
                log.warn("모임 인원이 가득 찼습니다. meetupIdx={}, userId={}, 현재인원={}, 최대인원={}",
                        meetupIdx, userId, meetup.getCurrentParticipants(), meetup.getMaxParticipants());
                throw new RuntimeException("모임 인원이 가득 찼습니다.");
            }
            // 영속성 컨텍스트 새로고침 (중복 DB 쿼리 제거)
            entityManager.refresh(meetup);
        }

        // 참가자 추가
        MeetupParticipants participant = MeetupParticipants.builder()
                .meetup(meetup)
                .user(user)
                .joinedAt(LocalDateTime.now())
                .build();

        MeetupParticipants savedParticipant = meetupParticipantsRepository.save(participant);

        log.info("모임 참가 완료. meetupIdx={}, userId={}, 현재인원={}, 최대인원={}",
                meetupIdx, userId, meetup.getCurrentParticipants(), meetup.getMaxParticipants());

        return participantsConverter.toDTO(savedParticipant);
    }

    // 모임 참가 취소
    @Transactional
    public void cancelMeetupParticipation(Long meetupIdx, String userId) {
        // 모임 존재 확인
        Meetup meetup = meetupRepository.findById(meetupIdx)
                .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));

        // 사용자 확인 (userId는 Users의 id 필드, 문자열)
        Users user = usersRepository.findByIdString(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        Long userIdx = user.getIdx(); // Users의 idx 필드 (Long)

        // 주최자는 참가 취소 불가
        if (meetup.getOrganizer().getIdx().equals(userIdx)) {
            throw new RuntimeException("주최자는 참가 취소할 수 없습니다.");
        }

        // 참가자 확인
        MeetupParticipants participant = meetupParticipantsRepository
                .findByMeetupIdxAndUserIdx(meetupIdx, userIdx)
                .orElseThrow(() -> new RuntimeException("참가 정보를 찾을 수 없습니다."));

        // 참가자 삭제
        meetupParticipantsRepository.delete(participant);

        // currentParticipants 감소
        meetup.setCurrentParticipants(Math.max(0, meetup.getCurrentParticipants() - 1));
        meetupRepository.save(meetup);

        // 채팅방에서도 자동으로 나가기
        try {
            conversationService.leaveMeetupChat(meetupIdx, userIdx);
            log.info("채팅방에서 나가기 완료: meetupIdx={}, userIdx={}", meetupIdx, userIdx);
        } catch (Exception e) {
            log.error("채팅방 나가기 실패: meetupIdx={}, userIdx={}, error={}", meetupIdx, userIdx, e.getMessage());
            // 채팅방 나가기 실패해도 모임 참여 취소는 성공으로 처리
        }

        log.info("모임 참가 취소 완료: meetupIdx={}, userId={}, userIdx={}", meetupIdx, userId, userIdx);
    }

    // 사용자가 특정 모임에 참가했는지 확인
    public boolean isUserParticipating(Long meetupIdx, String userId) {
        // 사용자 확인 (userId는 Users의 id 필드, 문자열)
        Users user = usersRepository.findByIdString(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        Long userIdx = user.getIdx(); // Users의 idx 필드 (Long)
        return meetupParticipantsRepository.existsByMeetupIdxAndUserIdx(meetupIdx, userIdx);
    }

    // 지역별 모임 조회
    @Timed("getMeetupsByLocation")
    public List<MeetupDTO> getMeetupsByLocation(Double minLat, Double maxLat, Double minLng, Double maxLng) {
        List<Meetup> meetups = meetupRepository.findByLocationRange(minLat, maxLat, minLng, maxLng);
        return convertToDTOs(meetups);
    }

    // 키워드로 모임 검색
    @Timed("searchMeetupsByKeyword")
    public List<MeetupDTO> searchMeetupsByKeyword(String keyword) {
        List<Meetup> meetups = meetupRepository.findByKeyword(keyword);
        return convertToDTOs(meetups);
    }

    // 참여 가능한 모임 조회
    @Timed("getAvailableMeetups")
    public List<MeetupDTO> getAvailableMeetups() {
        List<Meetup> meetups = meetupRepository.findAvailableMeetups(LocalDateTime.now());
        return convertToDTOs(meetups);
    }

    // 주최자별 모임 조회
    public List<MeetupDTO> getMeetupsByOrganizer(Long organizerIdx) {
        return convertToDTOs(
                meetupRepository.findByOrganizerIdxOrderByCreatedAtDesc(organizerIdx)
        );
    }

    // 공통 메서드: Meetup 엔티티 리스트를 DTO 리스트로 변환
    private List<MeetupDTO> convertToDTOs(List<Meetup> meetups) {
        return meetups.stream()
                .map(converter::toDTO)
                .collect(Collectors.toList());
    }

    // 공통 메서드: MeetupParticipants 엔티티 리스트를 DTO 리스트로 변환
    private List<MeetupParticipantsDTO> convertToParticipantDTOs(List<MeetupParticipants> participants) {
        return participants.stream()
                .map(participantsConverter::toDTO)
                .collect(Collectors.toList());
    }
}
