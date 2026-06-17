package plana.replan.domain.replan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "리플랜 수락 저장 요청")
public record ReplanSaveRequest(
    @Schema(description = "실패한(앵커) 투두 ID", example = "42",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "앵커 투두 ID는 필수입니다.") Long anchorTodoId,
    @Schema(description = "선택한 실패 이유 코드 목록(최대 3)", example = "[\"GOAL_NO_PRIORITY\"]")
        List<String> reasonCodes,
    @Schema(description = "사용자가 수락한 작업 목록. 비어있으면(추가 없이 끝내기) 실패사유만 저장")
        List<ReplanOperation> acceptedOperations) {}
