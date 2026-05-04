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

    @SuppressWarnings("null")
    @Override
    public LocationService save(LocationService locationService) {
        return jpaRepository.save(locationService);
    }

    @SuppressWarnings("null")
    @Override
    public List<LocationService> saveAll(List<LocationService> locationServices) {
        return jpaRepository.saveAll(locationServices);
    }

    @SuppressWarnings("null")
    @Override
    public Optional<LocationService> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public List<LocationService> findByOrderByRatingDesc(String keyword, String category) {
        return jpaRepository.findByOrderByRatingDesc(keyword, category);
    }

    @Override
    public List<LocationService> findTop10ByCategoryOrderByRatingDesc(String category) {
        return jpaRepository.findTop10ByCategoryOrderByRatingDesc(category);
    }

    @Override
    public List<LocationService> findByNameContaining(String keyword, String category) {
        return jpaRepository.findByNameContaining(keyword, category);
    }

    @Override
    public boolean existsByNameAndAddress(String name, String address) {
        return jpaRepository.existsByNameAndAddress(name, address);
    }

    @Override
    public List<LocationService> findByRadius(Double latitude, Double longitude, Double radiusInMeters,
            String keyword, String category, String sort) {
        return jpaRepository.findByRadius(latitude, longitude, radiusInMeters, keyword, category, sort);
    }

    @Override
    public List<LocationService> findBySigungu(String sigungu, String keyword, String category) {
        return jpaRepository.findBySigungu(sigungu, keyword, category);
    }

    @Override
    public List<LocationService> findBySido(String sido, String keyword, String category) {
        return jpaRepository.findBySido(sido, keyword, category);
    }

    @Override
    public List<LocationService> findByEupmyeondong(String eupmyeondong, String keyword, String category) {
        return jpaRepository.findByEupmyeondong(eupmyeondong, keyword, category);
    }

    @Override
    public List<LocationService> findByRoadName(String roadName, String keyword, String category) {
        return jpaRepository.findByRoadName(roadName, keyword, category);
    }

    @Override
    public void updateReviewStats(Long serviceIdx) {
        jpaRepository.updateReviewStats(serviceIdx);
    }
}
