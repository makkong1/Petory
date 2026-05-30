package com.linkup.Petory.domain.place.service;

import com.linkup.Petory.domain.place.dto.PlaceDto;
import com.linkup.Petory.domain.place.entity.Place;
import com.linkup.Petory.domain.place.entity.PlaceStatus;
import com.linkup.Petory.domain.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PlaceAdminService {

    private final PlaceRepository placeRepo;

    public Page<PlaceDto> listByStatus(PlaceStatus status, Pageable pageable) {
        return placeRepo.findByStatus(status, pageable).map(PlaceDto::from);
    }

    @Transactional
    public PlaceDto activate(Long id, String adminUsername) {
        Place place = placeRepo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (place.getStatus() != PlaceStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "activate 허용 상태: PENDING. 현재: " + place.getStatus());
        }

        place.setStatus(PlaceStatus.ACTIVE);
        place.setActivatedBy(adminUsername);
        place.setActivatedAt(LocalDateTime.now());
        placeRepo.save(place);
        return PlaceDto.from(place);
    }
}
