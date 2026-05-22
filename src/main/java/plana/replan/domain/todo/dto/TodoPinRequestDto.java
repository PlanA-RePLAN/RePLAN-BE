package plana.replan.domain.todo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "투두 핀 설정 요청")
@Getter
@NoArgsConstructor
public class TodoPinRequestDto {

  @NotNull
  @Schema(description = "핀 여부", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
  private Boolean isPinned;
}
