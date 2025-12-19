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
                    List.of(organizer.getIdx()));

            // 주최자를 ADMIN 역할로 설정
            conversationService.setParticipantRole(
                    RelatedType.MEETUP,
                    savedMeetup.getIdx(),
                    organizer.getIdx(),
                    ParticipantRole.ADMIN);

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
        long startTime = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();

        log.info("[모임 참가 시작] thread={}, meetupIdx={}, userId={}", threadName, meetupIdx, userId);

        // ✅ Pessimistic Lock으로 모임 조회 (Race Condition 방지)
        // 다른 트랜잭션은 이 Lock이 해제될 때까지 대기
        Meetup meetup = meetupRepository.findByIdWithLock(meetupIdx)
                .orElseThrow(() -> {
                    log.error("[모임 참가 실패] 모임을 찾을 수 없음: meetupIdx={}, userId={}", meetupIdx, userId);
                    return new RuntimeException("모임을 찾을 수 없습니다.");
                });

        log.debug("[Lock 획득] thread={}, meetupIdx={}, 현재인원={}, 최대인원={}",
                threadName, meetupIdx, meetup.getCurrentParticipants(), meetup.getMaxParticipants());

        // 사용자 확인 (userId는 Users의 id 필드, 문자열)
        Users user = usersRepository.findByIdString(userId)
                .orElseThrow(() -> {
                    log.error("[모임 참가 실패] 사용자를 찾을 수 없음: meetupIdx={}, userId={}", meetupIdx, userId);
                    return new RuntimeException("사용자를 찾을 수 없습니다.");
                });

        // 이메일 인증 확인
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            log.warn("[모임 참가 실패] 이메일 인증 필요: meetupIdx={}, userId={}", meetupIdx, userId);
            throw new EmailVerificationRequiredException("모임 참여를 위해 이메일 인증이 필요합니다.");
        }

        Long userIdx = user.getIdx(); // Users의 idx 필드 (Long)

        // 이미 참가했는지 확인
        if (meetupParticipantsRepository.existsByMeetupIdxAndUserIdx(meetupIdx, userIdx)) {
            log.warn("[모임 참가 실패] 이미 참가한 모임: meetupIdx={}, userId={}, userIdx={}", meetupIdx, userId, userIdx);
            throw new RuntimeException("이미 참가한 모임입니다.");
        }

        // 주최자는 자동으로 참가자에 포함되므로, 주최자가 아닌 경우에만 인원 체크
        if (!meetup.getOrganizer().getIdx().equals(userIdx)) {
            // ⚠️ Race Condition 발생 가능 지점 - 인원 체크 전 상태 로깅
            int currentParticipants = meetup.getCurrentParticipants();
            int maxParticipants = meetup.getMaxParticipants();
            int availableSlots = maxParticipants - currentParticipants;

            log.info("[인원 체크] thread={}, meetupIdx={}, userId={}, 현재인원={}, 최대인원={}, 남은자리={}",
                    threadName, meetupIdx, userId, currentParticipants, maxParticipants, availableSlots);

            // 최대 인원 체크
            if (currentParticipants >= maxParticipants) {
                log.warn("[모임 참가 실패] 인원 초과: thread={}, meetupIdx={}, userId={}, 현재인원={}, 최대인원={}",
                        threadName, meetupIdx, userId, currentParticipants, maxParticipants);
                throw new RuntimeException("모임 인원이 가득 찼습니다.");
            }

            // ⚠️ Race Condition 위험 경고 로그
            if (availableSlots <= 1) {
                log.warn("[Race Condition 위험] 남은 자리가 1개 이하: thread={}, meetupIdx={}, 남은자리={}, " +
                        "동시 참가 시도 시 인원 초과 가능성 있음", threadName, meetupIdx, availableSlots);
            }
        }

        // 참가자 추가
        MeetupParticipants participant = MeetupParticipants.builder()
                .meetup(meetup)
                .user(user)
                .joinedAt(LocalDateTime.now())
                .build();

        MeetupParticipants savedParticipant = meetupParticipantsRepository.save(participant);
        log.debug("[참가자 추가 완료] thread={}, meetupIdx={}, userId={}, participantIdx={}",
                threadName, meetupIdx, userId, savedParticipant.getUser().getIdx());

        // 주최자가 아닌 경우에만 currentParticipants 증가
        if (!meetup.getOrganizer().getIdx().equals(userIdx)) {
            // ⚠️ Race Condition 발생 지점 - 증가 전 상태 로깅
            int beforeCount = meetup.getCurrentParticipants();
            meetup.setCurrentParticipants(meetup.getCurrentParticipants() + 1);
            int afterCount = meetup.getCurrentParticipants();

            log.info("[인원 증가] thread={}, meetupIdx={}, userId={}, 증가전={}, 증가후={}, 최대인원={}",
                    threadName, meetupIdx, userId, beforeCount, afterCount, meetup.getMaxParticipants());

            // ⚠️ Race Condition 감지: 증가 후 인원이 최대 인원을 초과하는 경우
            if (afterCount > meetup.getMaxParticipants()) {
                log.error("[Race Condition 발생!] 인원 초과 감지: thread={}, meetupIdx={}, 증가전={}, 증가후={}, 최대인원={}, " +
                        "동시 참가로 인한 인원 초과 발생 가능성",
                        threadName, meetupIdx, beforeCount, afterCount, meetup.getMaxParticipants());
            }

            meetupRepository.save(meetup);
            log.debug("[인원 저장 완료] thread={}, meetupIdx={}, 현재인원={}",
                    threadName, meetupIdx, afterCount);
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 최종 상태 확인 및 로깅
        Meetup finalMeetup = meetupRepository.findById(meetupIdx).orElse(null);
        if (finalMeetup != null) {
            long actualParticipantCount = meetupParticipantsRepository.countByMeetupIdx(meetupIdx);

            log.info("[모임 참가 완료] thread={}, meetupIdx={}, userId={}, userIdx={}, 소요시간={}ms, " +
                    "현재인원={}, 최대인원={}, 실제참가자수={}",
                    threadName, meetupIdx, userId, userIdx, duration,
                    finalMeetup.getCurrentParticipants(), finalMeetup.getMaxParticipants(), actualParticipantCount);

            // 데이터 정합성 검증 로그
            if (finalMeetup.getCurrentParticipants() != actualParticipantCount) {
                log.error("[데이터 불일치 감지] currentParticipants({})와 실제 참가자 수({})가 일치하지 않음: meetupIdx={}",
                        finalMeetup.getCurrentParticipants(), actualParticipantCount, meetupIdx);
            }

            // Race Condition 최종 검증
            if (finalMeetup.getCurrentParticipants() > finalMeetup.getMaxParticipants()) {
                log.error("[Race Condition 최종 확인] 인원 초과: meetupIdx={}, 현재인원={}, 최대인원={}, " +
                        "Race Condition으로 인한 인원 초과 발생",
                        meetupIdx, finalMeetup.getCurrentParticipants(), finalMeetup.getMaxParticipants());
            }
        }

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
