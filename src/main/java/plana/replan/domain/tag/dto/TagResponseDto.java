package plana.replan.domain.tag.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import plana.replan.domain.tag.entity.Tag;

@Schema(description = "태그 응답")
@Getter
@AllArgsConstructor
public class TagResponseDto {

  @Schema(description = "태그 ID", example = "1")
  private Long tagId;

  @Schema(description = "태그 이름", example = "영어")
  private String title;

  @Schema(description = "태그 색상 (없으면 null)", example = "BLUE")
  private String color;

  public static TagResponseDto from(Tag tag) {
    return new TagResponseDto(tag.getId(), tag.getTitle(), tag.getColor());
  }
}
