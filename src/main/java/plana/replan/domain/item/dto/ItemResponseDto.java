package plana.replan.domain.item.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import plana.replan.domain.routine.dto.RoutineResponseDto;
import plana.replan.domain.todo.dto.TodoListResponseDto;

@Schema(description = "통합 아이템 목록 응답 (투두 + 루틴 회차). 목록은 카드 표시·조작에 필요한 최소 정보만, 나머지는 상세 API 참고")
public record ItemResponseDto(
    @Schema(description = "아이템 종류 (TODO / ROUTINE)", example = "TODO") ItemKind kind,
    @Schema(description = "투두 ID. TODO면 항상 존재, ROUTINE이면 그날 투두가 이미 생성된 경우에만 존재", example = "42")
        Long todoId,
    @Schema(description = "루틴 ID. ROUTINE일 때만 존재", example = "7") Long routineId,
    @Schema(description = "날짜. ROUTINE=회차 날짜(조작 시 주소로 사용), TODO=마감일의 날짜(정보용, 없으면 null)")
        LocalDate date,
    @Schema(description = "제목", example = "영어 단어 외우기") String title,
    @Schema(
            description = "마감 일시. TODO=본인 마감(없으면 null), ROUTINE=그날의 실제 마감일시",
            example = "2026-07-10T08:00:00")
        LocalDateTime dueDate,
    @Schema(description = "반복 유형 (DAILY/WEEKLY/MONTHLY). 반복 아니면 null", example = "WEEKLY")
        String routineType,
    @Schema(description = "태그 ID. 없으면 null", example = "3") Long tagId,
    @Schema(description = "태그 제목. 없으면 null", example = "영어") String tagTitle,
    @Schema(description = "태그 색상. 없으면 null", example = "BLUE") String tagColor,
    @Schema(description = "정렬 순서 (작을수록 위)", example = "10000.0") double sortOrder,
    @JsonProperty("isPinned") @Schema(description = "핀 여부") boolean isPinned,
    @JsonProperty("isCompleted") @Schema(description = "완료 여부") boolean isCompleted,
    @JsonProperty("isOverdue") @Schema(description = "기한 초과 여부") boolean isOverdue) {

  public static ItemResponseDto fromTodo(TodoListResponseDto todo) {
    return new ItemResponseDto(
        ItemKind.TODO,
        todo.getTodoId(),
        null,
        todo.getDueDate() != null ? todo.getDueDate().toLocalDate() : null,
        todo.getTitle(),
        todo.getDueDate(),
        todo.getRoutineType(),
        todo.getTagId(),
        todo.getTagTitle(),
        todo.getTagColor(),
        todo.getSortOrder(),
        todo.isPinned(),
        todo.isCompleted(),
        todo.isOverdue());
  }

  public static ItemResponseDto fromRoutine(RoutineResponseDto routine, LocalDate date) {
    return new ItemResponseDto(
        ItemKind.ROUTINE,
        routine.getTodoId(),
        routine.getRoutineId(),
        date,
        routine.getTitle(),
        routine.getDueDate(),
        routine.getRoutineType() != null ? routine.getRoutineType().name() : null,
        routine.getTagId(),
        routine.getTagTitle(),
        routine.getTagColor(),
        routine.getSortOrder(),
        routine.isPinned(),
        routine.isCompleted(),
        routine.isOverdue());
  }
}
