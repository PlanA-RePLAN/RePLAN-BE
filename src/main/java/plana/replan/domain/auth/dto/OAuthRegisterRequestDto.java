package plana.replan.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OAuth 신규유저 프로필 등록 요청")
public class OAuthRegisterRequestDto {

  @Schema(description = "닉네임", example = "리플랜", requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank(message = "닉네임은 필수입니다.")
  private String nickname;

  @Schema(description = "프로필 이미지 업로드 후 받은 S3 key(선택). 생략하면 기본 프로필", example = "temp/abc.png")
  private String s3Key;

  // 마케팅 정보 수신 동의(선택 약관). 생략하거나 null이면 미동의로 처리한다.
  @Schema(description = "마케팅 정보 수신 동의(선택). 생략/null이면 미동의로 저장", example = "true")
  private Boolean agreeMarketing;
}
