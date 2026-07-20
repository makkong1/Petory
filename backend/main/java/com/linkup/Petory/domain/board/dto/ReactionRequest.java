package com.linkup.Petory.domain.board.dto;

import com.linkup.Petory.domain.board.entity.ReactionType;

import jakarta.validation.constraints.NotNull;

/**
 * 좋아요/싫어요 반응 요청 DTO record: 불변 데이터 캐리어. Jackson 역직렬화(@RequestBody) 지원.
 * 대상 유저는 인증 주체(JWT)에서 컨트롤러가 주입하므로 body에 userId를 받지 않는다.
 */
public record ReactionRequest(
        @NotNull
        ReactionType reactionType) {

}
