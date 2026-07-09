package plana.replan.domain.item.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import plana.replan.domain.routine.dto.RoutineOverrideResponseDto;
import plana.replan.domain.todo.dto.TodoDetailResponseDto;

@Schema(description = "통합 아이템 상세 응답")
public record ItemDetailResponseDto(
    @Schema(description = "아이템 종류 (TODO / ROUTINE)") ItemKind kind,
    @Schema(description = "투두 ID. ROUTINE이면 그날 투두가 생성된 경우에만 존재") Long todoId,
    @Schema(description = "루틴 ID. ROUTINE일 때만") Long routineId,
    @Schema(description = "날짜. ROUTINE=회차 날짜, TODO=마감일의 날짜") LocalDate date,
    @Schema(description = "제목 (ROUTINE이면 override 적용값)") String title,
    @Schema(description = "마감 일시. TODO만, 없으면 null") LocalDateTime dueDate,
    @JsonProperty("isCompleted") @Schema(description = "완료 여부") boolean isCompleted,
    @JsonProperty("isPinned") @Schema(description = "핀 여부. TODO 상세에서는 제공하지 않음(null)")
        Boolean isPinned,
    @JsonProperty("isSkipped") @Schema(description = "건너뜀 여부. ROUTINE만") boolean isSkipped,
    @JsonProperty("hasOverride") @Schema(description = "override 존재 여부. ROUTINE만")
        boolean hasOverride,
    @Schema(description = "태그 ID (ROUTINE이면 override 적용값)") Long tagId,
    @Schema(description = "태그 제목") String tagTitle,
    @Schema(description = "태그 색상") String tagColor,
    @Schema(description = "반복 유형. TODO 상세에서만 제공") String routineType,
    @Schema(description = "반복 날짜 배열. TODO 상세에서만 제공") List<Integer> routineDays,
    @Schema(description = "하위 아이템 목록 (하위 투두)") List<SubItemDto> subItems) {

  @Schema(description = "하위 아이템")
  public record SubItemDto(
      @Schema(description = "하위 투두 ID") Long todoId,
      @Schema(description = "제목") String title,
      @JsonProperty("isCompleted") @Schema(description = "완료 여부") boolean isCompleted) {

    public static SubItemDto from(TodoDetailResponseDto.SubTodoDto sub) {
      return new SubItemDto(sub.getTodoId(), sub.getTitle(), sub.isCompleted());
    }
  }

  public static ItemDetailResponseDto fromTodo(TodoDetailResponseDto todo) {
    return new ItemDetailResponseDto(
        ItemKind.TODO,
        todo.getTodoId(),
        null,
        todo.getDueDate() != null ? todo.getDueDate().toLocalDate() : null,
        todo.getTitle(),
        todo.getDueDate(),
        todo.isCompleted(),
        null,
        false,
        false,
        todo.getTagId(),
        todo.getTagTitle(),
        todo.getTagColor(),
        todo.getRoutineType(),
        todo.getRoutineDays(),
        todo.getSubTodos().stream().map(SubItemDto::from).toList());
  }

  public static ItemDetailResponseDto fromRoutine(
      RoutineOverrideResponseDto override, List<SubItemDto> subItems) {
    return new ItemDetailResponseDto(
        ItemKind.ROUTINE,
        override.todoId(),
        override.routineId(),
        override.overrideDate(),
        override.effectiveTitle(),
        null,
        override.isCompleted(),
        override.isPinned(),
        override.isSkipped(),
        override.hasOverride(),
        override.effectiveTagId(),
        override.effectiveTagTitle(),
        override.effectiveTagColor(),
        null,
        null,
        subItems);
  }
}
