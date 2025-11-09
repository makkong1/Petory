package com.linkup.Petory.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.linkup.Petory.service.FileStorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class FileUploadController {

    private final FileStorageService fileStorageService;

    @PostMapping("/images")
    public ResponseEntity<Map<String, Object>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "ownerType", required = false) String ownerType,
            @RequestParam(value = "ownerId", required = false) String ownerId,
            @RequestParam(value = "entityId", required = false) String entityId) {

        List<String> segments = new ArrayList<>();
        if (StringUtils.hasText(category)) {
            segments.add(category);
        }
        if (StringUtils.hasText(ownerType)) {
            segments.add(ownerType);
        }
        if (StringUtils.hasText(ownerId)) {
            segments.add(ownerId);
        }
        if (StringUtils.hasText(entityId)) {
            segments.add(entityId);
        }

        String storedPath = fileStorageService.storeImage(file, segments.toArray(String[]::new));
        String fileUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/uploads/file")
                .queryParam("path", storedPath)
                .toUriString();

        Map<String, Object> response = new HashMap<>();
        response.put("path", storedPath);
        response.put("filename", storedPath.substring(storedPath.lastIndexOf('/') + 1));
        response.put("url", fileUrl);
        response.put("contentType", file.getContentType());
        response.put("size", file.getSize());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/file")
    public ResponseEntity<Resource> serveFile(@RequestParam("path") String relativePath) {
        Resource resource;
        try {
            resource = fileStorageService.loadAsResource(relativePath);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.notFound().build();
        }

        String contentType = detectContentType(resource);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    private String detectContentType(Resource resource) {
        try {
            Path path = Path.of(resource.getURI());
            String detectedType = Files.probeContentType(path);
            return detectedType != null ? detectedType : MediaType.APPLICATION_OCTET_STREAM_VALUE;
        } catch (IOException ex) {
            log.warn("Could not determine content type for {}", resource.getFilename(), ex);
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
    }
}
