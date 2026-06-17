package plana.replan.domain.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.auth.dto.PresignedUrlResponseDto;
import plana.replan.domain.user.dto.ProfileUpdateRequestDto;
import plana.replan.domain.user.dto.UserResponseDto;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;
import plana.replan.global.exception.GlobalErrorCode;
import plana.replan.global.s3.S3Service;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final S3Service s3Service;
  private final StringRedisTemplate redisTemplate;

  @Transactional(readOnly = true)
  public UserResponseDto getMyInfo(Long userId) {
    return UserResponseDto.from(findUser(userId));
  }

  @Transactional
  public UserResponseDto updateProfile(Long userId, ProfileUpdateRequestDto request) {
    User user = findUser(userId);

    String nickname = request.nickname();
    if (nickname != null) {
      if (nickname.isBlank()) {
        throw new CustomException(GlobalErrorCode.INVALID_INPUT);
      }
      if (!nickname.equals(user.getNickname())) {
        if (userRepository.existsByNickname(nickname)) {
          throw new CustomException(UserErrorCode.DUPLICATE_NICKNAME);
        }
        user.updateNickname(nickname);
      }
    }

    String profileImageKey = request.profileImageKey();
    if (profileImageKey != null) {
      String imageUrl = s3Service.moveToConfirmed(profileImageKey);
      user.updateProfileImage(imageUrl);
    }

    return UserResponseDto.from(user);
  }

  @Transactional(readOnly = true)
  public PresignedUrlResponseDto createProfileImagePresignedUrl(
      Long userId, String filename, String contentType) {
    findUser(userId);
    return s3Service.generatePresignedUrlForUser(filename, contentType);
  }

  @Transactional
  public void deleteAccount(Long userId) {
    User user = findUser(userId);
    user.softDelete();
    redisTemplate.delete("refresh:" + user.getEmail());
  }

  private User findUser(Long userId) {
    if (userId == null) {
      throw new CustomException(UserErrorCode.USER_NOT_FOUND);
    }
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));
  }
}
