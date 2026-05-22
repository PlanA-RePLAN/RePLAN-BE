package plana.replan.domain.todo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import plana.replan.domain.routine.entity.RoutineType;

@Schema(description = "투두 수정 요청")
@Getter
@NoArgsConstructor
public class TodoUpdateRequestDto {

  @NotBlank
  @Schema(
      description = "투두 제목",
      example = "토익 단어 50개 외우기",
      requiredMode = Schema.RequiredMode.REQUIRED)
  private String title;

  @Schema(description = "마감 일시 (ISO 8601 형식). null이면 마감일 제거", example = "2025-12-31T23:59:59")
  private LocalDateTime dueDate;

  @Schema(description = "태그 ID. null이면 태그 제거", example = "3")
  private Long tagId;

  @Schema(description = "반복 유형 (DAILY/WEEKLY/MONTHLY). null이면 반복 없음")
  private RoutineType routineType;

  @Schema(description = "반복 날짜 (WEEKLY: 1-127 비트마스크, MONTHLY: 1-31). DAILY는 null", example = "5")
  private Integer routineDate;
}
