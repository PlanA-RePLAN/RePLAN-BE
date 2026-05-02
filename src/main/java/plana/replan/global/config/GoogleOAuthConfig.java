package plana.replan.global.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class GoogleOAuthConfig {

  @Value("${google.client-ids.web}")
  private String webClientId;

  @Value("${google.client-ids.android:}")
  private String androidClientId;

  @Value("${google.client-ids.ios:}")
  private String iosClientId;

  @Bean
  public GoogleIdTokenVerifier googleIdTokenVerifier() {
    List<String> allowedClientIds =
        Stream.of(webClientId, androidClientId, iosClientId).filter(StringUtils::hasText).toList();

    return new GoogleIdTokenVerifier.Builder(
            new NetHttpTransport(), GsonFactory.getDefaultInstance())
        .setAudience(allowedClientIds)
        .build();
  }
}
