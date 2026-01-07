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
    public Optional<LocationService> findById(Long id) {
        return jpaRepository.findById(id);
    }

    @Override
    public void delete(LocationService locationService) {
        jpaRepository.delete(locationService);
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public List<LocationService> findByLocationRange(
            Double minLat,
            Double maxLat,
            Double minLng,
            Double maxLng) {
        return jpaRepository.findByLocationRange(minLat, maxLat, minLng, maxLng);
    }

    @Override
    public List<LocationService> findByOrderByRatingDesc() {
        return jpaRepository.findByOrderByRatingDesc();
    }

    @Override
    public List<LocationService> findByCategoryOrderByRatingDesc(String category) {
        return jpaRepository.findByCategoryOrderByRatingDesc(category);
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
    public List<LocationService> findByRatingGreaterThanEqualOrderByRatingDesc(Double minRating) {
        return jpaRepository.findByRatingGreaterThanEqualOrderByRatingDesc(minRating);
    }

    @Override
    public List<LocationService> findByNameAndAddress(String name, String address) {
        return jpaRepository.findByNameAndAddress(name, address);
    }

    @Override
    public boolean existsByNameAndAddress(String name, String address) {
        return jpaRepository.existsByNameAndAddress(name, address);
    }

    @Override
    public List<LocationService> findByAddress(String address) {
        return jpaRepository.findByAddress(address);
    }

    @Override
    public List<LocationService> findByAddressContaining(String address) {
        return jpaRepository.findByAddressContaining(address);
    }

    @Override
    public List<LocationService> findByRadius(Double latitude, Double longitude, Double radiusInMeters) {
        return jpaRepository.findByRadius(latitude, longitude, radiusInMeters);
    }

    @Override
    public List<LocationService> findBySeoulGuAndDong(String gu, String dong) {
        return jpaRepository.findBySeoulGuAndDong(gu, dong);
    }

    @Override
    public List<LocationService> findByRegion(String sido, String sigungu, String dong) {
        return jpaRepository.findByRegion(sido, sigungu, dong);
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
    public List<LocationService> findByUserLocation(String sigungu, String eupmyeondong) {
        return jpaRepository.findByUserLocation(sigungu, eupmyeondong);
    }

    @Override
    public List<LocationService> findByRadiusOrderByDistance(
            Double latitude,
            Double longitude,
            Double radiusInMeters) {
        return jpaRepository.findByRadiusOrderByDistance(latitude, longitude, radiusInMeters);
    }
}

