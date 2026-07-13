package plana.replan.domain.todo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.routine.util.RoutineDays;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.todo.entity.Todo;

@Schema(description = "투두 상세 조회 응답")
@Getter
@AllArgsConstructor
public class TodoDetailResponseDto {

  @Schema(description = "투두 ID", example = "1")
  private Long todoId;

  @Schema(description = "투두 제목", example = "토익 단어 50개 외우기")
  private String title;

  @Schema(description = "마감 일시 (ISO 8601 형식)", example = "2025-12-31T23:59:59")
  private LocalDateTime dueDate;

  @JsonProperty("isCompleted")
  @Schema(description = "완료 여부", example = "false")
  private boolean completed;

  @Schema(description = "태그 ID (없으면 null)", example = "3")
  private Long tagId;

  @Schema(description = "태그 제목 (없으면 null)", example = "영어")
  private String tagTitle;

  @Schema(description = "태그 색상 (없으면 null)", example = "BLUE")
  private String tagColor;

  @Schema(description = "반복 유형 (DAILY/WEEKLY/MONTHLY, 반복 아니면 null)", example = "DAILY")
  private String routineType;

  @Schema(
      description = "반복 날짜 배열 (WEEKLY: 요일 인덱스 월0…일6, MONTHLY: 일자 1~31, DAILY 또는 반복 없으면 null)",
      example = "[0, 2, 4]")
  private List<Integer> routineDays;

  @Schema(description = "하위 투두 목록")
  private List<SubTodoDto> subTodos;

  @Schema(description = "하위 투두 정보")
  @Getter
  @AllArgsConstructor
  public static class SubTodoDto {

    @Schema(description = "하위 투두 ID", example = "10")
    private Long todoId;

    @Schema(description = "하위 투두 제목", example = "단어장 챕터 1 읽기")
    private String title;

    @JsonProperty("isCompleted")
    @Schema(description = "완료 여부", example = "false")
    private boolean completed;

    @Schema(description = "하위 루틴 ID. 하위 루틴이 찍어낸 하위 투두일 때만, 아니면 null", example = "11")
    private Long subRoutineId;

    public static SubTodoDto from(Todo todo) {
      return new SubTodoDto(
          todo.getId(),
          todo.getTitle(),
          todo.isCompleted(),
          todo.getRoutine() != null ? todo.getRoutine().getId() : null);
    }
  }

  public static TodoDetailResponseDto from(Todo todo) {
    Tag tag = todo.getTag();
    Routine routine = todo.getRoutine();
    List<SubTodoDto> subTodos = todo.getChildren().stream().map(SubTodoDto::from).toList();
    return new TodoDetailResponseDto(
        todo.getId(),
        todo.getTitle(),
        todo.getDueDate(),
        todo.isCompleted(),
        tag != null ? tag.getId() : null,
        tag != null ? tag.getTitle() : null,
        tag != null ? tag.getColor() : null,
        routine != null && routine.getRoutineType() != null
            ? routine.getRoutineType().name()
            : null,
        routine != null
            ? RoutineDays.toDays(routine.getRoutineType(), routine.getRoutineDate())
            : null,
        subTodos);
  }
}
