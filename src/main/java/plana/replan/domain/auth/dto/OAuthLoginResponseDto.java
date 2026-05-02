package plana.replan.domain.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class OAuthLoginResponseDto {

  @JsonProperty("isNewUser")
  private final boolean newUser;

  private final String tempToken;
  private final String accessToken;
  private final String refreshToken;

  private OAuthLoginResponseDto(
      boolean newUser, String tempToken, String accessToken, String refreshToken) {
    this.newUser = newUser;
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
