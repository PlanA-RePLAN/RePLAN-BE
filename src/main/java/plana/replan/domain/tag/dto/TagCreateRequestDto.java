package plana.replan.domain.tag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import plana.replan.domain.tag.entity.TagColor;

@Schema(description = "태그 생성 요청")
@Getter
@NoArgsConstructor
public class TagCreateRequestDto {

  @NotBlank
  @Schema(description = "태그 이름", example = "영어", requiredMode = Schema.RequiredMode.REQUIRED)
  private String title;

  @Schema(description = "태그 색상", example = "BLUE")
  private TagColor color;
}
