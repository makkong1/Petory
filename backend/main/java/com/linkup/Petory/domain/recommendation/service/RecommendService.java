package com.linkup.Petory.domain.recommendation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.linkup.Petory.domain.location.dto.LocationServiceDTO;
import com.linkup.Petory.domain.location.service.LocationServiceService;
import com.linkup.Petory.domain.recommendation.client.PetDataApiClient;
import com.linkup.Petory.domain.recommendation.dto.RecommendCopyRequest;
import com.linkup.Petory.domain.recommendation.dto.RecommendCopyResponse;
import com.linkup.Petory.domain.recommendation.dto.RecommendEventRequest;
import com.linkup.Petory.domain.recommendation.dto.RecommendRequest;
import com.linkup.Petory.domain.recommendation.dto.RecommendResponse;
import com.linkup.Petory.domain.recommendation.dto.TrendTimeseriesResponse;
import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.repository.PetRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendService {

    private static final double DEFAULT_RADIUS_KM = 10.0;
    private static final int DEFAULT_RADIUS_METERS = 10_000;
    private static final int DEFAULT_TOP_N = 5;
    private static final int NEARBY_CANDIDATE_LIMIT = 20;
    private static final int DEFAULT_TREND_LIMIT = 15;

    private static final Set<String> PETORY_OWNED_CONTEXTS = Set.of(
            "grooming", "hospital", "pharmacy", "cafe", "restaurant", "pension",
            "boarding", "hotel");

    private static final Map<String, String> CONTEXT_TO_CATEGORY = createContextToCategory();
    private static final Map<String, String> CONTEXT_LABELS = createContextLabels();
    private static final Map<String, List<String>> CONTEXT_NAME_SUFFIXES = createContextNameSuffixes();

    private final PetRepository petRepository;
    private final PetDataApiClient petDataApiClient;
    private final LocationServiceService locationServiceService;

    public RecommendResponse recommend(String userId, double lat, double lng, String context) {
        RecommendRequest.PetInfo petInfo = findPetInfo(userId);
        String normalizedContext = normalizeContext(context);

        RecommendResponse response = isPetoryOwnedContext(normalizedContext)
                ? recommendWithPetoryCandidates(lat, lng, normalizedContext, petInfo)
                : recommendWithLegacyProxy(lat, lng, context, petInfo);

        int nf = response.facilities() == null ? 0 : response.facilities().size();
        int nt = response.trends() == null ? 0 : response.trends().size();
        int nr = response.recommendation() == null ? 0 : response.recommendation().length();
        log.info(
                "[RecommendService→Petory 응답] request_id={} context={} facilities={} trends={} reco_chars={}",
                response.requestId(), response.context(), nf, nt, nr);
        return response;
    }

    private RecommendResponse recommendWithLegacyProxy(
            double lat,
            double lng,
            String context,
            RecommendRequest.PetInfo petInfo) {
        RecommendRequest request = RecommendRequest.builder()
                .lat(lat)
                .lng(lng)
                .context(context)
                .radiusKm(DEFAULT_RADIUS_KM)
                .topN(DEFAULT_TOP_N)
                .pet(petInfo)
                .build();
        return petDataApiClient.recommend(request);
    }

    private RecommendResponse recommendWithPetoryCandidates(
            double lat,
            double lng,
            String context,
            RecommendRequest.PetInfo petInfo) {
        String requestId = newRequestId();
        String category = CONTEXT_TO_CATEGORY.get(context);

        List<LocationServiceDTO> nearbyCandidates = locationServiceService.searchLocationServicesByLocation(
                lat,
                lng,
                DEFAULT_RADIUS_METERS,
                null,
                category,
                "distance",
                NEARBY_CANDIDATE_LIMIT);

        List<RecommendResponse.FacilityItem> popularSignals =
                petDataApiClient.fetchPopular(context, NEARBY_CANDIDATE_LIMIT, requestId);
        List<RecommendResponse.TrendItem> trends =
                petDataApiClient.fetchTrends(context, DEFAULT_TREND_LIMIT, requestId);
        List<RecommendResponse.FacilityItem> facilities =
                mergeNearbyCandidates(nearbyCandidates, popularSignals, context);

        return new RecommendResponse(
                context,
                "petory-nearby-v1",
                requestId,
                facilities,
                trends,
                buildNearbyRecommendation(context, petInfo, facilities, trends),
                OffsetDateTime.now(ZoneOffset.UTC).toString());
    }

    public RecommendCopyResponse recommendCopy(String userId, RecommendCopyRequest body) {
        // 카피 호출에도 같은 펫 컨텍스트가 필요. 프론트에는 펫 정보를 넘기지 않고,
        // 백엔드가 첫 호출과 동일한 로직으로 채워 넣어 일관성을 보장한다.
        RecommendRequest.PetInfo petInfo = findPetInfo(userId);

        RecommendCopyRequest enriched = RecommendCopyRequest.builder()
                .context(body.context())
                .requestId(body.requestId())
                .facilities(body.facilities())
                .trends(body.trends())
                .pet(petInfo)
                .build();

        return petDataApiClient.recommendCopy(enriched);
    }

    public TrendTimeseriesResponse getTrendTimeseries(String category, int days, int topKeywords) {
        return petDataApiClient.getTrendTimeseries(category, days, topKeywords);
    }

    public void recordEvents(String userId, RecommendEventRequest body) {
        // 가이드 §6: user_ref 는 익명 식별자. Petory userId 의 해시 일부로 자동 생성해
        // 원본 사용자 ID 노출 없이 추천 품질 환류만 가능하게 한다.
        String userRef = hashUserId(userId);

        List<RecommendEventRequest.Event> events = body.events() == null
                ? List.of()
                : body.events().stream()
                        .map(e -> RecommendEventRequest.Event.builder()
                                .facilityId(e.facilityId())
                                .sourceId(e.sourceId())
                                .event(e.event())
                                .occurredAt(e.occurredAt())
                                .userRef(e.userRef() != null ? e.userRef() : userRef)
                                .build())
                        .toList();

        RecommendEventRequest enriched = RecommendEventRequest.builder()
                .requestId(body.requestId())
                .events(events)
                .build();

        petDataApiClient.sendEvents(enriched);
    }

    private static String hashUserId(String userId) {
        if (userId == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(userId.getBytes(StandardCharsets.UTF_8));
            return "petory-" + HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 은 JRE 표준. 실제 발생 가능성 없음 — 폴백은 원본 ID 비노출 위해 그냥 null.
            return null;
        }
    }

    private RecommendRequest.PetInfo findPetInfo(String userId) {
        List<Pet> pets = petRepository.findByUserIdAndNotDeleted(userId);
        return pets.isEmpty() ? null : toPetInfo(pets.get(0));
    }

    private List<RecommendResponse.FacilityItem> mergeNearbyCandidates(
            List<LocationServiceDTO> nearbyCandidates,
            List<RecommendResponse.FacilityItem> popularSignals,
            String context) {
        if (nearbyCandidates == null || nearbyCandidates.isEmpty()) {
            return List.of();
        }
        if (popularSignals == null) {
            popularSignals = List.of();
        }

        Map<String, PopularSignal> popularIndex = indexPopularSignals(popularSignals, context);
        int maxMentionCount = popularSignals.stream()
                .map(RecommendResponse.FacilityItem::mentionCount)
                .filter(count -> count != null && count > 0)
                .max(Integer::compareTo)
                .orElse(0);
        double maxMentionScore = popularSignals.stream()
                .map(RecommendResponse.FacilityItem::mentionScore)
                .filter(score -> score != null && score > 0)
                .max(Double::compareTo)
                .orElse(0.0);

        return nearbyCandidates.stream()
                .map(candidate -> toRankedCandidate(candidate, context, popularIndex, maxMentionCount, maxMentionScore))
                .sorted((left, right) -> {
                    int byScore = Double.compare(right.finalScore(), left.finalScore());
                    if (byScore != 0) {
                        return byScore;
                    }
                    int byDistance = Integer.compare(left.item().distanceM(), right.item().distanceM());
                    if (byDistance != 0) {
                        return byDistance;
                    }
                    return left.item().name().compareToIgnoreCase(right.item().name());
                })
                .limit(DEFAULT_TOP_N)
                .map(RankedCandidate::item)
                .toList();
    }

    private RankedCandidate toRankedCandidate(
            LocationServiceDTO candidate,
            String context,
            Map<String, PopularSignal> popularIndex,
            int maxMentionCount,
            double maxMentionScore) {
        PopularSignal popular = findPopularSignal(candidate.getName(), context, popularIndex);

        int distanceM = candidate.getDistance() == null
                ? DEFAULT_RADIUS_METERS
                : Math.max(0, (int) Math.round(candidate.getDistance()));
        double distanceScore = clamp01(1.0 - (Math.min(distanceM, DEFAULT_RADIUS_METERS) / (double) DEFAULT_RADIUS_METERS));
        double ratingScore = clamp01((candidate.getRating() == null ? 0.0 : candidate.getRating()) / 5.0);
        int reviewCount = candidate.getReviewCount() == null ? 0 : candidate.getReviewCount();
        double reviewScore = reviewCount > 0
                ? clamp01(Math.log1p(reviewCount) / Math.log(101))
                : 0.0;

        double popularityScore = 0.0;
        Integer mentionCount = null;
        Double mentionScore = null;
        if (popular != null) {
            mentionCount = popular.mentionCount();
            mentionScore = popular.mentionScore();
            double mentionCountScore = maxMentionCount > 0 && mentionCount != null
                    ? clamp01(mentionCount / (double) maxMentionCount)
                    : 0.0;
            double mentionScoreNorm = maxMentionScore > 0 && mentionScore != null
                    ? clamp01(mentionScore / maxMentionScore)
                    : 0.0;
            popularityScore = (mentionScoreNorm * 0.6) + (mentionCountScore * 0.4);
        }

        // 초기 휴리스틱: 가까운 후보를 우선하고, 인기/리뷰 신호는 보정값으로만 사용한다.
        double finalScore = (distanceScore * 0.55)
                + (ratingScore * 0.20)
                + (reviewScore * 0.15)
                + (popularityScore * 0.10);

        List<String> reasons = new ArrayList<>();
        reasons.add("nearby");
        if (popular != null) {
            reasons.add("popular_signal");
        }
        if (reviewCount > 0) {
            reasons.add("reviewed");
        }
        if (candidate.getRating() != null && candidate.getRating() >= 4.5) {
            reasons.add("high_rating");
        }

        RecommendResponse.FacilityItem item = new RecommendResponse.FacilityItem(
                candidate.getIdx(),
                null,
                candidate.getName(),
                distanceM,
                candidate.getAddress(),
                candidate.getLatitude(),
                candidate.getLongitude(),
                mentionCount,
                mentionScore,
                "petory_location",
                finalScore,
                List.copyOf(reasons));

        return new RankedCandidate(item, finalScore);
    }

    private Map<String, PopularSignal> indexPopularSignals(
            List<RecommendResponse.FacilityItem> popularSignals,
            String context) {
        Map<String, PopularSignal> indexed = new LinkedHashMap<>();
        if (popularSignals == null) {
            return indexed;
        }

        for (RecommendResponse.FacilityItem signal : popularSignals) {
            if (signal == null || signal.name() == null || signal.name().isBlank()) {
                continue;
            }
            PopularSignal current = new PopularSignal(signal.mentionCount(), signal.mentionScore());
            for (String key : buildNameAliases(signal.name(), context)) {
                indexed.merge(key, current, RecommendService::pickBetterSignal);
            }
        }
        return indexed;
    }

    private PopularSignal findPopularSignal(
            String facilityName,
            String context,
            Map<String, PopularSignal> popularIndex) {
        for (String key : buildNameAliases(facilityName, context)) {
            PopularSignal matched = popularIndex.get(key);
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    private List<String> buildNameAliases(String rawName, String context) {
        String normalized = normalizeFacilityName(rawName);
        if (normalized.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> aliases = new LinkedHashSet<>();
        aliases.add(normalized);
        for (String suffix : CONTEXT_NAME_SUFFIXES.getOrDefault(context, List.of())) {
            String normalizedSuffix = normalizeFacilityName(suffix);
            if (normalized.endsWith(normalizedSuffix)) {
                String stripped = normalized.substring(0, normalized.length() - normalizedSuffix.length());
                if (stripped.length() >= 2) {
                    aliases.add(stripped);
                }
            }
            if (normalized.startsWith(normalizedSuffix)) {
                String stripped = normalized.substring(normalizedSuffix.length());
                if (stripped.length() >= 2) {
                    aliases.add(stripped);
                }
            }
        }
        return List.copyOf(aliases);
    }

    private String buildNearbyRecommendation(
            String context,
            RecommendRequest.PetInfo petInfo,
            List<RecommendResponse.FacilityItem> facilities,
            List<RecommendResponse.TrendItem> trends) {
        String label = CONTEXT_LABELS.getOrDefault(context, context);
        String petLabel = toPetLabel(petInfo);

        if (facilities.isEmpty() && trends.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        if (!facilities.isEmpty()) {
            RecommendResponse.FacilityItem first = facilities.get(0);
            sb.append(petLabel)
                    .append(" 기준 현재 위치에서 가까운 ")
                    .append(label)
                    .append(" 후보를 먼저 골랐어요. ");
            sb.append("가장 먼저 볼 곳은 '")
                    .append(first.name())
                    .append("'");
            if (first.distanceM() > 0) {
                sb.append(" (약 ").append(first.distanceM()).append("m)");
            }
            sb.append("입니다.");
            if (first.mentionCount() != null && first.mentionCount() > 0) {
                sb.append(" 블로그 언급 신호도 함께 확인됐어요.");
            }
        } else {
            sb.append(label)
                    .append(" 주변 후보는 아직 적지만, 인기 키워드 신호는 함께 볼 수 있어요.");
        }

        if (!trends.isEmpty()) {
            sb.append(" 요즘 키워드는 '")
                    .append(trends.get(0).keyword())
                    .append("'");
            if (trends.size() > 1) {
                sb.append(", '").append(trends.get(1).keyword()).append("'");
            }
            sb.append(" 입니다.");
        }
        return sb.toString();
    }

    private static PopularSignal pickBetterSignal(PopularSignal left, PopularSignal right) {
        double leftScore = left.mentionScore() == null ? 0.0 : left.mentionScore();
        double rightScore = right.mentionScore() == null ? 0.0 : right.mentionScore();
        if (Double.compare(leftScore, rightScore) != 0) {
            return leftScore >= rightScore ? left : right;
        }
        int leftCount = left.mentionCount() == null ? 0 : left.mentionCount();
        int rightCount = right.mentionCount() == null ? 0 : right.mentionCount();
        return leftCount >= rightCount ? left : right;
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String normalizeContext(String context) {
        if (context == null || context.isBlank()) {
            return "";
        }
        return context.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeFacilityName(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static boolean isPetoryOwnedContext(String context) {
        return PETORY_OWNED_CONTEXTS.contains(context);
    }

    private static String toPetLabel(RecommendRequest.PetInfo petInfo) {
        if (petInfo == null || petInfo.type() == null) {
            return "반려동물";
        }
        return switch (petInfo.type().toLowerCase(Locale.ROOT)) {
            case "dog" -> "반려견";
            case "cat" -> "반려묘";
            default -> "반려동물";
        };
    }

    private static String newRequestId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static Map<String, String> createContextToCategory() {
        Map<String, String> mapping = new LinkedHashMap<>();
        mapping.put("grooming", "미용");
        mapping.put("hospital", "동물병원");
        mapping.put("pharmacy", "동물약국");
        mapping.put("cafe", "카페");
        mapping.put("restaurant", "식당");
        mapping.put("pension", "펜션");
        mapping.put("boarding", "위탁관리");
        mapping.put("hotel", "호텔");
        mapping.put("supplies", "반려동물용품");
        return Map.copyOf(mapping);
    }

    private static Map<String, String> createContextLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("grooming", "미용");
        labels.put("hospital", "동물병원");
        labels.put("pharmacy", "동물약국");
        labels.put("cafe", "카페");
        labels.put("restaurant", "식당");
        labels.put("pension", "펜션");
        labels.put("boarding", "위탁관리");
        labels.put("hotel", "호텔");
        labels.put("supplies", "반려동물용품");
        labels.put("snack", "간식");
        labels.put("food", "사료");
        labels.put("clothes", "의류");
        return Map.copyOf(labels);
    }

    private static Map<String, List<String>> createContextNameSuffixes() {
        Map<String, List<String>> suffixes = new LinkedHashMap<>();
        suffixes.put("grooming", List.of("미용", "애견미용", "반려동물미용", "애견미용실"));
        suffixes.put("hospital", List.of("동물병원", "24시동물병원", "병원"));
        suffixes.put("pharmacy", List.of("동물약국", "약국"));
        suffixes.put("cafe", List.of("카페", "애견카페", "펫카페"));
        suffixes.put("restaurant", List.of("식당", "레스토랑", "음식점"));
        suffixes.put("pension", List.of("펜션"));
        suffixes.put("boarding", List.of("위탁관리", "애견유치원", "유치원"));
        suffixes.put("hotel", List.of("호텔", "애견호텔"));
        suffixes.put("supplies", List.of("반려동물용품", "용품", "펫샵"));
        return Map.copyOf(suffixes);
    }

    private record PopularSignal(Integer mentionCount, Double mentionScore) {
    }

    private record RankedCandidate(RecommendResponse.FacilityItem item, double finalScore) {
    }

    private RecommendRequest.PetInfo toPetInfo(Pet pet) {
        Integer ageMonths = null;
        if (pet.getBirthDate() != null) {
            ageMonths = (int) ChronoUnit.MONTHS.between(pet.getBirthDate(), LocalDate.now());
        }

        return RecommendRequest.PetInfo.builder()
                .type(pet.getPetType().name().toLowerCase())
                .breed(pet.getBreed())
                .ageMonths(ageMonths)
                .build();
    }
}
