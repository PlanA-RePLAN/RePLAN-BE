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
    // 요청 본문이 JSON null로 들어오면 변경 없이 현재 설정을 그대로 반환한다(보내지 않은 필드는 유지 규칙과 동일).
    if (request != null) {
      user.updateNotificationSettings(request.todoDue(), request.todoFailed(), request.report());
    }
    return NotificationSettingResponse.from(user);
  }

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
  }
}
