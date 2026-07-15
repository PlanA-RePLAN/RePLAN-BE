package plana.replan.domain.monthlyreport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "팁노트 반영 결과 — 실제로 투두/루틴에 반영된 카드 목록 (\"내 Todo 리스트에 추가했어요\" 화면용)")
public record TipNoteApplyResponse(
    @Schema(description = "반영된 카드 목록") List<TipNoteResponse.TipNoteItemResponse> appliedItems) {}
