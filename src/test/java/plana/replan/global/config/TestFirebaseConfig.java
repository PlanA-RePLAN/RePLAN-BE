package plana.replan.global.config;

import com.google.firebase.messaging.FirebaseMessaging;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class TestFirebaseConfig {
  @Bean
  public FirebaseMessaging firebaseMessaging() {
    return Mockito.mock(FirebaseMessaging.class);
  }
}
