package plana.replan.domain.goal.dto.create;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import plana.replan.domain.routine.entity.RoutineType;

@Schema(description = "투두 항목 요청")
public record TodoItemRequest(
    @Schema(description = "투두 유형 (ONE_TIME / RECURRING)", example = "ONE_TIME") String type,
    @Schema(description = "투두 제목", example = "단어 암기") String title,
    @Schema(
            description = "마감 날짜 (yyyy-MM-dd 형식). ONE_TIME만 사용. RECURRING이면 null.",
            example = "2025-06-01")
        String dueDate,
    @Schema(description = "마감 시간 (HH:mm 형식). ONE_TIME만 사용. RECURRING이면 null.", example = "09:00")
        String dueTime,
    @Schema(description = "반복 유형. RECURRING만 사용. ONE_TIME이면 null.", example = "DAILY")
        RoutineType routineType,
    @Schema(
            description =
                "반복 날짜. RECURRING만 사용. ONE_TIME이면 null. WEEKLY: 요일 bitmask (월=1, 화=2, 수=4, 목=8, 금=16, 토=32, 일=64). MONTHLY: 일자(1~31). DAILY: null.",
            example = "null")
        Integer routineDate,
    @Schema(description = "태그 ID. 없으면 null.", example = "1") Long tagId,
    @Schema(description = "하위 투두 제목 목록. ONE_TIME만 사용 가능.", example = "[\"챕터 1\", \"챕터 2\"]")
        List<String> subTodos) {}
