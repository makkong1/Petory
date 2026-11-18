package com.linkup.Petory.domain.location.controller;

import java.time.LocalDate;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.linkup.Petory.domain.location.entity.PetNews;
import com.linkup.Petory.domain.location.repository.PetNewsRepository;

import lombok.RequiredArgsConstructor;

/**
 * 소식 1건 조회. <b>목록 API 도 전용 화면도 없다</b> — 사용자는 알림과 지도 배너로만 본다.
 *
 * <p>
 * 이 엔드포인트가 필요한 이유는 하나다. 알림에는 {@code relatedId} 만 들어 있어서,
 * 배너에서 <b>원문으로 보내려면</b> 그 id 로 제목과 원문 주소를 한 번 읽어야 한다.
 * 본문은 저장하지 않으므로 여기서도 돌려줄 것이 없다(원문 링크로 보낸다).
 */
@RestController
@RequestMapping("/api/pet-news")
@RequiredArgsConstructor
public class PetNewsController {

    private final PetNewsRepository petNewsRepository;

    @GetMapping("/{idx}")
    public ResponseEntity<PetNewsResponse> get(@PathVariable Long idx) {
        return petNewsRepository.findById(idx)
                .map(PetNewsResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record PetNewsResponse(Long idx, String title, String dept, String category,
            LocalDate publishedAt, String sourceUrl) {

        static PetNewsResponse from(PetNews news) {
            return new PetNewsResponse(news.getIdx(), news.getTitle(), news.getDept(),
                    news.getCategory().name(), news.getPublishedAt(), news.getSourceUrl());
        }
    }
}
