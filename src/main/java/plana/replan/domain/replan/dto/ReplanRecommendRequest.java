package plana.replan.domain.replan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "리플랜 추천 요청")
public record ReplanRecommendRequest(
    @Schema(
            description = "실패한(앵커) 투두 ID",
            example = "42",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "앵커 투두 ID는 필수입니다.")
        Long anchorTodoId,
    @Schema(description = "선택한 실패 이유 코드 목록(최대 3)", example = "[\"GOAL_NO_PRIORITY\"]")
        List<String> reasonCodes,
    @Schema(description = "추가 질문 답변 목록", nullable = true) List<ReplanAnswer> answers,
    @Schema(
            description = "새로고침 횟수(0~3). 0 또는 생략은 첫 추천, 1~3은 다시 추천(회차별 스타일 적용)",
            example = "0",
            nullable = true)
        Integer refreshCount) {}
