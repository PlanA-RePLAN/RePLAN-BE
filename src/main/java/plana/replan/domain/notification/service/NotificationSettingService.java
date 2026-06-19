package plana.replan.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.notification.dto.NotificationSettingResponse;
import plana.replan.domain.notification.dto.NotificationSettingUpdateRequest;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@Service
@RequiredArgsConstructor
public class NotificationSettingService {

  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public NotificationSettingResponse get(Long userId) {
    return NotificationSettingResponse.from(findUser(userId));
  }

  @Transactional
  public NotificationSettingResponse update(Long userId, NotificationSettingUpdateRequest request) {
    User user = findUser(userId);
    user.updateNotificationSettings(request.todoDue(), request.todoFailed(), request.report());
    return NotificationSettingResponse.from(user);
  }

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
  }
}
