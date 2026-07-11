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

  // 마케팅 정보 수신 동의(선택 약관). 생략하거나 null이면 미동의로 처리한다.
  private Boolean agreeMarketing;
}
