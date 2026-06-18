package plana.replan.domain.replan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import plana.replan.domain.todo.entity.Todo;

@Schema(description = "리플랜 대상(앵커) 투두의 기존 정보. 질문 화면의 \"기존 투두 수정 사항\" 카드에 표시한다.")
public record ReplanAnchorTodo(
    @Schema(description = "투두 ID", example = "42") Long todoId,
    @Schema(description = "투두 제목", example = "데이터 분석 공부하기") String title,
    @Schema(
            description = "마감 일시 (ISO 8601 형식). 없으면 null",
            nullable = true,
            example = "2026-06-25T11:00:00")
        LocalDateTime dueDate,
    @Schema(description = "태그 ID. 없으면 null", nullable = true, example = "3") Long tagId,
    @Schema(description = "태그 이름. 없으면 null", nullable = true, example = "Study") String tagTitle,
    @Schema(description = "태그 색상. 없으면 null", nullable = true, example = "#FAD7A0") String tagColor,
    @Schema(
            description = "반복 유형 DAILY/WEEKLY/MONTHLY. 반복 투두가 아니면 null",
            nullable = true,
            example = "WEEKLY")
        String routineType) {

  public static ReplanAnchorTodo from(Todo todo) {
    return new ReplanAnchorTodo(
        todo.getId(),
        todo.getTitle(),
        todo.getDueDate(),
        todo.getTag() != null ? todo.getTag().getId() : null,
        todo.getTag() != null ? todo.getTag().getTitle() : null,
        todo.getTag() != null ? todo.getTag().getColor() : null,
        todo.getRoutine() != null ? String.valueOf(todo.getRoutine().getRoutineType()) : null);
  }
}
