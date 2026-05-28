package plana.replan.domain.goal.dto.recommend;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "AI 투두 추천 결과")
public record TodoRecommendationResponse(
    @Schema(description = "전체 추천에 대한 총평") String overallReason,
    @Schema(description = "추천 투두 목록") List<RecommendedTodo> todos) {}
