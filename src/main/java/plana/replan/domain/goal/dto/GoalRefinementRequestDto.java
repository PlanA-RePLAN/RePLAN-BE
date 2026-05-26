package plana.replan.domain.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Schema(description = "AI 목표 정제 요청")
public record GoalRefinementRequestDto(
    @Schema(
            description = "목표 (자연어)",
            example = "토익 900점",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "목표는 필수입니다.")
        String goal,
    @Schema(
            description = "마감기한 (자연어). 기한 없음도 자연어로 입력 가능",
            example = "3달 안에",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "마감기한은 필수입니다.")
        String deadline,
    @Schema(description = "현재 수준 (자연어, 선택)", example = "현재 600점") String currentLevel,
    @Schema(description = "투자 가능 시간 (자연어, 선택)", example = "하루 1시간") String availableTime,
    @Schema(description = "특이사항 항목 목록 (선택)") List<NoteItemDto> notes) {}
