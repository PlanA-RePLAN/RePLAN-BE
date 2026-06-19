package plana.replan.domain.goal.dto.refine;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "AI 목표 정제 요청 (탐색에서 받은 질문/답변을 함께 전달)")
public record GoalRefinementRequest(
    @Schema(
            description = "목표 (자연어)",
            example = "토익 850점 이상 달성",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "목표는 필수입니다.")
        String goal,
    @Schema(description = "종료 날짜 (yyyy-MM-dd 형식). 선택", example = "2026-05-01") String deadlineDate,
    @Schema(description = "종료 시간 (HH:mm 형식). 선택", example = "23:59") String deadlineTime,
    @Schema(description = "탐색에서 받은 질문과 사용자 답변 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "질문/답변은 최소 1개 이상이어야 합니다.")
        @Valid
        List<QuestionAnswer> answers) {}
