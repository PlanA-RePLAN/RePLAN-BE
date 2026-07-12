package plana.replan.domain.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(
    description =
        """
        예약된 하위 투두 제목 수정 요청 (행이 아직 없는 회차 전용).
        행이 있는 날짜의 하위 투두는 기존 투두 API(PUT /api/todos/{parentId}/sub-todos/{subTodoId})를 사용한다.
        index는 상세 응답 subTodos의 reservedIndex 값.
        """)
public record ItemSubTodoUpdateRequestDto(
    @Schema(description = "루틴 ID", example = "7") @NotNull Long routineId,
    @Schema(description = "회차 날짜", example = "2026-07-20") @NotNull LocalDate date,
    @Schema(description = "예약 배열 위치 (상세 응답의 reservedIndex)", example = "0") @NotNull Integer index,
    @Schema(description = "새 제목", example = "단어 100개 외우기") @NotBlank String title) {}
