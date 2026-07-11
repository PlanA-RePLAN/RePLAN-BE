package plana.replan.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "회원가입 요청")
public class SignUpRequestDto {

  @Schema(
      description = "이메일",
      example = "replan@example.com",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = "이메일은 필수입니다.")
  @Email(message = "이메일 형식이 올바르지 않습니다.")
  private String email;

  @Schema(
      description = "비밀번호 (8자 이상)",
      example = "password1234",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = "비밀번호는 필수입니다.")
  @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
  private String password;

  @Schema(description = "닉네임", example = "리플랜", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = "닉네임은 필수입니다.")
  private String nickname;

  // 마케팅 정보 수신 동의(선택 약관). 생략하거나 null이면 미동의로 처리한다.
  @Schema(description = "마케팅 정보 수신 동의(선택). 생략/null이면 미동의로 저장", example = "true")
  private Boolean agreeMarketing;
}
