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
import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.entity.Users;
import com.linkup.Petory.domain.user.exception.EmailVerificationRequiredException;
import com.linkup.Petory.domain.user.repository.PetRepository;
import com.linkup.Petory.domain.user.repository.UsersRepository;
import com.linkup.Petory.domain.user.service.EmailVerificationService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CareRequestService {

    private final CareRequestRepository careRequestRepository;
    private final UsersRepository usersRepository;
    private final PetRepository petRepository;
    private final CareRequestConverter careRequestConverter;
    private final EmailVerificationService emailVerificationService;

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
        // 이메일 인증 확인
        Users user = usersRepository.findById(dto.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (user.getEmailVerified() == null || !user.getEmailVerified()) {
            throw new EmailVerificationRequiredException(
                    "펫케어 서비스 이용을 위해 이메일 인증이 필요합니다.",
                    com.linkup.Petory.domain.user.entity.EmailVerificationPurpose.PET_CARE);
        }

        CareRequest.CareRequestBuilder builder = CareRequest.builder()
                .title(dto.getTitle())
                .description(dto.getDescription())
                .date(dto.getDate())
                .user(user)
                .status(CareRequestStatus.OPEN);

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
    public CareRequestDTO updateCareRequest(Long idx, CareRequestDTO dto) {
        CareRequest request = careRequestRepository.findById(idx)
                .orElseThrow(() -> new RuntimeException("CareRequest not found"));

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
