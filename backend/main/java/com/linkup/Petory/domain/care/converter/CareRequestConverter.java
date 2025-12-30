package com.linkup.Petory.domain.care.converter;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.linkup.Petory.domain.care.dto.CareRequestDTO;
import com.linkup.Petory.domain.care.entity.CareRequest;
import com.linkup.Petory.domain.user.converter.PetConverter;
import com.linkup.Petory.domain.user.dto.PetDTO;
import com.linkup.Petory.domain.user.entity.Pet;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CareRequestConverter {

    private final PetConverter petConverter;
    private final CareApplicationConverter careApplicationConverter;

    // Entity → DTO
    public CareRequestDTO toDTO(CareRequest request) {
        return toDTO(request, null);
    }

    // Entity → DTO (PetDTO를 파라미터로 받는 오버로드)
    // [2단계 최적화] File N+1 문제 해결: 미리 변환된 PetDTO 사용
    public CareRequestDTO toDTO(CareRequest request, PetDTO petDTO) {
        CareRequestDTO.CareRequestDTOBuilder builder = CareRequestDTO.builder()
                .idx(request.getIdx())
                .title(request.getTitle())
                .description(request.getDescription())
                .date(request.getDate())
                .status(request.getStatus().name())
                .createdAt(request.getCreatedAt())
                .deleted(request.getIsDeleted())
                .deletedAt(request.getDeletedAt())
                .userId(request.getUser().getIdx())
                .username(request.getUser().getUsername())
                .userLocation(request.getUser().getLocation())
                .applicationCount(request.getApplications() != null ? request.getApplications().size() : 0);

        // 펫 정보 추가
        if (request.getPet() != null) {
            builder.petIdx(request.getPet().getIdx());
            // 미리 변환된 PetDTO가 있으면 사용, 없으면 개별 변환
            if (petDTO != null) {
                builder.pet(petDTO);
            } else {
                builder.pet(petConverter.toDTO(request.getPet()));
            }
        }

        // 지원 정보 추가
        if (request.getApplications() != null && !request.getApplications().isEmpty()) {
            builder.applications(request.getApplications().stream()
                    .map(careApplicationConverter::toDTO)
                    .collect(Collectors.toList()));
        }

        return builder.build();
    }

    // DTO 리스트 변환
    // [2단계 최적화] File N+1 문제 해결: Pet 배치 변환 사용
    public List<CareRequestDTO> toDTOList(List<CareRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }
        
        // 모든 Pet 수집
        List<Pet> pets = requests.stream()
                .map(CareRequest::getPet)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        
        // Pet 배치 변환 (File N+1 방지)
        Map<Long, PetDTO> petDTOMap = Map.of();
        if (!pets.isEmpty()) {
            List<PetDTO> petDTOs = petConverter.toDTOList(pets);
            petDTOMap = petDTOs.stream()
                    .collect(Collectors.toMap(PetDTO::getIdx, dto -> dto, (existing, replacement) -> existing));
        }
        
        // 각 CareRequest를 변환하면서 미리 변환된 Pet DTO 사용
        final Map<Long, PetDTO> finalPetDTOMap = petDTOMap;
        return requests.stream()
                .map(request -> {
                    // 미리 변환된 Pet DTO 사용 (개별 조회 방지)
                    PetDTO petDTO = null;
                    if (request.getPet() != null && finalPetDTOMap.containsKey(request.getPet().getIdx())) {
                        petDTO = finalPetDTOMap.get(request.getPet().getIdx());
                    }
                    return toDTO(request, petDTO);
                })
                .collect(Collectors.toList());
    }
}