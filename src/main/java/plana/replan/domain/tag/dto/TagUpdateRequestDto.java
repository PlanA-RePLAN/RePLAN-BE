package plana.replan.domain.tag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import plana.replan.domain.tag.entity.TagColor;

@Schema(description = "태그 수정 요청")
@Getter
@NoArgsConstructor
public class TagUpdateRequestDto {

  @Schema(description = "태그 이름. null이면 변경하지 않음. 빈 문자열은 허용하지 않음", example = "업무")
  private String title;

  @Schema(description = "태그 색상. null이면 색상 제거", example = "RED")
  private TagColor color;
}
