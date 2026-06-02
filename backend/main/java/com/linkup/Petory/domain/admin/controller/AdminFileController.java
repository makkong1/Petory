package com.linkup.Petory.domain.admin.controller;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.linkup.Petory.domain.admin.service.AdminFileFacade;
import com.linkup.Petory.domain.file.dto.FileDTO;
import com.linkup.Petory.global.security.AuthenticatedUserIdResolver;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/files")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','MASTER')")
/** 관리자용 첨부파일 목록 조회·삭제 API. [ADMIN, MASTER] */
public class AdminFileController {

    private final AdminFileFacade adminFileFacade;
    private final AuthenticatedUserIdResolver userIdResolver;

    @GetMapping
    public ResponseEntity<Page<FileDTO>> listFiles(
            @RequestParam(name = "targetType", required = false) String targetType,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(adminFileFacade.getFiles(targetType, q, page, size));
    }

    @GetMapping("/target")
    public ResponseEntity<List<FileDTO>> getFilesByTarget(
            @RequestParam("targetType") String targetType,
            @RequestParam("targetIdx") Long targetIdx) {
        return ResponseEntity.ok(adminFileFacade.getFilesByTarget(targetType, targetIdx));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(@PathVariable("id") Long id) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        adminFileFacade.deleteFile(id, adminIdx);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/target")
    public ResponseEntity<Void> deleteFilesByTarget(
            @RequestParam("targetType") String targetType,
            @RequestParam("targetIdx") Long targetIdx) {
        Long adminIdx = userIdResolver.requireCurrentUserIdx();
        adminFileFacade.deleteFilesByTarget(targetType, targetIdx, adminIdx);
        return ResponseEntity.noContent().build();
    }
}
