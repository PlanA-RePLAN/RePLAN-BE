package plana.replan.domain.tag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(description = "태그 생성 요청")
@Getter
@NoArgsConstructor
public class TagCreateRequestDto {

  @NotBlank(message = "태그 이름은 필수입니다.")
  @Schema(description = "태그 이름", example = "영어", requiredMode = Schema.RequiredMode.REQUIRED)
  private String title;

  @Schema(description = "태그 색상 (hex 코드). 생략 또는 null 시 색상 없음", example = "#3B82F6")
  private String color;
}
