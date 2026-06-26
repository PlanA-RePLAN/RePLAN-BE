package plana.replan.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import plana.replan.domain.notification.dto.DeviceTokenDeleteRequest;
import plana.replan.domain.notification.dto.DeviceTokenRegisterRequest;
import plana.replan.domain.notification.entity.DeviceToken;
import plana.replan.domain.notification.entity.Platform;
import plana.replan.domain.notification.exception.NotificationErrorCode;
import plana.replan.domain.notification.repository.DeviceTokenRepository;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

  @Mock private DeviceTokenRepository deviceTokenRepository;
  @Mock private UserRepository userRepository;
  @InjectMocks private DeviceTokenService deviceTokenService;

  private User user() {
    return User.builder()
        .email("a@a.com")
        .nickname("nick")
        .role(Role.ROLE_USER)
        .provider(Provider.LOCAL)
        .build();
  }

  @Test
  @DisplayName("새 토큰이면 저장한다")
  void registerNewToken() {
    given(userRepository.findById(1L)).willReturn(Optional.of(user()));
    given(deviceTokenRepository.findByToken("t1")).willReturn(Optional.empty());

    deviceTokenService.register(1L, new DeviceTokenRegisterRequest("t1", Platform.WEB));

    verify(deviceTokenRepository).save(any(DeviceToken.class));
  }

  @Test
  @DisplayName("이미 있는 토큰이면 새로 저장하지 않고 플랫폼만 갱신한다")
  void registerExistingTokenUpserts() {
    User newUser = user();
    DeviceToken existing =
        DeviceToken.builder().user(user()).token("t1").platform(Platform.WEB).build();
    given(userRepository.findById(1L)).willReturn(Optional.of(newUser));
    given(deviceTokenRepository.findByToken("t1")).willReturn(Optional.of(existing));

    deviceTokenService.register(1L, new DeviceTokenRegisterRequest("t1", Platform.ANDROID));

    assertThat(existing.getPlatform()).isEqualTo(Platform.ANDROID);
    assertThat(existing.getUser()).isSameAs(newUser);
    verify(deviceTokenRepository, never()).save(any(DeviceToken.class));
  }

  @Test
  @DisplayName("내 토큰이 아니면 삭제 시 예외")
  void deleteMissingToken() {
    User u = user();
    given(userRepository.findById(1L)).willReturn(Optional.of(u));
    given(deviceTokenRepository.findByUserAndToken(u, "nope")).willReturn(Optional.empty());

    assertThatThrownBy(() -> deviceTokenService.delete(1L, new DeviceTokenDeleteRequest("nope")))
        .isInstanceOf(CustomException.class)
        .hasMessageContaining(NotificationErrorCode.TOKEN_NOT_FOUND.getMessage());
  }
}
