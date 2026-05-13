package com.linkup.Petory.domain.recommendation.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;

import org.springframework.stereotype.Service;

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

@Service
@RequiredArgsConstructor
public class RecommendService {

    private final PetRepository petRepository;
    private final PetDataApiClient petDataApiClient;

    public RecommendResponse recommend(String userId, double lat, double lng, String context) {
        RecommendRequest.PetInfo petInfo = findPetInfo(userId);

        RecommendRequest request = RecommendRequest.builder()
                .lat(lat)
                .lng(lng)
                .context(context)
                .radiusKm(10.0)
                .topN(5)
                .pet(petInfo)
                .build();

        return petDataApiClient.recommend(request);
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
