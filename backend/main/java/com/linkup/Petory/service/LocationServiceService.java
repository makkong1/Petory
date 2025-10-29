package com.linkup.Petory.service;

import com.linkup.Petory.converter.LocationServiceReviewConverter;
import com.linkup.Petory.dto.LocationServiceDTO;
import com.linkup.Petory.dto.LocationServiceReviewDTO;
import com.linkup.Petory.entity.LocationService;
import com.linkup.Petory.repository.LocationServiceRepository;
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
        LocationService service = LocationService.builder()
                .name(serviceDTO.getName())
                .category(serviceDTO.getCategory())
                .address(serviceDTO.getAddress())
                .latitude(serviceDTO.getLatitude())
                .longitude(serviceDTO.getLongitude())
                .rating(serviceDTO.getRating())
                .phoneNumber(serviceDTO.getPhoneNumber())
                .operatingHours(serviceDTO.getOperatingHours())
                .description(serviceDTO.getDescription())
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
        service.setLatitude(serviceDTO.getLatitude());
        service.setLongitude(serviceDTO.getLongitude());
        service.setPhoneNumber(serviceDTO.getPhoneNumber());
        service.setOperatingHours(serviceDTO.getOperatingHours());
        service.setDescription(serviceDTO.getDescription());

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
                .latitude(service.getLatitude())
                .longitude(service.getLongitude())
                .rating(service.getRating())
                .phoneNumber(service.getPhoneNumber())
                .operatingHours(service.getOperatingHours())
                .description(service.getDescription())
                .reviewCount(service.getReviews() != null ? service.getReviews().size() : 0)
                .reviews(reviews)
                .build();
    }
}
