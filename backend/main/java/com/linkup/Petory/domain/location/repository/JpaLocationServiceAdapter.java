package com.linkup.Petory.domain.location.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import com.linkup.Petory.domain.location.entity.LocationService;

import lombok.RequiredArgsConstructor;

/**
 * LocationServiceRepository의 JPA 구현체(어댑터)입니다.
 */
@Repository
@Primary
@RequiredArgsConstructor
public class JpaLocationServiceAdapter implements LocationServiceRepository {

    private final SpringDataJpaLocationServiceRepository jpaRepository;

    @Override
    public LocationService save(LocationService locationService) {
        return jpaRepository.save(locationService);
    }

    @Override
    public List<LocationService> saveAll(List<LocationService> locationServices) {
        return jpaRepository.saveAll(locationServices);
    }

    @Override
    public Optional<LocationService> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<LocationService> findByOrderByRatingDesc() {
        return jpaRepository.findByOrderByRatingDesc();
    }

    @Override
    public List<LocationService> findTop10ByCategoryOrderByRatingDesc(String category) {
        return jpaRepository.findTop10ByCategoryOrderByRatingDesc(category);
    }

    @Override
    public List<LocationService> findByNameContaining(String keyword) {
        return jpaRepository.findByNameContaining(keyword);
    }

    @Override
    public boolean existsByNameAndAddress(String name, String address) {
        return jpaRepository.existsByNameAndAddress(name, address);
    }

    @Override
    public List<LocationService> findByRadius(Double latitude, Double longitude, Double radiusInMeters) {
        return jpaRepository.findByRadius(latitude, longitude, radiusInMeters);
    }

    @Override
    public List<LocationService> findBySigungu(String sigungu) {
        return jpaRepository.findBySigungu(sigungu);
    }

    @Override
    public List<LocationService> findBySido(String sido) {
        return jpaRepository.findBySido(sido);
    }

    @Override
    public List<LocationService> findByEupmyeondong(String eupmyeondong) {
        return jpaRepository.findByEupmyeondong(eupmyeondong);
    }

    @Override
    public List<LocationService> findByRoadName(String roadName) {
        return jpaRepository.findByRoadName(roadName);
    }

    @Override
    public void updateRatingByAvg(Long serviceIdx) {
        jpaRepository.updateRatingByAvg(serviceIdx);
    }
}
