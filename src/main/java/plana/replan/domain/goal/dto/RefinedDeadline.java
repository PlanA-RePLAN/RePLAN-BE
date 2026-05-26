package plana.replan.domain.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "정제된 마감기한 (날짜·시간 각각 nullable)")
public record RefinedDeadline(
    @Schema(description = "마감 날짜 (yyyy-MM-dd). 기한 없음이면 null", example = "2025-08-25") String date,
    @Schema(description = "마감 시간 (HH:mm). 시간 미설정이면 null", example = "08:00") String time,
    @Schema(description = "AI 정제 근거", example = "오늘부터 3개월, 마지막 1주는 점검 기간으로 배정했습니다.")
        String reason) {}
