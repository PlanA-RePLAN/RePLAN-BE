package plana.replan.domain.notification.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;

class DeviceTokenTest {

  private User user() {
    return User.builder()
        .email("a@a.com")
        .nickname("n")
        .role(Role.ROLE_USER)
        .provider(Provider.LOCAL)
        .build();
  }

  @Test
  @DisplayName("플랫폼을 갱신할 수 있다")
  void updatePlatform() {
    DeviceToken token =
        DeviceToken.builder().user(user()).token("abc").platform(Platform.WEB).build();

    token.updatePlatform(Platform.ANDROID);

    assertThat(token.getPlatform()).isEqualTo(Platform.ANDROID);
  }
}
