package plana.replan.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;
import plana.replan.domain.auth.apple.AppleAuthClient;
import plana.replan.domain.auth.dto.PresignedUrlResponseDto;
import plana.replan.domain.goal.repository.GoalRepository;
import plana.replan.domain.routine.repository.RoutineRepository;
import plana.replan.domain.tag.repository.TagRepository;
import plana.replan.domain.todo.repository.TodoRepository;
import plana.replan.domain.user.dto.ProfileUpdateRequestDto;
import plana.replan.domain.user.dto.UserResponseDto;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;
import plana.replan.global.s3.S3Service;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private TodoRepository todoRepository;
  @Mock private GoalRepository goalRepository;
  @Mock private RoutineRepository routineRepository;
  @Mock private TagRepository tagRepository;
  @Mock private S3Service s3Service;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;
  @Mock private AppleAuthClient appleAuthClient;

  @InjectMocks private UserService userService;

  private User testUser() {
    User user =
        User.builder()
            .email("test@test.com")
            .nickname("기존닉네임")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .profileImage("https://cdn.example.com/profiles/confirmed/old.png")
            .build();
    ReflectionTestUtils.setField(user, "id", 1L);
    return user;
  }

  @Test
  @DisplayName("내 정보 조회: 유저 정보를 그대로 반환한다")
  void getMyInfo_success() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));

    UserResponseDto result = userService.getMyInfo(1L);

    assertThat(result.getEmail()).isEqualTo("test@test.com");
    assertThat(result.getNickname()).isEqualTo("기존닉네임");
    assertThat(result.getProfileImage())
        .isEqualTo("https://cdn.example.com/profiles/confirmed/old.png");
  }

  @Test
  @DisplayName("내 정보 조회: userId가 null이면 USER_NOT_FOUND")
  void getMyInfo_nullUserId() {
    assertThatThrownBy(() -> userService.getMyInfo(null))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("내 정보 조회: 유저가 없으면 USER_NOT_FOUND")
  void getMyInfo_userNotFound() {
    given(userRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.getMyInfo(999L))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("프로필 수정: 닉네임만 변경하면 중복 검사 후 닉네임이 수정된다")
  void updateProfile_nicknameOnly() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(userRepository.existsByNickname("새닉네임")).willReturn(false);

    UserResponseDto result =
        userService.updateProfile(1L, new ProfileUpdateRequestDto("새닉네임", null));

    assertThat(user.getNickname()).isEqualTo("새닉네임");
    assertThat(result.getNickname()).isEqualTo("새닉네임");
    verify(s3Service, never()).moveToConfirmed(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  @DisplayName("프로필 수정: 닉네임이 이미 사용 중이면 DUPLICATE_NICKNAME")
  void updateProfile_duplicateNickname() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(userRepository.existsByNickname("중복닉네임")).willReturn(true);

    assertThatThrownBy(
            () -> userService.updateProfile(1L, new ProfileUpdateRequestDto("중복닉네임", null)))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.DUPLICATE_NICKNAME);

    assertThat(user.getNickname()).isEqualTo("기존닉네임");
  }

  @Test
  @DisplayName("프로필 수정: 현재 닉네임과 동일하면 중복 검사 없이 통과한다")
  void updateProfile_sameNickname() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    userService.updateProfile(1L, new ProfileUpdateRequestDto("기존닉네임", null));

    verify(userRepository, never()).existsByNickname(org.mockito.ArgumentMatchers.anyString());
    assertThat(user.getNickname()).isEqualTo("기존닉네임");
  }

  @Test
  @DisplayName("프로필 수정: 이미지 key가 오면 confirmed로 이동 후 URL이 저장된다")
  void updateProfile_imageOnly() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));
    given(s3Service.moveToConfirmed("profiles/temp/abc_new.png"))
        .willReturn("https://cdn.example.com/profiles/confirmed/abc_new.png");

    UserResponseDto result =
        userService.updateProfile(
            1L, new ProfileUpdateRequestDto(null, "profiles/temp/abc_new.png"));

    assertThat(user.getProfileImage())
        .isEqualTo("https://cdn.example.com/profiles/confirmed/abc_new.png");
    assertThat(result.getProfileImage())
        .isEqualTo("https://cdn.example.com/profiles/confirmed/abc_new.png");
  }

  @Test
  @DisplayName("프로필 수정: 두 필드 모두 null이면 아무것도 변경되지 않는다")
  void updateProfile_bothNull() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    userService.updateProfile(1L, new ProfileUpdateRequestDto(null, null));

    assertThat(user.getNickname()).isEqualTo("기존닉네임");
    assertThat(user.getProfileImage())
        .isEqualTo("https://cdn.example.com/profiles/confirmed/old.png");
    verify(userRepository, never()).existsByNickname(org.mockito.ArgumentMatchers.anyString());
    verify(s3Service, never()).moveToConfirmed(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  @DisplayName("프로필 수정: 닉네임이 공백이면 INVALID_INPUT")
  void updateProfile_blankNickname() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    assertThatThrownBy(() -> userService.updateProfile(1L, new ProfileUpdateRequestDto("  ", null)))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue(
            "errorCode", plana.replan.global.exception.GlobalErrorCode.INVALID_INPUT);

    assertThat(user.getNickname()).isEqualTo("기존닉네임");
    verify(userRepository, never()).existsByNickname(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  @DisplayName("프로필 수정: 유저가 없으면 USER_NOT_FOUND")
  void updateProfile_userNotFound() {
    given(userRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () -> userService.updateProfile(999L, new ProfileUpdateRequestDto("새닉네임", null)))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
  }

  @Test
  @DisplayName("프로필 이미지 presigned URL: 유저 확인 후 S3 발급 결과를 반환한다")
  void createProfileImagePresignedUrl_success() {
    given(userRepository.findById(1L)).willReturn(Optional.of(testUser()));
    PresignedUrlResponseDto dto =
        new PresignedUrlResponseDto("https://s3/presigned", "profiles/temp/uuid_avatar.png");
    given(s3Service.generatePresignedUrlForUser("avatar.png", "image/png")).willReturn(dto);

    PresignedUrlResponseDto result =
        userService.createProfileImagePresignedUrl(1L, "avatar.png", "image/png");

    assertThat(result.getS3Key()).isEqualTo("profiles/temp/uuid_avatar.png");
  }

  @Test
  @DisplayName("프로필 이미지 presigned URL: 유저가 없으면 USER_NOT_FOUND")
  void createProfileImagePresignedUrl_userNotFound() {
    given(userRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(
            () -> userService.createProfileImagePresignedUrl(999L, "avatar.png", "image/png"))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);

    verify(s3Service, never())
        .generatePresignedUrlForUser(
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  @DisplayName("계정 삭제: 개인정보 익명화 + 본인 데이터 삭제 + refresh token 삭제")
  void deleteAccount_success() {
    User user = testUser();
    given(userRepository.findById(1L)).willReturn(Optional.of(user));

    userService.deleteAccount(1L);

    // 개인정보 익명화 + soft delete
    assertThat(user.getDeletedAt()).isNotNull();
    assertThat(user.getEmail()).isEqualTo("deleted_1@deleted.local");
    assertThat(user.getNickname()).isEqualTo("deleted_1");
    assertThat(user.getPassword()).isNull();
    assertThat(user.getProfileImage()).isNull();

    // 본인이 만든 데이터 일괄 soft delete 호출
    verify(todoRepository)
        .softDeleteAllByUserId(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    verify(goalRepository)
        .softDeleteAllByUserId(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    verify(routineRepository)
        .softDeleteAllByUserId(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.any(LocalDateTime.class));
    verify(tagRepository)
        .softDeleteAllByUserId(
            org.mockito.ArgumentMatchers.eq(1L),
            org.mockito.ArgumentMatchers.any(LocalDateTime.class));

    // refresh token은 익명화 전 "원래 이메일" 키로 삭제되어야 한다
    verify(redisTemplate).delete("refresh:test@test.com");
  }

  @Test
  @DisplayName("애플 유저 탈퇴 시 refresh token으로 revoke를 호출하고 키를 삭제한다")
  void deleteAccount_apple_revokes() {
    Long userId = 1L;
    User user =
        User.builder()
            .email("apple-user@privaterelay.appleid.com")
            .nickname("a")
            .role(Role.ROLE_USER)
            .provider(Provider.APPLE)
            .build();
    org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
    given(userRepository.findById(userId)).willReturn(java.util.Optional.of(user));
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get("apple:refresh:" + userId))
        .willReturn("com.replan.service|apple-refresh-token");

    userService.deleteAccount(userId);

    verify(appleAuthClient).revoke("com.replan.service", "apple-refresh-token");
    verify(redisTemplate).delete("apple:refresh:" + userId);
  }

  @Test
  @DisplayName("revoke가 실패해도 탈퇴(soft delete)는 진행된다")
  void deleteAccount_apple_revokeFails_stillWithdraws() {
    Long userId = 1L;
    User user =
        User.builder()
            .email("apple-user@privaterelay.appleid.com")
            .nickname("a")
            .role(Role.ROLE_USER)
            .provider(Provider.APPLE)
            .build();
    org.springframework.test.util.ReflectionTestUtils.setField(user, "id", userId);
    given(userRepository.findById(userId)).willReturn(java.util.Optional.of(user));
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(valueOperations.get("apple:refresh:" + userId))
        .willReturn("com.replan.service|apple-refresh-token");
    org.mockito.BDDMockito.willThrow(new RuntimeException("apple down"))
        .given(appleAuthClient)
        .revoke(anyString(), anyString());

    userService.deleteAccount(userId);

    // 탈퇴는 진행됨 — 데이터 soft delete가 호출됐는지로 확인
    verify(todoRepository)
        .softDeleteAllByUserId(
            org.mockito.ArgumentMatchers.eq(userId), org.mockito.ArgumentMatchers.any());
    verify(redisTemplate).delete("apple:refresh:" + userId);
  }

  @Test
  @DisplayName("계정 삭제: 유저가 없으면 USER_NOT_FOUND")
  void deleteAccount_userNotFound() {
    given(userRepository.findById(999L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> userService.deleteAccount(999L))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);

    verify(redisTemplate, never()).delete(org.mockito.ArgumentMatchers.anyString());
  }
}
