package plana.replan.domain.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.LocalDateTime;
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
  @DisplayName("기본값: 알림 3종은 켜짐, 마케팅 동의는 꺼짐이다")
  void defaults() {
    given(userRepository.findById(1L)).willReturn(Optional.of(user()));

    NotificationSettingResponse res = settingService.get(1L);

    assertThat(res.todo()).isTrue();
    assertThat(res.stats()).isTrue();
    assertThat(res.notice()).isTrue();
    assertThat(res.marketing()).isFalse();
  }

  @Test
  @DisplayName("일부만 보내면 그 항목만 바뀌고 나머지는 유지된다")
  void partialUpdate() {
    User u = user();
    given(userRepository.findById(1L)).willReturn(Optional.of(u));

    NotificationSettingResponse res =
        settingService.update(1L, new NotificationSettingUpdateRequest(false, null, null, null));

    assertThat(res.todo()).isFalse();
    assertThat(res.stats()).isTrue();
    assertThat(res.notice()).isTrue();
    assertThat(res.marketing()).isFalse();
  }

  @Test
  @DisplayName("마케팅 동의를 켜면 동의 시각이 함께 기록된다")
  void marketingOnRecordsTimestamp() {
    User u = user();
    given(userRepository.findById(1L)).willReturn(Optional.of(u));

    NotificationSettingResponse res =
        settingService.update(1L, new NotificationSettingUpdateRequest(null, null, null, true));

    assertThat(res.marketing()).isTrue();
    assertThat(u.getMarketingAgreedAt()).isNotNull();
  }

  @Test
  @DisplayName("마케팅 동의를 껐다 켜면 시각이 갱신된다")
  void marketingToggleUpdatesTimestamp() {
    User u = user();
    given(userRepository.findById(1L)).willReturn(Optional.of(u));

    settingService.update(1L, new NotificationSettingUpdateRequest(null, null, null, true));
    LocalDateTime agreedAt = u.getMarketingAgreedAt();

    settingService.update(1L, new NotificationSettingUpdateRequest(null, null, null, false));
    LocalDateTime withdrawnAt = u.getMarketingAgreedAt();

    assertThat(u.isMarketingAgreed()).isFalse();
    assertThat(withdrawnAt).isNotNull().isAfterOrEqualTo(agreedAt);
  }

  @Test
  @DisplayName("마케팅 값이 그대로면(미동의→false) 시각을 기록하지 않는다")
  void marketingSameValueKeepsTimestamp() {
    User u = user();
    given(userRepository.findById(1L)).willReturn(Optional.of(u));

    NotificationSettingResponse res =
        settingService.update(1L, new NotificationSettingUpdateRequest(null, null, null, false));

    assertThat(res.marketing()).isFalse();
    assertThat(u.getMarketingAgreedAt()).isNull();
  }

  @Test
  @DisplayName("존재하지 않는 사용자 조회 시 예외")
  void getThrowsWhenUserNotFound() {
    given(userRepository.findById(99L)).willReturn(java.util.Optional.empty());
    assertThatThrownBy(() -> settingService.get(99L)).isInstanceOf(CustomException.class);
  }
}
