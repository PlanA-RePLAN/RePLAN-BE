package plana.replan.domain.goal.dto.recommend;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "AI 추천 투두 항목")
public record RecommendedTodo(
    @Schema(
            description = "투두 유형. ONE_TIME: 일회형, RECURRING: 반복형",
            example = "RECURRING",
            allowableValues = {"ONE_TIME", "RECURRING"})
        String type,
    @Schema(description = "투두 제목", example = "해커스 보카 30단어 암기") String title,
    @Schema(
            description = "마감 날짜 (yyyy-MM-dd 형식). ONE_TIME=마감일, RECURRING=반복 종료일. 없으면 null",
            example = "2025-08-25",
            nullable = true)
        String dueDate,
    @Schema(
            description = "마감 시간 (HH:mm 형식). ONE_TIME=마감 시각, RECURRING=반복 종료일의 시각. 없으면 null",
            example = "08:00",
            nullable = true)
        String dueTime,
    @Schema(
            description = "반복 유형 (RECURRING만 사용). DAILY·WEEKLY·MONTHLY",
            example = "WEEKLY",
            nullable = true,
            allowableValues = {"DAILY", "WEEKLY", "MONTHLY"})
        String routineType,
    @Schema(
            description =
                "반복 날짜 배열 (RECURRING만 사용). WEEKLY: 요일 인덱스(월=0 … 일=6). MONTHLY: 일자(1~31). DAILY: null.",
            example = "[0, 2, 4]",
            nullable = true)
        List<Integer> routineDays,
    @Schema(
            description = "매 회차 수행 시각(반복시간, HH:mm 형식). RECURRING만 사용. 없으면 null",
            example = "09:00",
            nullable = true)
        String routineTime,
    @Schema(
            description = "AI가 이 투두에 배정한 태그 ID. 유저의 실제 태그 중에서 선택되며, 마땅한 태그가 없으면 null",
            example = "1",
            nullable = true)
        Long tagId,
    @Schema(description = "배정된 태그 이름. tagId가 null이면 함께 null", example = "Study", nullable = true)
        String tagName) {}
