package plana.replan.domain.todo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "하위 투두 수정 요청")
public class SubTodoUpdateRequestDto {

  @Schema(
      description = "하위 투두 제목",
      example = "개념 정리하기 (수정)",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = "제목은 필수입니다.")
  private String title;
}
