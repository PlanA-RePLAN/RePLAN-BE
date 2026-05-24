package plana.replan.domain.todo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import plana.replan.domain.routine.entity.Routine;
import plana.replan.domain.tag.entity.Tag;
import plana.replan.domain.todo.entity.Todo;

@Schema(description = "투두 목록 조회 응답")
@Getter
@AllArgsConstructor
public class TodoListResponseDto {

  @Schema(description = "투두 ID", example = "1")
  private Long todoId;

  @Schema(description = "투두 제목", example = "토익 단어 50개 외우기")
  private String title;

  @Schema(description = "마감 일시 (ISO 8601 형식)", example = "2025-12-31T23:59:59")
  private LocalDateTime dueDate;

  @JsonProperty("isPinned")
  @Schema(description = "핀 여부", example = "false")
  private boolean isPinned;

  @Schema(description = "정렬 순서", example = "10000.0")
  private double sortOrder;

  @JsonProperty("isCompleted")
  @Schema(description = "완료 여부", example = "false")
  private boolean isCompleted;

  @Schema(description = "태그 ID (없으면 null)", example = "3")
  private Long tagId;

  @Schema(description = "태그 제목 (없으면 null)", example = "영어")
  private String tagTitle;

  @Schema(description = "태그 색상 (없으면 null)", example = "BLUE")
  private String tagColor;

  @Schema(description = "반복 유형 (DAILY/WEEKLY/MONTHLY, 반복 아니면 null)", example = "DAILY")
  private String routineType;

  @JsonProperty("isOverdue")
  @Schema(description = "기한 초과 여부 (미완료이고 dueDate가 현재 시각 이전인 경우 true)", example = "false")
  private boolean isOverdue;

  public static TodoListResponseDto from(Todo todo, Clock clock) {
    Tag tag = todo.getTag();
    Routine routine = todo.getRoutine();
    boolean overdue =
        !todo.isCompleted()
            && todo.getDueDate() != null
            && todo.getDueDate().isBefore(LocalDateTime.now(clock));
    return new TodoListResponseDto(
        todo.getId(),
        todo.getTitle(),
        todo.getDueDate(),
        todo.isPinned(),
        todo.getSortOrder(),
        todo.isCompleted(),
        tag != null ? tag.getId() : null,
        tag != null ? tag.getTitle() : null,
        tag != null && tag.getColor() != null ? tag.getColor().name() : null,
        routine != null && routine.getRoutineType() != null
            ? routine.getRoutineType().name()
            : null,
        overdue);
  }
}
