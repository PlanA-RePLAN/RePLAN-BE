package plana.replan.domain.monthlyreport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "팁노트 반영 요청 — 체크한 카드 ID 목록")
public record TipNoteApplyRequest(
    @Schema(
            description = "반영할 추천 카드 ID 목록 (조회 응답의 items[].id)",
            example = "[3, 5]",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty(message = "반영할 카드를 1개 이상 선택해야 합니다.")
        List<Long> itemIds) {}
