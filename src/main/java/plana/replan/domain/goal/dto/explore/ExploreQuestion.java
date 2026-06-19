package plana.replan.domain.goal.dto.explore;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "AI가 생성한 질문 1개 (질문 + 예시 칩)")
public record ExploreQuestion(
    @Schema(description = "질문 텍스트", example = "현재 영어 실력") String question,
    @Schema(description = "사용자가 바로 누를 수 있는 예시 답변 칩 목록")
        List<String> chips) {}
