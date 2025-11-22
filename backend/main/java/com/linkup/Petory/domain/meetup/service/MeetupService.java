package com.linkup.Petory.domain.meetup.service;

import com.linkup.Petory.domain.meetup.converter.MeetupConverter;
import com.linkup.Petory.domain.meetup.converter.MeetupParticipantsConverter;
import com.linkup.Petory.domain.meetup.dto.MeetupDTO;
import com.linkup.Petory.domain.meetup.dto.MeetupParticipantsDTO;
import com.linkup.Petory.domain.meetup.entity.Meetup;
import com.linkup.Petory.domain.meetup.repository.MeetupRepository;
import com.linkup.Petory.domain.meetup.repository.MeetupParticipantsRepository;
import com.linkup.Petory.domain.user.entity.Users;
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

    // 모임 생성
    @Transactional
    public MeetupDTO createMeetup(MeetupDTO meetupDTO) {
        Users organizer = usersRepository.findById(meetupDTO.getOrganizerIdx())
                .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));

        Meetup meetup = Meetup.builder()
                .title(meetupDTO.getTitle())
                .description(meetupDTO.getDescription())
                .location(meetupDTO.getLocation())
                .latitude(meetupDTO.getLatitude())
                .longitude(meetupDTO.getLongitude())
                .date(meetupDTO.getDate())
                .organizer(organizer)
                .maxParticipants(meetupDTO.getMaxParticipants())
                .build();

        Meetup savedMeetup = meetupRepository.save(meetup);
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
        return meetupRepository.findNearbyMeetups(lat, lng, radiusKm, now)
                .stream()
                .map(converter::toDTO)
                .collect(Collectors.toList());
    }

    // 특정 모임의 참가자 목록 조회
    public List<MeetupParticipantsDTO> getMeetupParticipants(Long meetupIdx) {
        return meetupParticipantsRepository.findByMeetupIdxOrderByJoinedAtAsc(meetupIdx)
                .stream()
                .map(participantsConverter::toDTO)
                .collect(Collectors.toList());
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
