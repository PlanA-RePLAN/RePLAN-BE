package plana.replan.domain.goal.dto.recommend;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "AI 투두 추천 요청 (정제된 값을 입력 권장)")
public record TodoRecommendationRequestDto(
    @Schema(
            description = "목표",
            example = "토익 900점 달성 (LC 450·RC 450 이상)",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "목표는 필수입니다.")
        String goal,
    @Schema(
            description = "마감기한",
            example = "2025-08-25",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "마감기한은 필수입니다.")
        String deadline,
    @Schema(
            description = "현재 수준",
            example = "토익 600점 (LC 310·RC 290 추정)",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "현재 수준은 필수입니다.")
        String currentLevel,
    @Schema(
            description = "투자 가능 시간",
            example = "평일 1시간·주말 4시간 (주 약 13시간)",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "투자 가능 시간은 필수입니다.")
        String availableTime,
    @Schema(
            description = "특이사항",
            example = "해커스 보카·RC·LC 활용. 주 1회 모의고사. 매주 오답 노트 정리.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "특이사항은 필수입니다.")
        String notes) {}
