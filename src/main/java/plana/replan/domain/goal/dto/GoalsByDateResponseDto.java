package plana.replan.domain.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.util.List;

@Schema(description = "날짜별 목표 묶음")
public record GoalsByDateResponseDto(
    @Schema(description = "생성 날짜 (yyyy-MM-dd)", example = "2026-05-04") LocalDate date,
    @Schema(description = "해당 날짜에 생성된 목표 목록") List<GoalSingleResponseDto> goals) {}
