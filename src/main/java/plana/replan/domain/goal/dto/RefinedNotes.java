package plana.replan.domain.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "정제된 특이사항 (항목 목록 + AI 근거)")
public record RefinedNotes(
    @Schema(description = "정제된 특이사항 항목 목록") List<NoteItemDto> value,
    @Schema(
            description = "AI 정제 근거",
            example = "교재 정보와 학습 전략을 카테고리별로 구조화하고 투두 생성에 필요한 세부 정보를 보완했습니다.")
        String reason) {}
