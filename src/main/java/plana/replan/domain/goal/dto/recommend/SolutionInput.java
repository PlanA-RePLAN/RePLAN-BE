package plana.replan.domain.goal.dto.recommend;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import plana.replan.domain.goal.dto.refine.RefinedNoteItem;

@Schema(description = "정제·수정이 끝난 최종 솔루션 1개 (질문 + 항목 목록)")
public record SolutionInput(
    @Schema(description = "질문 라벨", example = "현재 수준") String question,
    @Schema(description = "항목 목록 (제목 + 내용)") List<RefinedNoteItem> items) {}
