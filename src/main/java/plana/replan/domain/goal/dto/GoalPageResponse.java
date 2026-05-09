package plana.replan.domain.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "목표 목록 페이지 응답")
public record GoalPageResponse(
    @Schema(description = "목표 목록") List<GoalResponse> goals,
    @Schema(description = "다음 페이지 요청 시 사용할 cursor. null이면 마지막 페이지.", example = "25")
        Long nextCursor,
    @Schema(description = "다음 페이지 존재 여부", example = "true") boolean hasNext) {}
