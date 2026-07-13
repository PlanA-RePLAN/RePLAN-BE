package plana.replan.domain.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Schema(
    description =
        """
        통합 아이템 하위 투두 추가 요청.
        - kind=TODO: todoId 필수. 그 투두에 하위 투두를 추가
        - kind=ROUTINE + scope=THIS: routineId+date 필수. 그 날짜 회차에만 추가
          (그날 행(Todo)이 이미 있으면 행에 바로 생성, 아직 없으면(미래 회차) 회차 예외에 예약해 뒀다가 배치가 행을 만들 때 실체화)
        - kind=ROUTINE + scope=ALL: routineId 필수. 하위 루틴을 생성해 모든 회차에 반복
        """)
public record ItemSubTodoCreateRequestDto(
    @Schema(description = "아이템 종류", example = "ROUTINE") @NotNull ItemKind kind,
    @Schema(description = "투두 ID (kind=TODO일 때 필수)", example = "42") Long todoId,
    @Schema(description = "루틴 ID (kind=ROUTINE일 때 필수)", example = "7") Long routineId,
    @Schema(description = "회차 날짜 (kind=ROUTINE + scope=THIS일 때 필수)", example = "2026-07-20")
        LocalDate date,
    @Schema(description = "적용 범위. kind=ROUTINE일 때 필수 (THIS=그 날짜 회차만 / ALL=하위 루틴 생성)")
        ItemScope scope,
    @Schema(description = "하위 투두 제목", example = "단어 50개 외우기") @NotBlank String title) {}
