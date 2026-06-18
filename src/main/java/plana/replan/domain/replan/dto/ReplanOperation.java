package plana.replan.domain.replan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "추천 작업 1건")
public record ReplanOperation(
    @Schema(description = "작업 종류", example = "MODIFY_TODO") ReplanAction action,
    @Schema(description = "수정 대상 투두 ID. ADD/CREATE_ROUTINE이면 null", nullable = true, example = "42")
        Long targetTodoId,
    @Schema(description = "투두/루틴 제목", example = "데이터 분석 1~2강 수강") String title,
    @Schema(description = "마감 날짜 yyyy-MM-dd. 없으면 null", nullable = true, example = "2026-06-08")
        String dueDate,
    @Schema(description = "마감 시간 HH:mm. 없으면 null", nullable = true, example = "23:59")
        String dueTime,
    @Schema(description = "태그 ID. 없으면 null", nullable = true, example = "5") Long tagId,
    @Schema(
            description = "반복 유형 DAILY/WEEKLY/MONTHLY. 반복 아니면 null",
            nullable = true,
            example = "WEEKLY")
        String routineType,
    @Schema(
            description = "반복 날짜(WEEKLY=요일 bitmask, MONTHLY=일자). 없으면 null",
            nullable = true,
            example = "62")
        Integer routineDate,
    @Schema(description = "바뀐 필드 목록") List<ChangedField> changedFields) {}
