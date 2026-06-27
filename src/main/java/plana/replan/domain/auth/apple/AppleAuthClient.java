package plana.replan.domain.auth.apple;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
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

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final RestClient restClient;
  private final AppleClientSecretGenerator clientSecretGenerator;

  @SuppressWarnings("unchecked")
  public AppleTokenResponse exchangeRefreshToken(String clientId, String authorizationCode) {
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
      String refreshToken = (String) body.get("refresh_token");
      String sub = extractSub((String) body.get("id_token"));
      return new AppleTokenResponse(refreshToken, sub);
    } catch (CustomException e) {
      throw e;
    } catch (ResourceAccessException e) {
      throw new CustomException(UserErrorCode.OAUTH_SERVER_UNAVAILABLE);
    } catch (Exception e) {
      throw new CustomException(UserErrorCode.APPLE_TOKEN_INVALID);
    }
  }

  /**
   * 토큰 교환 응답의 id_token에서 sub(사용자 고유 식별자)만 꺼낸다. id_token은 애플 토큰 엔드포인트에서 HTTPS로 직접 받은 값이라 서명 재검증 없이
   * 페이로드만 디코딩한다. (호출자가 이 sub를 별도로 검증한 identityToken의 sub와 비교해 같은 사용자인지 확인한다.)
   */
  private String extractSub(String idToken) {
    try {
      if (idToken == null) {
        return null;
      }
      String[] parts = idToken.split("\\.");
      if (parts.length < 2) {
        return null;
      }
      byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
      return OBJECT_MAPPER.readTree(payload).path("sub").asText(null);
    } catch (Exception e) {
      return null;
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
