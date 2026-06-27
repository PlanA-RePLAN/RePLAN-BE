package plana.replan.domain.auth.apple;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import plana.replan.global.config.AppleProperties;

class AppleClientSecretGeneratorTest {

  private AppleProperties propsWithKey(ECPrivateKey privateKey) {
    String pem =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getEncoder().encodeToString(privateKey.getEncoded())
            + "\n-----END PRIVATE KEY-----";
    AppleProperties props = new AppleProperties();
    props.setTeamId("TEAM123456");
    props.setKeyId("KEY1234567");
    props.setPrivateKey(pem);
    return props;
  }

  @Test
  void generate_ES256_client_secret_헤더와_클레임이_올바르다() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("EC");
    gen.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair pair = gen.generateKeyPair();

    AppleProperties props = propsWithKey((ECPrivateKey) pair.getPrivate());
    AppleClientSecretGenerator generator = new AppleClientSecretGenerator(props);

    String secret = generator.generate("com.replan.service");

    Jws<Claims> jws = Jwts.parser().verifyWith(pair.getPublic()).build().parseSignedClaims(secret);
    Claims claims = jws.getPayload();
    assertThat(jws.getHeader().getKeyId()).isEqualTo("KEY1234567");
    assertThat(claims.getIssuer()).isEqualTo("TEAM123456");
    assertThat(claims.getSubject()).isEqualTo("com.replan.service");
    assertThat(claims.getAudience()).contains("https://appleid.apple.com");
  }
}
