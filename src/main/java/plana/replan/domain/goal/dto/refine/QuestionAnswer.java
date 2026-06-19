package plana.replan.domain.goal.dto.refine;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "탐색 단계에서 받은 질문과 사용자의 답변 한 쌍")
public record QuestionAnswer(
    @Schema(
            description = "탐색에서 받은 질문",
            example = "현재 영어 실력",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "질문은 필수입니다.")
        String question,
    @Schema(description = "사용자 답변. 빈 값일 수 있음", example = "토익 600점대, RC 취약") String answer) {}
