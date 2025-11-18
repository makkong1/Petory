package com.linkup.Petory.domain.care.service;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.linkup.Petory.domain.care.converter.CareRequestConverter;
import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.care.entity.CareRequestStatus;
import com.linkup.Petory.domain.care.repository.CareRequestRepository;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.repository.UsersRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CareRequestService {

    private final CareRequestRepository careRequestRepository;
    private final UsersRepository usersRepository;
    private final CareRequestConverter careRequestConverter;

    // 전체 케어 요청 조회 (필터링 포함)
    @Transactional(readOnly = true)
    public List<CareRequestDTO> getAllCareRequests(String status, String location) {
        List<CareRequest> requests = careRequestRepository.findAll().stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsDeleted()))
                .collect(Collectors.toList());

        // 상태 필터링
        if (status != null && !status.equals("ALL")) {
            CareRequestStatus statusEnum = CareRequestStatus.valueOf(status);
            requests = requests.stream()
                    .filter(r -> r.getStatus() == statusEnum)
                    .collect(Collectors.toList());
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
        CareRequest request = careRequestRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("CareRequest not found"));
        if (Boolean.TRUE.equals(request.getIsDeleted())) {
            throw new RuntimeException("CareRequest not found");
        }
        return careRequestConverter.toDTO(request);
    }

    // 케어 요청 생성
    @Transactional
    public CareRequestDTO createCareRequest(CareRequestDTO dto) {
        // 요청자 조회
        Users user = usersRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

        CareRequest request = CareRequest.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .date(dto.getDate())
                .user(user)
                .status(CareRequestStatus.OPEN)
                .build();

        CareRequest saved = careRequestRepository.save(request);
        return careRequestConverter.toDTO(saved);
    }

    // 케어 요청 수정
    @Transactional
    public CareRequestDTO updateCareRequest(Long idx, CareRequestDTO dto) {
        CareRequest request = careRequestRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("CareRequest not found"));

        if (dto.getTitle() != null)
            request.setTitle(dto.getTitle());
        if (dto.getDescription() != null)
            request.setDescription(dto.getDescription());
        if (dto.getDate() != null)
            request.setDate(dto.getDate());

        CareRequest updated = careRequestRepository.save(request);
        return careRequestConverter.toDTO(updated);
    }

    // 케어 요청 삭제
    @Transactional
    public void deleteCareRequest(Long idx) {
        CareRequest request = careRequestRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("CareRequest not found"));
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
    public CareRequestDTO updateStatus(Long idx, String status) {
        CareRequest request = careRequestRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("CareRequest not found"));

        request.setStatus(CareRequestStatus.valueOf(status));
        CareRequest updated = careRequestRepository.save(request);
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
