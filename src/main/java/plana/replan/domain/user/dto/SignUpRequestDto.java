package plana.replan.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignUpRequestDto {

  @NotBlank(message = "이메일은 필수입니다.")
  @Email(message = "이메일 형식이 올바르지 않습S니다.")
  private String email;

  //    @NotBlank(message = "비밀번호는 필수입니다.")
  //    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
  //    private String password;

  @NotBlank(message = "닉네임은 필수입니다.")
  private String nickname;
}
