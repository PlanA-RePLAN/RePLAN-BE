package plana.replan.domain.goal.dto.create;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import plana.replan.domain.routine.entity.RoutineType;

@Schema(description = "투두 항목 요청")
public record TodoItemRequest(
    @Schema(description = "투두 유형 (ONE_TIME / RECURRING)", example = "ONE_TIME")
        @NotBlank(message = "투두 유형은 필수입니다.")
        String type,
    @Schema(description = "투두 제목", example = "단어 암기") @NotBlank(message = "투두 제목은 필수입니다.")
        String title,
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
                "반복 날짜 배열. RECURRING만 사용. WEEKLY: 요일 인덱스(월=0, 화=1 … 일=6). MONTHLY: 일자(1~31). ONE_TIME: null. DAILY: null 또는 빈 배열([]). 예) 월·수·금=[0,2,4], 매월 3·20일=[3,20].",
            example = "[0, 2, 4]")
        List<Integer> routineDays,
    @Schema(description = "태그 ID. 없으면 null.", example = "1") Long tagId,
    @Schema(description = "하위 투두 제목 목록. ONE_TIME만 사용 가능.", example = "[\"챕터 1\", \"챕터 2\"]")
        List<String> subTodos,
    @Schema(description = "하위 루틴 제목 목록. RECURRING만 사용 가능.", example = "[\"스트레칭\", \"유산소\"]")
        List<String> subRoutines) {}
