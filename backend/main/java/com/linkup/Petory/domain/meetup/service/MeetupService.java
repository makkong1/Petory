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

import com.linkup.Petory.domain.meetup.event.MeetupCreatedEvent;
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
    public List<MeetupDTO> getAllMeetups() {
        long startTime = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        long dbStartTime = System.currentTimeMillis();
        List<Meetup> meetups = meetupRepository.findAllNotDeleted(); // 원래는 여기에서 n+1문제가 발생했지만 join fetch로 해결
        long dbTime = System.currentTimeMillis() - dbStartTime;

        long processingStartTime = System.currentTimeMillis();
        List<MeetupDTO> result = meetups.stream()
                .map(converter::toDTO)
                .collect(Collectors.toList());
        long processingTime = System.currentTimeMillis() - processingStartTime;

        long totalTime = System.currentTimeMillis() - startTime;
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;

        log.info("[성능 측정] getAllMeetups - 전체시간: {}ms, DB쿼리: {}ms, 처리시간: {}ms, 메모리사용: {}MB, 조회건수: {}",
                totalTime, dbTime, processingTime, memoryUsed / (1024 * 1024), result.size());

        return result;
    }

    // 특정 모임 조회 (참가자 목록 포함) - JOIN FETCH로 N+1 문제 해결
    public MeetupDTO getMeetupById(Long meetupIdx) {
        Meetup meetup = meetupRepository.findByIdWithDetails(meetupIdx)
                .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));
        return converter.toDTO(meetup);
    }

    // 반경 기반 모임 조회 (마커 표시용)
    public List<MeetupDTO> getNearbyMeetups(Double lat, Double lng, Double radiusKm) {
        long startTime = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        LocalDateTime now = LocalDateTime.now();
        log.info("반경 기반 모임 조회 요청: lat={}, lng={}, radius={}km, currentDate={}", lat, lng, radiusKm, now);

        // 모든 모임을 가져와서 Java에서 필터링 (Native query 문제 회피, 소프트 삭제 제외)
        long dbStartTime = System.currentTimeMillis();
        List<Meetup> allMeetups = meetupRepository.findAllNotDeleted();
        long dbTime = System.currentTimeMillis() - dbStartTime;
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
        long filteringStartTime = System.currentTimeMillis();

        // 필터링 통계 (람다 내부에서 수정 가능하도록 배열 사용)
        int totalCount = allMeetups.size();
        int[] noCoordinateCount = { 0 };
        int[] pastDateCount = { 0 };
        int[] completedCount = { 0 };
        int[] outOfRadiusCount = { 0 };
        int[] validAfterDateFilter = { 0 }; // 날짜 필터 통과한 모임 수

        List<java.util.Map.Entry<Meetup, Double>> meetupsWithDistance = allMeetups.stream()
                .filter(meetup -> {
                    // 좌표가 있는지 확인
                    if (meetup.getLatitude() == null || meetup.getLongitude() == null) {
                        noCoordinateCount[0]++;
                        return false;
                    }

                    // 미래 날짜만 포함
                    boolean isFuture = meetup.getDate() != null && meetup.getDate().isAfter(now);
                    if (!isFuture) {
                        pastDateCount[0]++;
                        return false; // 과거 날짜면 바로 제외
                    }

                    // COMPLETED 상태 제외
                    boolean isNotCompleted = meetup.getStatus() == null ||
                            !meetup.getStatus().equals(com.linkup.Petory.domain.meetup.entity.MeetupStatus.COMPLETED);
                    if (!isNotCompleted) {
                        completedCount[0]++;
                        return false; // COMPLETED면 바로 제외
                    }

                    validAfterDateFilter[0]++; // 날짜와 상태 필터 통과
                    return true;
                })
                .map(meetup -> {
                    double distance = calculateDistance.apply(meetup);
                    // 디버깅: 거리 계산 결과 로그 (최대 5개만)
                    if (validAfterDateFilter[0] <= 5 || outOfRadiusCount[0] < 5) {
                        log.debug("거리 계산: 모임 idx={}, title={}, 사용자위치=({}, {}), 모임위치=({}, {}), 거리={}km, 반경={}km",
                                meetup.getIdx(), meetup.getTitle(),
                                lat, lng, meetup.getLatitude(), meetup.getLongitude(),
                                String.format("%.2f", distance), radiusKm);
                    }
                    return new java.util.AbstractMap.SimpleEntry<>(meetup, distance);
                })
                .filter(entry -> {
                    boolean withinRadius = entry.getValue() <= radiusKm;
                    if (!withinRadius) {
                        outOfRadiusCount[0]++;
                    } else {
                        log.debug("✅ 반경 내 모임 발견: idx={}, title={}, 거리={}km",
                                entry.getKey().getIdx(), entry.getKey().getTitle(),
                                String.format("%.2f", entry.getValue()));
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
        long filteringTime = System.currentTimeMillis() - filteringStartTime;

        long conversionStartTime = System.currentTimeMillis();
        List<MeetupDTO> filteredMeetups = meetupsWithDistance.stream()
                .map(entry -> converter.toDTO(entry.getKey()))
                .collect(Collectors.toList());
        long conversionTime = System.currentTimeMillis() - conversionStartTime;

        long totalTime = System.currentTimeMillis() - startTime;
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;

        log.info("최종 필터링 후 모임 수: {}", filteredMeetups.size());
        log.info("[필터링 통계] 전체: {}, 좌표없음: {}, 과거날짜: {}, COMPLETED: {}, 날짜/상태통과: {}, 반경초과: {}, 최종결과: {}",
                totalCount, noCoordinateCount[0], pastDateCount[0], completedCount[0], validAfterDateFilter[0],
                outOfRadiusCount[0],
                filteredMeetups.size());
        log.info(
                "[성능 측정] getNearbyMeetups - 전체시간: {}ms, DB쿼리: {}ms, 필터링/정렬: {}ms, DTO변환: {}ms, 메모리사용: {}MB, 전체건수: {}, 결과건수: {}",
                totalTime, dbTime, filteringTime, conversionTime, memoryUsed / (1024 * 1024), allMeetups.size(),
                filteredMeetups.size());

        // 반경 통과한 모임들의 거리 정보 로그 (디버깅용)
        if (filteredMeetups.isEmpty() && validAfterDateFilter[0] > 0) {
            log.warn("⚠️ 반경 내 모임이 없습니다. 요청 위치: lat={}, lng={}, 반경={}km", lat, lng, radiusKm);
            log.warn("⚠️ 날짜/상태 통과한 모임 {}개 중 반경 초과: {}개", validAfterDateFilter[0], outOfRadiusCount[0]);

            // 반경 초과한 모임들의 거리 정보 출력 (최대 5개)
            allMeetups.stream()
                    .filter(meetup -> meetup.getLatitude() != null && meetup.getLongitude() != null)
                    .filter(meetup -> meetup.getDate() != null && meetup.getDate().isAfter(now))
                    .filter(meetup -> meetup.getStatus() == null ||
                            !meetup.getStatus().equals(com.linkup.Petory.domain.meetup.entity.MeetupStatus.COMPLETED))
                    .limit(5)
                    .forEach(meetup -> {
                        double distance = calculateDistance.apply(meetup);
                        log.warn("  - 모임 idx={}, title={}, lat={}, lng={}, 거리={}km (반경={}km 초과)",
                                meetup.getIdx(), meetup.getTitle(),
                                meetup.getLatitude(), meetup.getLongitude(),
                                String.format("%.2f", distance), radiusKm);
                    });
        }

        // 반경 내 모임들의 거리 정보 로그 (최대 10개)
        for (java.util.Map.Entry<Meetup, Double> entry : meetupsWithDistance.stream().limit(10)
                .collect(Collectors.toList())) {
            Meetup meetup = entry.getKey();
            double distance = entry.getValue();
            log.info("✅ 반경 내 모임: idx={}, title={}, date={}, lat={}, lng={}, 거리={}km",
                    meetup.getIdx(), meetup.getTitle(), meetup.getDate(),
                    meetup.getLatitude(), meetup.getLongitude(), String.format("%.2f", distance));
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
            // 업데이트된 모임 정보 다시 조회
            meetup = meetupRepository.findById(meetupIdx)
                    .orElseThrow(() -> new RuntimeException("모임을 찾을 수 없습니다."));
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
    public List<MeetupDTO> getMeetupsByLocation(Double minLat, Double maxLat, Double minLng, Double maxLng) {
        long startTime = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        long dbStartTime = System.currentTimeMillis();
        List<Meetup> meetups = meetupRepository.findByLocationRange(minLat, maxLat, minLng, maxLng);
        long dbTime = System.currentTimeMillis() - dbStartTime;

        long processingStartTime = System.currentTimeMillis();
        List<MeetupDTO> result = meetups.stream()
                .map(converter::toDTO)
                .collect(Collectors.toList());
        long processingTime = System.currentTimeMillis() - processingStartTime;

        long totalTime = System.currentTimeMillis() - startTime;
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;

        log.info("[성능 측정] getMeetupsByLocation - 전체시간: {}ms, DB쿼리: {}ms, 처리시간: {}ms, 메모리사용: {}MB, 조회건수: {}",
                totalTime, dbTime, processingTime, memoryUsed / (1024 * 1024), result.size());

        return result;
    }

    // 키워드로 모임 검색
    public List<MeetupDTO> searchMeetupsByKeyword(String keyword) {
        long startTime = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        long dbStartTime = System.currentTimeMillis();
        List<Meetup> meetups = meetupRepository.findByKeyword(keyword);
        long dbTime = System.currentTimeMillis() - dbStartTime;

        long processingStartTime = System.currentTimeMillis();
        List<MeetupDTO> result = meetups.stream()
                .map(converter::toDTO)
                .collect(Collectors.toList());
        long processingTime = System.currentTimeMillis() - processingStartTime;

        long totalTime = System.currentTimeMillis() - startTime;
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;

        log.info("[성능 측정] searchMeetupsByKeyword - 전체시간: {}ms, DB쿼리: {}ms, 처리시간: {}ms, 메모리사용: {}MB, 조회건수: {}, 키워드: {}",
                totalTime, dbTime, processingTime, memoryUsed / (1024 * 1024), result.size(), keyword);

        return result;
    }

    // 참여 가능한 모임 조회
    public List<MeetupDTO> getAvailableMeetups() {
        long startTime = System.currentTimeMillis();
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        long dbStartTime = System.currentTimeMillis();
        List<Meetup> meetups = meetupRepository.findAvailableMeetups(LocalDateTime.now());
        long dbTime = System.currentTimeMillis() - dbStartTime;

        long processingStartTime = System.currentTimeMillis();
        List<MeetupDTO> result = meetups.stream()
                .map(converter::toDTO)
                .collect(Collectors.toList());
        long processingTime = System.currentTimeMillis() - processingStartTime;

        long totalTime = System.currentTimeMillis() - startTime;
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;

        log.info("[성능 측정] getAvailableMeetups - 전체시간: {}ms, DB쿼리: {}ms, 처리시간: {}ms, 메모리사용: {}MB, 조회건수: {}",
                totalTime, dbTime, processingTime, memoryUsed / (1024 * 1024), result.size());

        return result;
    }

    // 주최자별 모임 조회
    public List<MeetupDTO> getMeetupsByOrganizer(Long organizerIdx) {
        return meetupRepository.findByOrganizerIdxOrderByCreatedAtDesc(organizerIdx)
                .stream()
                .map(converter::toDTO)
                .collect(Collectors.toList());
    }
}
