package com.linkup.Petory.domain.place.controller;

import com.linkup.Petory.domain.place.dto.PlaceDto;
import com.linkup.Petory.domain.place.entity.PlaceStatus;
import com.linkup.Petory.domain.place.service.PlaceAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/places")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'MASTER')")
public class PlaceAdminController {

    private final PlaceAdminService service;

    @GetMapping
    public ResponseEntity<Page<PlaceDto>> list(
        @RequestParam(defaultValue = "PENDING") PlaceStatus status,
        @PageableDefault(size = 20, sort = "confidence", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return ResponseEntity.ok(service.listByStatus(status, pageable));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<PlaceDto> activate(@PathVariable Long id, Authentication auth) {
        return ResponseEntity.ok(service.activate(id, auth.getName()));
    }
}
