package plana.replan.domain.user.service;

import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.auth.dto.PresignedUrlResponseDto;
import plana.replan.domain.goal.repository.GoalRepository;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.todo.repository.TodoRepository;
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
  private final TodoRepository todoRepository;
  private final GoalRepository goalRepository;
  private final RoutineRepository routineRepository;
  private final TagRepository tagRepository;
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

    // refresh token 키는 원래 이메일 기준이므로 익명화 전에 미리 확보한다.
    String originalEmail = user.getEmail();

    // 1) 회원이 만든 데이터(투두·목표·루틴·태그)를 함께 soft delete 한다.
    LocalDateTime now = LocalDateTime.now();
    todoRepository.softDeleteAllByUserId(userId, now);
    goalRepository.softDeleteAllByUserId(userId, now);
    routineRepository.softDeleteAllByUserId(userId, now);
    tagRepository.softDeleteAllByUserId(userId, now);

    // 2) 개인정보 익명화 + 회원 soft delete
    user.withdraw();

    // 3) 로그인 유지용 refresh token 무효화
    redisTemplate.delete("refresh:" + originalEmail);
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
