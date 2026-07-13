package plana.replan.domain.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

@Schema(
    description =
        """
        통합 아이템 하위 투두 제목 수정 요청. 아래 세 가지 지목 방법 중 정확히 하나를 사용한다.
        - 행 하위 (그날만): parentTodoId + subTodoId
        - 예약 하위 (그날만, 행이 아직 없는 회차): routineId + date + index (상세 응답 subTodos의 reservedIndex)
        - 하위 루틴 (반복 전체): subRoutineId (상세 응답 subTodos의 subRoutineId)
        """)
public record ItemSubTodoUpdateRequestDto(
    @Schema(description = "부모 투두 ID (행 하위 지목 시 필수)", example = "42") Long parentTodoId,
    @Schema(description = "하위 투두 ID (행 하위 지목 시 필수)", example = "128") Long subTodoId,
    @Schema(description = "루틴 ID (예약 하위 지목 시 필수)", example = "7") Long routineId,
    @Schema(description = "회차 날짜 (예약 하위 지목 시 필수)", example = "2026-07-20") LocalDate date,
    @Schema(description = "예약 배열 위치 (예약 하위 지목 시 필수, 상세 응답의 reservedIndex)", example = "0")
        Integer index,
    @Schema(description = "하위 루틴 ID (반복 전체 지목 시 필수, 상세 응답의 subRoutineId)", example = "11")
        Long subRoutineId,
    @Schema(description = "새 제목", example = "단어 100개 외우기") @NotBlank String title) {}
