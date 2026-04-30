package plana.replan.domain.auth.dto;

import lombok.Getter;

@Getter
public class OAuthLoginResponseDto {

  private final boolean isNewUser;
  private final String tempToken;
  private final String accessToken;
  private final String refreshToken;

  private OAuthLoginResponseDto(
      boolean isNewUser, String tempToken, String accessToken, String refreshToken) {
    this.isNewUser = isNewUser;
    this.tempToken = tempToken;
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
  }

  public static OAuthLoginResponseDto newUser(String tempToken) {
    return new OAuthLoginResponseDto(true, tempToken, null, null);
  }

  public static OAuthLoginResponseDto existingUser(String accessToken, String refreshToken) {
    return new OAuthLoginResponseDto(false, null, accessToken, refreshToken);
  }
}
