package plana.replan.domain.goal.dto.refine;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "AI 목표 정제 결과")
public record GoalRefinementResponse(
    @Schema(description = "정제된 목표") RefinedField goal,
    @Schema(description = "정제된 종료일정") RefinedDeadline deadline,
    @Schema(description = "질문별 정제 솔루션 목록") List<RefinedSolution> solutions) {}
