package plana.replan.domain.goal.dto.refine;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "질문 1개에 대한 정제 결과 (질문 + 항목 목록 + AI 근거)")
public record RefinedSolution(
    @Schema(description = "어떤 질문에 대한 정제인지", example = "현재 수준") String question,
    @Schema(description = "정제된 항목 목록 (제목 + 내용)") List<RefinedNoteItem> items,
    @Schema(description = "AI 정제 근거", example = "현재 실력과 목표 사이 격차를 영역별로 정리했습니다.")
        String reason) {}
