package plana.replan.domain.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "애플 로그인 요청")
public class AppleLoginRequestDto {

  @Schema(
      description = "애플이 발급한 identity token(JWT)",
      example = "eyJraWQiOiJ...",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank
  private String identityToken;

  @Schema(
      description = "애플이 발급한 authorization code (refresh token 교환 및 탈퇴 시 철회용)",
      example = "c1a2b3...",
      requiredMode = Schema.RequiredMode.REQUIRED)
  @NotBlank
  private String authorizationCode;
}
