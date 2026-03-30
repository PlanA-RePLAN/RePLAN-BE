package plana.replan.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LoginResponseDto {

  private String accessToken;
  private String refreshToken;
}
