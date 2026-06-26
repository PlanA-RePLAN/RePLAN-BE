package plana.replan.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.notification.dto.DeviceTokenDeleteRequest;
import plana.replan.domain.notification.dto.DeviceTokenRegisterRequest;
import plana.replan.domain.notification.entity.DeviceToken;
import plana.replan.domain.notification.exception.NotificationErrorCode;
import plana.replan.domain.notification.repository.DeviceTokenRepository;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;

@Service
@RequiredArgsConstructor
public class DeviceTokenService {

  private final DeviceTokenRepository deviceTokenRepository;
  private final UserRepository userRepository;

  @Transactional
  public void register(Long userId, DeviceTokenRegisterRequest request) {
    User user = findUser(userId);
    deviceTokenRepository
        .findByToken(request.token())
        .ifPresentOrElse(
            existing -> {
              existing.updatePlatform(request.platform());
              existing.changeOwner(user); // 같은 토큰이 다른 계정으로 재등록될 수 있음
            },
            () ->
                deviceTokenRepository.save(
                    DeviceToken.builder()
                        .user(user)
                        .token(request.token())
                        .platform(request.platform())
                        .build()));
  }

  @Transactional
  public void delete(Long userId, DeviceTokenDeleteRequest request) {
    User user = findUser(userId);
    DeviceToken token =
        deviceTokenRepository
            .findByUserAndToken(user, request.token())
            .orElseThrow(() -> new CustomException(NotificationErrorCode.TOKEN_NOT_FOUND));
    deviceTokenRepository.delete(token);
  }

  private User findUser(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
  }
}
