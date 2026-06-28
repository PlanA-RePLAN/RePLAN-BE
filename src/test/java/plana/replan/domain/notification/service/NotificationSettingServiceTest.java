package plana.replan.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import plana.replan.domain.notification.dto.NotificationSettingResponse;
import plana.replan.domain.notification.dto.NotificationSettingUpdateRequest;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@ExtendWith(MockitoExtension.class)
class NotificationSettingServiceTest {

  @Mock private UserRepository userRepository;
  @InjectMocks private NotificationSettingService settingService;

  private User user() {
    return User.builder()
        .email("a@a.com")
        .nickname("nick")
        .role(Role.ROLE_USER)
        .provider(Provider.LOCAL)
        .build();
  }

  @Test
  @DisplayName("기본값은 모두 켜짐이다")
  void defaultsAllOn() {
    given(userRepository.findById(1L)).willReturn(Optional.of(user()));

    NotificationSettingResponse res = settingService.get(1L);

    assertThat(res.todoDue()).isTrue();
    assertThat(res.todoFailed()).isTrue();
    assertThat(res.report()).isTrue();
  }

  @Test
  @DisplayName("일부만 보내면 그 항목만 바뀌고 나머지는 유지된다")
  void partialUpdate() {
    User u = user();
    given(userRepository.findById(1L)).willReturn(Optional.of(u));

    NotificationSettingResponse res =
        settingService.update(1L, new NotificationSettingUpdateRequest(false, null, null));

    assertThat(res.todoDue()).isFalse();
    assertThat(res.todoFailed()).isTrue();
    assertThat(res.report()).isTrue();
  }

  @Test
  @DisplayName("존재하지 않는 사용자 조회 시 예외")
  void getThrowsWhenUserNotFound() {
    given(userRepository.findById(99L)).willReturn(java.util.Optional.empty());
    assertThatThrownBy(() -> settingService.get(99L)).isInstanceOf(CustomException.class);
  }
}
