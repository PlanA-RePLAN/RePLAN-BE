package plana.replan.domain.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "정제된 필드 (값 + AI 근거)")
public record RefinedField(
    @Schema(description = "정제된 값", example = "토익 900점 달성 (LC 450·RC 450 이상)") String value,
    @Schema(description = "AI 정제 근거", example = "섹션별 목표를 명시해 학습 방향을 명확히 했습니다.") String reason) {}
