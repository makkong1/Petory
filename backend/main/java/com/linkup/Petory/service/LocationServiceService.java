package com.linkup.Petory.service;

import com.linkup.Petory.converter.LocationServiceReviewConverter;
import com.linkup.Petory.dto.LocationServiceDTO;
import com.linkup.Petory.dto.LocationServiceReviewDTO;
import com.linkup.Petory.entity.LocationService;
import com.linkup.Petory.repository.LocationServiceRepository;
import com.linkup.Petory.service.KakaoMapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LocationServiceService {

    private final LocationServiceRepository serviceRepository;
    private final LocationServiceReviewConverter reviewConverter;
    private final KakaoMapService kakaoMapService;

    // 모든 서비스 조회
    public List<LocationServiceDTO> getAllServices() {
        return serviceRepository.findAll()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // 특정 서비스 조회
    public LocationServiceDTO getServiceById(Long serviceIdx) {
        LocationService service = serviceRepository.findById(serviceIdx)
                .orElseThrow(() -> new RuntimeException("서비스를 찾을 수 없습니다."));
        return convertToDTO(service);
    }

    // 카테고리별 서비스 조회
    public List<LocationServiceDTO> getServicesByCategory(String category) {
        return serviceRepository.findByCategoryOrderByRatingDesc(category)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // 지역별 서비스 조회
    public List<LocationServiceDTO> getServicesByLocation(Double minLat, Double maxLat, Double minLng, Double maxLng) {
        return serviceRepository.findByLocationRange(minLat, maxLat, minLng, maxLng)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // 키워드로 서비스 검색
    public List<LocationServiceDTO> searchServicesByKeyword(String keyword) {
        return serviceRepository.findByNameContaining(keyword)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // 평점순 서비스 조회
    public List<LocationServiceDTO> getServicesByRating() {
        return serviceRepository.findByOrderByRatingDesc()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // 특정 평점 이상의 서비스 조회
    public List<LocationServiceDTO> getServicesByMinRating(Double minRating) {
        return serviceRepository.findByRatingGreaterThanEqualOrderByRatingDesc(minRating)
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // 서비스 생성
    @Transactional
    public LocationServiceDTO createService(LocationServiceDTO serviceDTO) {
        // 중복 체크: 같은 주소가 이미 등록되어 있는지 확인
        if (serviceDTO.getAddress() != null && !serviceDTO.getAddress().trim().isEmpty()) {
            String address = serviceDTO.getAddress().trim();
            String detailAddress = serviceDTO.getDetailAddress() != null ? serviceDTO.getDetailAddress().trim() : "";

            List<LocationService> existingServices;

            // 상세주소가 있으면 주소+상세주소로 체크, 없으면 주소만으로 체크
            if (!detailAddress.isEmpty()) {
                existingServices = serviceRepository.findByAddressAndDetailAddress(address, detailAddress);
            } else {
                // 주소만 같은 것 중에서 상세주소도 비어있는 것 찾기
                List<LocationService> byAddress = serviceRepository.findByAddress(address);
                existingServices = byAddress.stream()
                        .filter(ls -> ls.getDetailAddress() == null || ls.getDetailAddress().trim().isEmpty())
                        .collect(Collectors.toList());
            }

            if (!existingServices.isEmpty()) {
                String errorMessage = String.format("이미 등록된 주소입니다: %s", address);
                if (!detailAddress.isEmpty()) {
                    errorMessage += " " + detailAddress;
                }
                log.warn("서비스 등록 실패 - 중복 주소: {}", errorMessage);
                throw new RuntimeException(errorMessage);
            }
        }

        // 위도/경도가 없고 주소가 있으면 자동으로 변환
        Double latitude = serviceDTO.getLatitude();
        Double longitude = serviceDTO.getLongitude();

        if ((latitude == null || longitude == null) && serviceDTO.getAddress() != null
                && !serviceDTO.getAddress().trim().isEmpty()) {
            log.info("주소를 위도/경도로 변환합니다: {}", serviceDTO.getAddress());
            Double[] coordinates = kakaoMapService.addressToCoordinates(serviceDTO.getAddress());
            if (coordinates != null && coordinates.length == 2) {
                latitude = coordinates[0];
                longitude = coordinates[1];
                log.info("변환 성공: 위도={}, 경도={}", latitude, longitude);
            } else {
                log.warn("주소를 위도/경도로 변환하지 못했습니다: {}", serviceDTO.getAddress());
            }
        }

        LocationService service = LocationService.builder()
                .name(serviceDTO.getName())
                .category(serviceDTO.getCategory())
                .address(serviceDTO.getAddress())
                .detailAddress(serviceDTO.getDetailAddress())
                .latitude(latitude)
                .longitude(longitude)
                .rating(serviceDTO.getRating())
                .phone(serviceDTO.getPhone())
                .openingTime(serviceDTO.getOpeningTime())
                .closingTime(serviceDTO.getClosingTime())
                .description(serviceDTO.getDescription())
                .petFriendly(serviceDTO.getPetFriendly() != null ? serviceDTO.getPetFriendly() : false)
                .petPolicy(serviceDTO.getPetPolicy())
                .build();

        LocationService savedService = serviceRepository.save(service);
        return convertToDTO(savedService);
    }

    // 서비스 수정
    @Transactional
    public LocationServiceDTO updateService(Long serviceIdx, LocationServiceDTO serviceDTO) {
        LocationService service = serviceRepository.findById(serviceIdx)
                .orElseThrow(() -> new RuntimeException("서비스를 찾을 수 없습니다."));

        service.setName(serviceDTO.getName());
        service.setCategory(serviceDTO.getCategory());
        service.setAddress(serviceDTO.getAddress());
        service.setDetailAddress(serviceDTO.getDetailAddress());
        service.setPhone(serviceDTO.getPhone());
        service.setOpeningTime(serviceDTO.getOpeningTime());
        service.setClosingTime(serviceDTO.getClosingTime());
        service.setDescription(serviceDTO.getDescription());
        if (serviceDTO.getPetFriendly() != null) {
            service.setPetFriendly(serviceDTO.getPetFriendly());
        }
        if (serviceDTO.getPetPolicy() != null) {
            service.setPetPolicy(serviceDTO.getPetPolicy());
        }

        // 위도/경도 설정 (주소 변경 시 자동 변환)
        Double latitude = serviceDTO.getLatitude();
        Double longitude = serviceDTO.getLongitude();

        // 주소가 변경되었거나 위도/경도가 없으면 자동 변환
        if ((latitude == null || longitude == null) && serviceDTO.getAddress() != null
                && !serviceDTO.getAddress().trim().isEmpty()) {
            if (!serviceDTO.getAddress().equals(service.getAddress()) || latitude == null || longitude == null) {
                log.info("수정 시 주소를 위도/경도로 변환합니다: {}", serviceDTO.getAddress());
                Double[] coordinates = kakaoMapService.addressToCoordinates(serviceDTO.getAddress());
                if (coordinates != null && coordinates.length == 2) {
                    latitude = coordinates[0];
                    longitude = coordinates[1];
                    log.info("변환 성공: 위도={}, 경도={}", latitude, longitude);
                }
            }
        }

        service.setLatitude(latitude);
        service.setLongitude(longitude);

        LocationService savedService = serviceRepository.save(service);
        return convertToDTO(savedService);
    }

    // 서비스 삭제
    @Transactional
    public void deleteService(Long serviceIdx) {
        serviceRepository.deleteById(serviceIdx);
    }

    // Entity를 DTO로 변환
    private LocationServiceDTO convertToDTO(LocationService service) {
        List<LocationServiceReviewDTO> reviews = null;
        if (service.getReviews() != null) {
            reviews = service.getReviews().stream()
                    .map(reviewConverter::toDTO)
                    .collect(Collectors.toList());
        }

        return LocationServiceDTO.builder()
                .idx(service.getIdx())
                .name(service.getName())
                .category(service.getCategory())
                .address(service.getAddress())
                .detailAddress(service.getDetailAddress())
                .latitude(service.getLatitude())
                .longitude(service.getLongitude())
                .rating(service.getRating())
                .phone(service.getPhone())
                .openingTime(service.getOpeningTime())
                .closingTime(service.getClosingTime())
                .description(service.getDescription())
                .petFriendly(service.getPetFriendly())
                .petPolicy(service.getPetPolicy())
                .reviewCount(service.getReviews() != null ? service.getReviews().size() : 0)
                .reviews(reviews)
                .build();
    }
}
