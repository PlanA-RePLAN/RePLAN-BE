package plana.replan.domain.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PresignedUrlResponseDto {

  private String presignedUrl;
  private String s3Key;
}
