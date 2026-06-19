package plana.replan.domain.goal.dto.explore;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "AI 목표 탐색 결과")
public record GoalExploreResponse(
    @Schema(description = "달성 가능한 목표인지 여부. false면 questions는 비어있다", example = "true")
        boolean valid,
    @Schema(description = "valid=false일 때 사용자에게 보여줄 안내 메시지. valid=true면 null",
            example = "달성할 수 있는 목표를 입력해주세요.")
        String message,
    @Schema(description = "AI가 생성한 질문 목록 (valid=true일 때 3개)")
        List<ExploreQuestion> questions) {}
