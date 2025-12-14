package com.linkup.Petory.domain.meetup.service;

import com.linkup.Petory.domain.chat.entity.ConversationType;
import com.linkup.Petory.domain.chat.entity.ParticipantRole;
import com.linkup.Petory.domain.chat.entity.RelatedType;
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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    // 모임 생성
    @Transactional
    public MeetupDTO createMeetup(MeetupDTO meetupDTO, String userId) {
        // userId로 사용자 찾기 (id 필드 사용)
        Users organizer = usersRepository.findByIdString(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        // 이메일 인증 확인
        if (organizer.getEmailVerified() == null || !organizer.getEmailVerified()) {
            throw new EmailVerificationRequiredException("모임 생성을 위해 이메일 인증이 필요합니다.");
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

        // 그룹 채팅방 자동 생성 (주최자만 초기 참여)
        try {
            conversationService.createConversation(
                ConversationType.MEETUP,
                RelatedType.MEETUP,
                savedMeetup.getIdx(),
                savedMeetup.getTitle(),
                List.of(organizer.getIdx())
            );
            
            // 주최자를 ADMIN 역할로 설정
            conversationService.setParticipantRole(
                RelatedType.MEETUP,
                savedMeetup.getIdx(),
                organizer.getIdx(),
                ParticipantRole.ADMIN
            );
            
            log.info("모임 채팅방 생성 완료: meetupIdx={}, organizer={}", savedMeetup.getIdx(), userId);
        } catch (Exception e) {
            log.error("모임 채팅방 생성 실패: meetupIdx={}, error={}", savedMeetup.getIdx(), e.getMessage());
            // 채팅방 생성 실패해도 모임 생성은 성공으로 처리
        }

        log.info("모임 생성 완료: meetupIdx={}, organizer={}", savedMeetup.getIdx(), userId);
        return converter.toDTO(savedMeetup);
    }

    // 모임 수정
    @Transactional
    public MeetupDTO updateMeetup(Long meetupIdx, MeetupDTO meetupDTO) {
        Meetup meetup = meetupRepository.findById(meetupIdx)
                .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));

        meetup.setTitle(meetupDTO.getTitle());
        meetup.setDescription(meetupDTO.getDescription());
        meetup.setLocation(meetupDTO.getLocation());
        meetup.setLatitude(meetupDTO.getLatitude());
        meetup.setLongitude(meetupDTO.getLongitude());
        meetup.setDate(meetupDTO.getDate());
        meetup.setMaxParticipants(meetupDTO.getMaxParticipants());

        Meetup savedMeetup = meetupRepository.save(meetup);
        return converter.toDTO(savedMeetup);
    }

    // 모임 삭제
    @Transactional
    public void deleteMeetup(Long meetupIdx) {
        meetupRepository.deleteById(meetupIdx);
    }

    // 모든 모임 조회
    public List<MeetupDTO> getAllMeetups() {
        return meetupRepository.findAll()
                .stream()
                .map(converter::toDTO)
                .collect(Collectors.toList());
    }

    // 특정 모임 조회 (참가자 목록 포함)
    public MeetupDTO getMeetupById(Long meetupIdx) {
        Meetup meetup = meetupRepository.findById(meetupIdx)
                .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));
        return converter.toDTO(meetup);
    }

    // 반경 기반 모임 조회 (마커 표시용)
    public List<MeetupDTO> getNearbyMeetups(Double lat, Double lng, Double radiusKm) {
        LocalDateTime now = LocalDateTime.now();
        log.info("반경 기반 모임 조회 요청: lat={}, lng={}, radius={}km, currentDate={}", lat, lng, radiusKm, now);

        // 모든 모임을 가져와서 Java에서 필터링 (Native query 문제 회피)
        List<Meetup> allMeetups = meetupRepository.findAll();
        log.info("전체 모임 수: {}", allMeetups.size());

        // 거리 계산 함수 (Haversine 공식)
        java.util.function.Function<Meetup, Double> calculateDistance = (meetup) -> {
            if (meetup.getLatitude() == null || meetup.getLongitude() == null) {
                return Double.MAX_VALUE;
            }
            double lat1 = Math.toRadians(lat);
            double lat2 = Math.toRadians(meetup.getLatitude());
            double lon1 = Math.toRadians(lng);
            double lon2 = Math.toRadians(meetup.getLongitude());

            double dLat = lat2 - lat1;
            double dLon = lon2 - lon1;

            double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                    Math.cos(lat1) * Math.cos(lat2) *
                            Math.sin(dLon / 2) * Math.sin(dLon / 2);
            double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
            return 6371 * c; // 지구 반지름 6371km
        };

        // 필터링 및 정렬 (거리 정보를 함께 저장)
        List<java.util.Map.Entry<Meetup, Double>> meetupsWithDistance = allMeetups.stream()
                .filter(meetup -> {
                    // 좌표가 있는지 확인
                    if (meetup.getLatitude() == null || meetup.getLongitude() == null) {
                        log.debug("좌표 없는 모임 제외: idx={}", meetup.getIdx());
                        return false;
                    }

                    // 미래 날짜만 포함
                    boolean isFuture = meetup.getDate() != null && meetup.getDate().isAfter(now);

                    // COMPLETED 상태 제외
                    boolean isNotCompleted = meetup.getStatus() == null ||
                            !meetup.getStatus().equals(com.linkup.Petory.domain.meetup.entity.MeetupStatus.COMPLETED);

                    if (!isFuture) {
                        log.debug("과거 날짜 모임 제외: idx={}, date={}", meetup.getIdx(), meetup.getDate());
                    }
                    if (!isNotCompleted) {
                        log.debug("COMPLETED 상태 모임 제외: idx={}, status={}", meetup.getIdx(), meetup.getStatus());
                    }

                    return isFuture && isNotCompleted;
                })
                .map(meetup -> {
                    double distance = calculateDistance.apply(meetup);
                    return new java.util.AbstractMap.SimpleEntry<>(meetup, distance);
                })
                .filter(entry -> {
                    boolean withinRadius = entry.getValue() <= radiusKm;
                    if (!withinRadius) {
                        log.debug("거리 초과 모임 제외: idx={}, distance={}km", entry.getKey().getIdx(), entry.getValue());
                    }
                    return withinRadius;
                })
                .sorted((e1, e2) -> {
                    // 거리순 정렬, 같으면 날짜순
                    int distanceCompare = Double.compare(e1.getValue(), e2.getValue());
                    if (distanceCompare != 0) {
                        return distanceCompare;
                    }
                    return e1.getKey().getDate().compareTo(e2.getKey().getDate());
                })
                .collect(Collectors.toList());

        List<MeetupDTO> filteredMeetups = meetupsWithDistance.stream()
                .map(entry -> converter.toDTO(entry.getKey()))
                .collect(Collectors.toList());

        log.info("최종 필터링 후 모임 수: {}", filteredMeetups.size());
        for (java.util.Map.Entry<Meetup, Double> entry : meetupsWithDistance) {
            Meetup meetup = entry.getKey();
            double distance = entry.getValue();
            log.info("모임: idx={}, title={}, date={}, lat={}, lng={}, status={}, distance={}km",
                    meetup.getIdx(), meetup.getTitle(), meetup.getDate(),
                    meetup.getLatitude(), meetup.getLongitude(), meetup.getStatus(), distance);
        }

        return filteredMeetups;
    }

    // 특정 모임의 참가자 목록 조회
    public List<MeetupParticipantsDTO> getMeetupParticipants(Long meetupIdx) {
        return meetupParticipantsRepository.findByMeetupIdxOrderByJoinedAtAsc(meetupIdx)
                .stream()
                .map(participantsConverter::toDTO)
                .collect(Collectors.toList());
    }

    // 모임 참가
    @Transactional
    public MeetupParticipantsDTO joinMeetup(Long meetupIdx, String userId) {
        // 모임 존재 확인
        Meetup meetup = meetupRepository.findById(meetupIdx)
                .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));

        // 사용자 확인 (userId는 Users의 id 필드, 문자열)
        Users user = usersRepository.findByIdString(userId)
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        // 이메일 인증 확인
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            throw new EmailVerificationRequiredException("모임 참여를 위해 이메일 인증이 필요합니다.");
        }

        Long userIdx = user.getIdx(); // Users의 idx 필드 (Long)

        // 이미 참가했는지 확인
        if (meetupParticipantsRepository.existsByMeetupIdxAndUserIdx(meetupIdx, userIdx)) {
            throw new RuntimeException("이미 참가한 모임입니다.");
        }

        // 주최자는 자동으로 참가자에 포함되므로, 주최자가 아닌 경우에만 인원 체크
        if (!meetup.getOrganizer().getIdx().equals(userIdx)) {
            // 최대 인원 체크
            if (meetup.getCurrentParticipants() >= meetup.getMaxParticipants()) {
                throw new RuntimeException("모임 인원이 가득 찼습니다.");
            }
        }

        // 참가자 추가
        MeetupParticipants participant = MeetupParticipants.builder()
                .meetup(meetup)
                .user(user)
                .joinedAt(LocalDateTime.now())
                .build();

        MeetupParticipants savedParticipant = meetupParticipantsRepository.save(participant);

        // 주최자가 아닌 경우에만 currentParticipants 증가
        if (!meetup.getOrganizer().getIdx().equals(userIdx)) {
            meetup.setCurrentParticipants(meetup.getCurrentParticipants() + 1);
            meetupRepository.save(meetup);
        }

        log.info("모임 참가 완료: meetupIdx={}, userId={}, userIdx={}", meetupIdx, userId, userIdx);
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
    public List<MeetupDTO> getMeetupsByLocation(Double minLat, Double maxLat, Double minLng, Double maxLng) {
        return meetupRepository.findByLocationRange(minLat, maxLat, minLng, maxLng)
                .stream()
                .map(converter::toDTO)
                .collect(Collectors.toList());
    }

    // 키워드로 모임 검색
    public List<MeetupDTO> searchMeetupsByKeyword(String keyword) {
        return meetupRepository.findByKeyword(keyword)
                .stream()
                .map(converter::toDTO)
                .collect(Collectors.toList());
    }

    // 참여 가능한 모임 조회
    public List<MeetupDTO> getAvailableMeetups() {
        return meetupRepository.findAvailableMeetups(LocalDateTime.now())
                .stream()
                .map(converter::toDTO)
                .collect(Collectors.toList());
    }

    // 주최자별 모임 조회
    public List<MeetupDTO> getMeetupsByOrganizer(Long organizerIdx) {
        return meetupRepository.findByOrganizerIdxOrderByCreatedAtDesc(organizerIdx)
                .stream()
                .map(converter::toDTO)
                .collect(Collectors.toList());
    }
}
