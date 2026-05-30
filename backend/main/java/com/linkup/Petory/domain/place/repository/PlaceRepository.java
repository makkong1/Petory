package com.linkup.Petory.domain.place.repository;

import com.linkup.Petory.domain.place.entity.Place;
import com.linkup.Petory.domain.place.entity.PlaceStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlaceRepository extends JpaRepository<Place, Long> {
    Page<Place> findByStatus(PlaceStatus status, Pageable pageable);
}
