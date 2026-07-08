package plana.replan.domain.auth.apple;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.ProtectedHeader;
import io.jsonwebtoken.security.Jwks;
import io.jsonwebtoken.security.PublicJwk;
import java.security.Key;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.global.config.AppleProperties;
import plana.replan.global.exception.CustomException;

@Component
@RequiredArgsConstructor
public class AppleTokenVerifier {

  private static final String APPLE_ISS = "https://appleid.apple.com";
  private static final String JWKS_URL = "https://appleid.apple.com/auth/keys";

  private final RestClient restClient;
  private final AppleProperties properties;

  public AppleIdTokenPayload verify(String identityToken) {
    try {
      String jwksJson = restClient.get().uri(JWKS_URL).retrieve().body(String.class);
      var jwkSet = Jwks.setParser().build().parse(jwksJson);

      LocatorAdapter<Key> keyLocator =
          new LocatorAdapter<>() {
            @Override
            protected Key locate(ProtectedHeader header) {
              String kid = header.getKeyId();
              return jwkSet.getKeys().stream()
                  .filter(jwk -> kid != null && kid.equals(jwk.getId()))
                  .findFirst()
                  .map(jwk -> (Key) ((PublicJwk<?>) jwk).toKey())
                  .orElseThrow(() -> new CustomException(UserErrorCode.APPLE_TOKEN_INVALID));
            }
          };

      Jws<Claims> jws =
          Jwts.parser()
              .keyLocator(keyLocator)
              .requireIssuer(APPLE_ISS)
              .build()
              .parseSignedClaims(identityToken);

      Claims claims = jws.getPayload();
      Set<String> audiences = claims.getAudience();
      String matchedAud =
          properties.getClientIds().stream()
              .filter(id -> audiences != null && audiences.contains(id))
              .findFirst()
              .orElseThrow(() -> new CustomException(UserErrorCode.APPLE_TOKEN_INVALID));

      // sub(고유 식별번호)는 애플이 로그인마다 항상 주는 값이라, 이걸 사용자 식별 기준으로 삼는다.
      String sub = claims.getSubject();
      if (sub == null) {
        throw new CustomException(UserErrorCode.APPLE_TOKEN_INVALID);
      }

      // 이메일은 애플이 최초 인증 때만 준다(네이티브 재로그인 시엔 없음).
      // 이메일이 있을 때만 이메일 인증 여부를 확인하고, 없으면 sub로만 식별한다.
      // (email_verified는 boolean 또는 "true" 문자열로 옴)
      String email = claims.get("email", String.class);
      if (email != null) {
        Object emailVerified = claims.get("email_verified");
        if (!"true".equals(String.valueOf(emailVerified))) {
          throw new CustomException(UserErrorCode.APPLE_TOKEN_INVALID);
        }
      }

      return new AppleIdTokenPayload(email, matchedAud, sub);
    } catch (CustomException e) {
      throw e;
    } catch (ResourceAccessException e) {
      throw new CustomException(UserErrorCode.OAUTH_SERVER_UNAVAILABLE);
    } catch (Exception e) {
      throw new CustomException(UserErrorCode.APPLE_TOKEN_INVALID);
    }
  }
}
