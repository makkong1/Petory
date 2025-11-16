package com.linkup.Petory.domain.location.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.location.dto.LocationServiceLoadResponse;
import com.linkup.Petory.domain.location.service.LocationServiceAdminService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/location-services")
@RequiredArgsConstructor
public class LocationServiceAdminController {

    private final LocationServiceAdminService locationServiceAdminService;

    @PostMapping("/load-data")
    public ResponseEntity<LocationServiceLoadResponse> loadInitialData(
            @RequestParam(defaultValue = "서울특별시") String region,
            @RequestParam(defaultValue = "10") Integer maxResultsPerKeyword,
            @RequestParam(required = false) String customKeywords) {

        LocationServiceLoadResponse response = locationServiceAdminService.loadInitialData(
                region,
                maxResultsPerKeyword,
                customKeywords);
        return ResponseEntity.ok(response);
    }
}

