package plana.replan.domain.todo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import plana.replan.domain.routine.entity.RoutineType;

@Schema(description = "투두 수정 요청")
@Getter
@NoArgsConstructor
public class TodoUpdateRequestDto {

  @Schema(description = "투두 제목. null이면 변경하지 않음. 빈 문자열은 허용하지 않음", example = "토익 단어 50개 외우기")
  private String title;

  @Schema(description = "마감 일시 (ISO 8601 형식). null이면 마감일 제거", example = "2025-12-31T23:59:59")
  private LocalDateTime dueDate;

  @Schema(description = "태그 ID. null이면 태그 제거", example = "3")
  private Long tagId;

  @Schema(description = "반복 유형 (DAILY/WEEKLY/MONTHLY). null이면 반복 없음")
  private RoutineType routineType;

  @Schema(
      description = "반복 날짜 (WEEKLY: 1-127 비트마스크, MONTHLY: 일자 비트마스크). DAILY는 null",
      example = "5")
  private Integer routineDate;

  @Schema(description = "반복 시각 (HH:mm:ss). 반복되는 날의 마감 시각. 생략 시 23:59:59로 처리", example = "09:00:00")
  private LocalTime routineTime;
}
