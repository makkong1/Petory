package com.linkup.Petory.domain.recommendation.service;

import com.linkup.Petory.domain.recommendation.client.PetDataApiClient;
import com.linkup.Petory.domain.recommendation.dto.RecommendRequest;
import com.linkup.Petory.domain.recommendation.dto.RecommendResponse;
import com.linkup.Petory.domain.user.entity.Pet;
import com.linkup.Petory.domain.user.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendService {

    private final PetRepository petRepository;
    private final PetDataApiClient petDataApiClient;

    public RecommendResponse recommend(String userId, double lat, double lng, String context) {
        List<Pet> pets = petRepository.findByUserIdAndNotDeleted(userId);

        RecommendRequest.PetInfo petInfo = pets.isEmpty() ? null : toPetInfo(pets.get(0));

        RecommendRequest request = RecommendRequest.builder()
                .lat(lat)
                .lng(lng)
                .context(context)
                .radiusKm(3.0)
                .topN(5)
                .pet(petInfo)
                .build();

        return petDataApiClient.recommend(request);
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
