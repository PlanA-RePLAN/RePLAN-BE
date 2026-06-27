package plana.replan.domain.auth.apple;

import io.jsonwebtoken.Jwts;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import plana.replan.global.config.AppleProperties;
import plana.replan.global.exception.CustomException;
import plana.replan.global.exception.GlobalErrorCode;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppleClientSecretGenerator {

  private static final String APPLE_AUD = "https://appleid.apple.com";

  private final AppleProperties properties;

  public String generate(String clientId) {
    PrivateKey privateKey = loadPrivateKey(properties.getPrivateKey());
    Instant now = Instant.now();
    return Jwts.builder()
        .header()
        .keyId(properties.getKeyId())
        .and()
        .issuer(properties.getTeamId())
        .subject(clientId)
        .audience()
        .add(APPLE_AUD)
        .and()
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(Duration.ofMinutes(5))))
        .signWith(privateKey, Jwts.SIG.ES256)
        .compact();
  }

  private PrivateKey loadPrivateKey(String p8) {
    try {
      String cleaned =
          p8.replace("\\n", "") // 환경변수로 주입 시 한 줄에 담긴 리터럴 \n 제거
              .replace("-----BEGIN PRIVATE KEY-----", "")
              .replace("-----END PRIVATE KEY-----", "")
              .replaceAll("\\s", "");
      byte[] der = Base64.getDecoder().decode(cleaned);
      return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
    } catch (Exception e) {
      // 사용자 토큰 오류가 아니라 서버의 애플 비공개키 설정/파싱 문제이므로 500으로 처리한다.
      log.error("애플 비공개키 로딩 실패 - 서버 설정을 확인하세요", e);
      throw new CustomException(GlobalErrorCode.INTERNAL_SERVER_ERROR);
    }
  }
}
