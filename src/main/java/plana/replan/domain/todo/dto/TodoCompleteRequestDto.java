package plana.replan.domain.todo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "투두 완료/미완료 처리 요청")
@Getter
@NoArgsConstructor
public class TodoCompleteRequestDto {

  @NotNull
  @Schema(description = "완료 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
  private Boolean isCompleted;
}
