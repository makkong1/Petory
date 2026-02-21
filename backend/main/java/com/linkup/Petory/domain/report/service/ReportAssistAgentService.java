package com.linkup.Petory.domain.report.service;

import java.util.Optional;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkup.Petory.domain.report.dto.ReportAssistSuggestion;
import com.linkup.Petory.domain.report.dto.ReportDetailDTO;
import com.linkup.Petory.domain.report.entity.ReportActionType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 관리자 신고 보조 에이전트 (Ollama 연동).
 * 신고 상세 정보를 LLM에 넘겨 요약·심각도·조치 제안을 받는다. 자동 처리는 하지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportAssistAgentService {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        너는 커뮤니티 신고 검토를 돕는 보조 AI다. 다음 신고 정보를 보고 JSON만 한 줄로 답해라.
        반드시 아래 키만 사용: summary, suggestedSeverity, suggestedAction, reasoning
        
        - summary: 신고 내용 한 줄 요약 (한국어)
        - suggestedSeverity: LOW, MEDIUM, HIGH 중 하나
        - suggestedAction: NONE, DELETE_CONTENT, WARN_USER, SUSPEND_USER, OTHER 중 하나
        - reasoning: 제안 이유 1~2문장 (한국어)
        
        다른 말 없이 JSON만 출력해라.
        """;

    /**
     * 신고 상세를 바탕으로 AI 제안을 생성한다. LLM 실패 시 빈 Optional 반환.
     */
    public Optional<ReportAssistSuggestion> getAssistSuggestions(ReportDetailDTO detail) {
        if (detail == null || detail.getReport() == null) {
            return Optional.empty();
        }

        String userMessage = buildUserMessage(detail);
        try {
            log.warn("[AI보조] Ollama 호출 시작 reportId={}", detail.getReport().getIdx());
            String response = chatModel.call(new Prompt(SYSTEM_PROMPT + "\n\n" + userMessage))
                    .getResult()
                    .getOutput()
                    .getText();

            if (response == null || response.isBlank()) {
                log.warn("[AI보조] Ollama 응답이 비어 있음. 모델이 아무것도 반환하지 않았을 수 있음.");
                return Optional.empty();
            }
            log.debug("[AI보조] Ollama 원문 응답 길이={}", response.length());

            return parseResponse(response);
        } catch (Exception e) {
            log.error("[AI보조] 호출 실패 " + e.getClass().getSimpleName() + ": " + e.getMessage() + " (Ollama 실행·모델명 확인)", e);
            return Optional.empty();
        }
    }

    private String buildUserMessage(ReportDetailDTO detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("[신고 정보]\n");
        sb.append("대상 타입: ").append(detail.getReport().getTargetType()).append("\n");
        sb.append("신고 사유: ").append(detail.getReport().getReason()).append("\n");
        if (detail.getTarget() != null) {
            if (detail.getTarget().getTitle() != null) {
                sb.append("대상 제목: ").append(detail.getTarget().getTitle()).append("\n");
            }
            if (detail.getTarget().getSummary() != null) {
                sb.append("대상 내용 요약: ").append(detail.getTarget().getSummary()).append("\n");
            }
            if (detail.getTarget().getAuthorName() != null) {
                sb.append("작성자: ").append(detail.getTarget().getAuthorName()).append("\n");
            }
        }
        sb.append("\n위 내용을 바탕으로 JSON만 출력해라.");
        return sb.toString();
    }

    private Optional<ReportAssistSuggestion> parseResponse(String response) {
        if (response == null || response.isBlank()) {
            return Optional.empty();
        }
        try {
            // LLM이 마크다운 코드블록으로 감쌌을 수 있음
            String json = response.trim();
            if (json.contains("```")) {
                int start = json.indexOf('{');
                int end = json.lastIndexOf('}') + 1;
                if (start >= 0 && end > start) {
                    json = json.substring(start, end);
                }
            }

            JsonNode root = objectMapper.readTree(json);
            String summary = text(root, "summary");
            String suggestedSeverity = text(root, "suggestedSeverity");
            String suggestedActionStr = text(root, "suggestedAction");
            String reasoning = text(root, "reasoning");

            ReportActionType action = parseActionType(suggestedActionStr);

            return Optional.of(ReportAssistSuggestion.builder()
                    .summary(summary)
                    .suggestedSeverity(suggestedSeverity != null ? suggestedSeverity.toUpperCase() : null)
                    .suggestedAction(action)
                    .reasoning(reasoning)
                    .build());
        } catch (Exception e) {
            String preview = response.length() > 500 ? response.substring(0, 500) + "..." : response;
            log.warn("[AI보조] JSON 파싱 실패 - {}. 응답 일부: {}", e.getMessage(), preview);
            return Optional.empty();
        }
    }

    private String text(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return v != null && v.isTextual() ? v.asText().trim() : null;
    }

    private ReportActionType parseActionType(String value) {
        if (value == null || value.isBlank()) {
            return ReportActionType.NONE;
        }
        try {
            return ReportActionType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ReportActionType.NONE;
        }
    }
}
