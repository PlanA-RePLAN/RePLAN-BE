package plana.replan.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class OAuthRegisterRequestDto {

  @NotBlank(message = "닉네임은 필수입니다.")
  private String nickname;

  private String s3Key;
}
