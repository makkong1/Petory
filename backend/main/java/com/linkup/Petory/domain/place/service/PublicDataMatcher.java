package com.linkup.Petory.domain.place.service;

import com.linkup.Petory.domain.location.entity.LocationService;
import com.linkup.Petory.domain.location.repository.SpringDataJpaLocationServiceRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PublicDataMatcher {

    private final SpringDataJpaLocationServiceRepository lsRepo;

    @Getter
    public static class MatchResult {
        private final Long locationServiceId;
        private final double nameSimilarity;
        private final double distanceMeters;

        public MatchResult(Long locationServiceId, double nameSimilarity, double distanceMeters) {
            this.locationServiceId = locationServiceId;
            this.nameSimilarity = nameSimilarity;
            this.distanceMeters = distanceMeters;
        }
    }

    /**
     * strong match: 이름 유사도 ≥ 0.85 AND (주소 정규화 일치 OR 좌표 150m 이내)
     */
    public Optional<MatchResult> findStrongMatch(String name, String address, Double lat, Double lng) {
        List<LocationService> candidates = loadCandidates(name, lat, lng, 500.0);
        for (LocationService ls : candidates) {
            double nameSim = StringSimilarityUtil.normalized(name, ls.getName());
            if (nameSim < 0.85) continue;
            boolean addressMatch = StringUtils.hasText(address)
                && StringUtils.hasText(ls.getAddress())
                && normalizeAddress(address).equals(normalizeAddress(ls.getAddress()));
            boolean coordMatch = lat != null && lng != null
                && ls.getLatitude() != null && ls.getLongitude() != null
                && GeoUtil.haversineMeters(lat, lng, ls.getLatitude(), ls.getLongitude()) <= 150.0;
            if (addressMatch || coordMatch) {
                double dist = (lat != null && ls.getLatitude() != null)
                    ? GeoUtil.haversineMeters(lat, lng, ls.getLatitude(), ls.getLongitude())
                    : Double.MAX_VALUE;
                return Optional.of(new MatchResult(ls.getIdx(), nameSim, dist));
            }
        }
        return Optional.empty();
    }

    /**
     * medium match: 이름 유사도 0.6~0.85 OR 좌표 150~500m 이내
     */
    public Optional<MatchResult> findMediumMatch(String name, String address, Double lat, Double lng) {
        List<LocationService> candidates = loadCandidates(name, lat, lng, 500.0);
        MatchResult best = null;
        for (LocationService ls : candidates) {
            double nameSim = StringSimilarityUtil.normalized(name, ls.getName());
            double dist = (lat != null && ls.getLatitude() != null)
                ? GeoUtil.haversineMeters(lat, lng, ls.getLatitude(), ls.getLongitude())
                : Double.MAX_VALUE;
            boolean nameInRange = nameSim >= 0.6 && nameSim < 0.85;
            boolean coordInRange = dist >= 150.0 && dist <= 500.0;
            if (nameInRange || coordInRange) {
                if (best == null || nameSim > best.getNameSimilarity()) {
                    best = new MatchResult(ls.getIdx(), nameSim, dist);
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private List<LocationService> loadCandidates(String name, Double lat, Double lng, double radiusMeters) {
        if (lat != null && lng != null) {
            double delta = GeoUtil.latLngDeltaForMeters(radiusMeters);
            return lsRepo.findInBoundingBox(lat - delta, lat + delta, lng - delta, lng + delta);
        }
        // 좌표 없으면 이름 첫 2글자로 pre-filter
        String prefix = (name != null && name.length() >= 2) ? name.substring(0, 2) : "";
        return StringUtils.hasText(prefix) ? lsRepo.findByNamePrefix(prefix) : List.of();
    }

    private String normalizeAddress(String address) {
        if (address == null) return "";
        return address.trim()
            .replaceAll("\\s+", " ")
            .replaceAll("([0-9]+)가", "$1")
            .toLowerCase();
    }
}
