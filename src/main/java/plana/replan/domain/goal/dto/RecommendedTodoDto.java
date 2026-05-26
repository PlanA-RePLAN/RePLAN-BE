package plana.replan.domain.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 추천 투두 항목")
public record RecommendedTodoDto(
    @Schema(
            description = "투두 유형. ONE_TIME: 일회형, RECURRING: 반복형",
            example = "RECURRING",
            allowableValues = {"ONE_TIME", "RECURRING"})
        String type,
    @Schema(description = "투두 제목", example = "해커스 보카 30단어 암기") String title,
    @Schema(
            description = "마감일 (ISO 8601 형식). 없으면 null",
            example = "2025-08-25T00:00:00",
            nullable = true)
        String dueDate,
    @Schema(
            description = "반복 유형 (RECURRING만 사용). DAILY·WEEKLY·MONTHLY",
            example = "WEEKLY",
            nullable = true,
            allowableValues = {"DAILY", "WEEKLY", "MONTHLY"})
        String routineType,
    @Schema(
            description =
                "반복 날짜 (RECURRING만 사용). WEEKLY: 요일 bitmask (월=1,화=2,수=4,목=8,금=16,토=32,일=64). MONTHLY: 일자(1~31). DAILY: null.",
            example = "62",
            nullable = true)
        Integer routineDate) {}
