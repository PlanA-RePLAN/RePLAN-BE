package plana.replan.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NaverLoginRequestDto {

  @NotBlank(message = "Naver Access Token은 필수입니다.")
  private String accessToken;
}
