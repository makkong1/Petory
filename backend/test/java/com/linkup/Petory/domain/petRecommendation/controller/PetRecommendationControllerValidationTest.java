package com.linkup.Petory.domain.petRecommendation.controller;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.validation.annotation.Validated;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T3: text 파라미터 @Size(max=500) 검증.
 * T5: interactionType 파라미터 @Pattern(VIEW|NAVIGATE|FAVORITE) 검증.
 */
class PetRecommendationControllerValidationTest {

    @Test
    @DisplayName("Controller 클래스에 @Validated 가 선언되어 있다 (T3/T5 전제 조건)")
    void controller_hasValidatedAnnotation() {
        assertThat(PetRecommendationController.class.getAnnotation(Validated.class))
                .as("@Validated 가 없으면 @Size, @Pattern 이 동작하지 않음")
                .isNotNull();
    }

    @Test
    @DisplayName("recommend() text 파라미터에 @Size(max=500) 가 선언되어 있다 (T3)")
    void recommend_textParam_hasSizeMax500() throws Exception {
        Method method = PetRecommendationController.class
                .getDeclaredMethod("recommend", double.class, double.class, String.class, int.class, String.class);

        Parameter textParam = method.getParameters()[2]; // lat, lng, text, radius, petType
        Size sizeAnnotation = textParam.getAnnotation(Size.class);

        assertThat(sizeAnnotation)
                .as("text 파라미터에 @Size 가 없음 — 대용량 입력이 NLP 서버에 그대로 전달됨")
                .isNotNull();
        assertThat(sizeAnnotation.max())
                .as("max 가 500 이어야 함")
                .isEqualTo(500);
    }

    @Test
    @DisplayName("interact() interactionType 파라미터에 @Pattern(VIEW|NAVIGATE|FAVORITE) 이 선언되어 있다 (T5)")
    void interact_interactionTypeParam_hasPatternConstraint() throws Exception {
        Method method = PetRecommendationController.class
                .getDeclaredMethod("interact", Long.class, String.class);

        Parameter typeParam = method.getParameters()[1]; // locationIdx, interactionType
        Pattern patternAnnotation = typeParam.getAnnotation(Pattern.class);

        assertThat(patternAnnotation)
                .as("interactionType 파라미터에 @Pattern 이 없음 — 임의 문자열이 DB에 저장됨")
                .isNotNull();
        assertThat(patternAnnotation.regexp())
                .as("VIEW, NAVIGATE, FAVORITE 세 값만 허용해야 함")
                .contains("VIEW")
                .contains("NAVIGATE")
                .contains("FAVORITE");
    }
}
