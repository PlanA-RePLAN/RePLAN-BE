package plana.replan.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeviceTokenTest {

  @Test
  @DisplayName("플랫폼을 갱신할 수 있다")
  void updatePlatform() {
    DeviceToken token =
        DeviceToken.builder().user(null).token("abc").platform(Platform.WEB).build();

    token.updatePlatform(Platform.ANDROID);

    assertThat(token.getPlatform()).isEqualTo(Platform.ANDROID);
  }
}
