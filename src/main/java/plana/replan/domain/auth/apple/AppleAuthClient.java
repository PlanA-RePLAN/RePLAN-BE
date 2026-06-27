package plana.replan.domain.auth.apple;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.global.exception.CustomException;

@Component
@RequiredArgsConstructor
public class AppleAuthClient {

  private static final String TOKEN_URL = "https://appleid.apple.com/auth/token";
  private static final String REVOKE_URL = "https://appleid.apple.com/auth/revoke";

  private final RestClient restClient;
  private final AppleClientSecretGenerator clientSecretGenerator;

  @SuppressWarnings("unchecked")
  public String exchangeRefreshToken(String clientId, String authorizationCode) {
    try {
      MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
      form.add("client_id", clientId);
      form.add("client_secret", clientSecretGenerator.generate(clientId));
      form.add("grant_type", "authorization_code");
      form.add("code", authorizationCode);

      Map<String, Object> body =
          restClient
              .post()
              .uri(TOKEN_URL)
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(Map.class);

      if (body == null || body.get("refresh_token") == null) {
        throw new CustomException(UserErrorCode.APPLE_TOKEN_INVALID);
      }
      return (String) body.get("refresh_token");
    } catch (CustomException e) {
      throw e;
    } catch (ResourceAccessException e) {
      throw new CustomException(UserErrorCode.OAUTH_SERVER_UNAVAILABLE);
    } catch (Exception e) {
      throw new CustomException(UserErrorCode.APPLE_TOKEN_INVALID);
    }
  }

  public void revoke(String clientId, String refreshToken) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", clientId);
    form.add("client_secret", clientSecretGenerator.generate(clientId));
    form.add("token", refreshToken);
    form.add("token_type_hint", "refresh_token");

    restClient
        .post()
        .uri(REVOKE_URL)
        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
        .body(form)
        .retrieve()
        .toBodilessEntity();
  }
}
