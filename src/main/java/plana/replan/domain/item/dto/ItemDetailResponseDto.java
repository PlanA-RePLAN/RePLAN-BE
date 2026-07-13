package plana.replan.domain.item.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
    @Schema(description = "마감 일시. TODO=본인 마감(없으면 null), ROUTINE=그날의 실제 마감일시(회차 예외 시간 반영)")
        LocalDateTime dueDate,
    @JsonProperty("isCompleted") @Schema(description = "완료 여부") boolean isCompleted,
    @JsonProperty("isPinned") @Schema(description = "핀 여부. TODO 상세에서는 제공하지 않음(null)")
        Boolean isPinned,
    @JsonProperty("isSkipped") @Schema(description = "건너뜀 여부. ROUTINE만") boolean isSkipped,
    @JsonProperty("hasOverride") @Schema(description = "override 존재 여부. ROUTINE만")
        boolean hasOverride,
    @Schema(description = "태그 ID (ROUTINE이면 override 적용값)") Long tagId,
    @Schema(description = "태그 제목") String tagTitle,
    @Schema(description = "태그 색상") String tagColor,
    @Schema(description = "반복 유형 (DAILY/WEEKLY/MONTHLY). 반복 아니면 null") String routineType,
    @Schema(description = "반복 날짜 배열. WEEKLY=요일 인덱스(월0…일6), MONTHLY=일자(1~31), 아니면 null")
        List<Integer> routineDays,
    @Schema(description = "루틴 기본 반복시간. ROUTINE만, 설정 안 했으면 null", example = "09:00:00")
        LocalTime routineTime,
    @Schema(description = "반복 종료일. ROUTINE만") LocalDateTime repeatEndDate,
    @Schema(description = "하위 아이템 목록 (하위 투두)") List<SubItemDto> subItems) {

  @Schema(description = "하위 아이템")
  public record SubItemDto(
      @Schema(description = "하위 투두 ID. 아직 행이 없는 회차의 하위(예정분·예약분)는 null") Long todoId,
      @Schema(description = "제목") String title,
      @JsonProperty("isCompleted") @Schema(description = "완료 여부") boolean isCompleted,
      @Schema(description = "예약 하위의 배열 위치 (수정/삭제 시 지목용). 예약 하위가 아니면 null") Integer reservedIndex,
      @Schema(description = "하위 루틴 ID (반복 전체 수정/삭제 시 지목용). 하위 루틴과 무관한 하위면 null", example = "11")
          Long subRoutineId) {

    public static SubItemDto from(TodoDetailResponseDto.SubTodoDto sub) {
      return new SubItemDto(
          sub.getTodoId(), sub.getTitle(), sub.isCompleted(), null, sub.getSubRoutineId());
    }

    /** 하위 루틴 예정분 — 그날이 되면 배치가 만들 하위. 이번만 조작은 불가, subRoutineId로 반복 전체 수정/삭제만 가능. */
    public static SubItemDto plannedFromChildRoutine(Long subRoutineId, String title) {
      return new SubItemDto(null, title, false, null, subRoutineId);
    }

    /** 회차 예외에 예약된 하위 — index로 완료/수정/삭제한다. */
    public static SubItemDto reserved(String title, boolean isCompleted, int index) {
      return new SubItemDto(null, title, isCompleted, index, null);
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
        null,
        null,
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
        override.overrideDate().atTime(override.effectiveTime()),
        override.isCompleted(),
        override.isPinned(),
        override.isSkipped(),
        override.hasOverride(),
        override.effectiveTagId(),
        override.effectiveTagTitle(),
        override.effectiveTagColor(),
        override.routineType(),
        override.routineDays(),
        override.routineTime(),
        override.repeatEndDate(),
        subItems);
  }
}
