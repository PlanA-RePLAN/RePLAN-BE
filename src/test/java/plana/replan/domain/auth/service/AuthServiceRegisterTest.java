package plana.replan.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import plana.replan.domain.auth.dto.LoginResponseDto;
import plana.replan.domain.auth.dto.OAuthRegisterRequestDto;
import plana.replan.domain.tag.service.TagService;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;
import plana.replan.global.jwt.JwtUtil;
import plana.replan.global.s3.S3Service;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceRegisterTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtUtil jwtUtil;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private GoogleIdTokenVerifier googleIdTokenVerifier;
  @Mock private RestClient restClient;
  @Mock private S3Service s3Service;
  @Mock private TagService tagService;

  @InjectMocks private AuthService authService;

  @Mock private ValueOperations<String, String> valueOperations;

  @BeforeEach
  void setUp() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(jwtUtil.generateAccessToken(anyString(), anyString(), any())).willReturn("access-token");
    given(jwtUtil.generateRefreshToken(anyString())).willReturn("refresh-token");
    given(jwtUtil.getRefreshExpiration()).willReturn(604800000L);
  }

  @Test
  @DisplayName("정상 등록 (이미지 없음): User 생성, JWT 반환, tempToken Redis 삭제")
  void register_success_withoutImage() {
    String tempToken = "valid-temp-token";
    given(valueOperations.get("oauth-temp:" + tempToken)).willReturn("test@kakao.com:KAKAO");
    given(userRepository.existsByNickname("홍길동")).willReturn(false);

    User savedUser =
        User.builder()
            .email("test@kakao.com")
            .nickname("홍길동")
            .role(Role.ROLE_USER)
            .provider(Provider.KAKAO)
            .build();
    given(userRepository.save(any(User.class))).willReturn(savedUser);

    LoginResponseDto result =
        authService.register(new OAuthRegisterRequestDto("홍길동", null), tempToken);

    assertThat(result.getAccessToken()).isEqualTo("access-token");
    assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
    verify(userRepository).save(any(User.class));
    verify(s3Service, never()).moveToConfirmed(anyString());
    verify(redisTemplate).delete("oauth-temp:" + tempToken);
    verify(valueOperations).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
  }

  @Test
  @DisplayName("정상 등록 (이미지 있음): S3 이동 후 User 생성, JWT 반환")
  void register_success_withImage() {
    String tempToken = "valid-temp-token";
    given(valueOperations.get("oauth-temp:" + tempToken)).willReturn("test@naver.com:NAVER");
    given(userRepository.existsByNickname("홍길동")).willReturn(false);
    given(s3Service.moveToConfirmed("profiles/temp/uuid_photo.jpg"))
        .willReturn("https://d1vob2cgkjpe9n.cloudfront.net/profiles/confirmed/uuid_photo.jpg");

    User savedUser =
        User.builder()
            .email("test@naver.com")
            .nickname("홍길동")
            .role(Role.ROLE_USER)
            .provider(Provider.NAVER)
            .profileImage("https://d1vob2cgkjpe9n.cloudfront.net/profiles/confirmed/uuid_photo.jpg")
            .build();
    given(userRepository.save(any(User.class))).willReturn(savedUser);

    LoginResponseDto result =
        authService.register(
            new OAuthRegisterRequestDto("홍길동", "profiles/temp/uuid_photo.jpg"), tempToken);

    assertThat(result.getAccessToken()).isEqualTo("access-token");
    verify(s3Service).moveToConfirmed("profiles/temp/uuid_photo.jpg");
    verify(userRepository).save(any(User.class));
    verify(redisTemplate).delete("oauth-temp:" + tempToken);
  }

  @Test
  @DisplayName("유효하지 않은 tempToken: INVALID_TEMP_TOKEN 예외")
  void register_invalidTempToken_throws() {
    given(valueOperations.get("oauth-temp:bad-token")).willReturn(null);

    assertThatThrownBy(
            () -> authService.register(new OAuthRegisterRequestDto("홍길동", null), "bad-token"))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.INVALID_TEMP_TOKEN));

    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  @DisplayName("애플 신규 가입 완료 시 임시 refresh token을 userId 키로 옮기고 임시 키를 삭제한다")
  void register_apple_movesRefreshToken() {
    String tempToken = "temp-uuid";
    String email = "apple-user@privaterelay.appleid.com";
    given(valueOperations.get("oauth-temp:" + tempToken)).willReturn(email + ":APPLE");
    given(valueOperations.get("apple-refresh-temp:" + email))
        .willReturn("com.replan.service|apple-refresh-token");
    given(userRepository.existsByNickname(anyString())).willReturn(false);
    User saved =
        User.builder()
            .email(email)
            .nickname("nick")
            .role(Role.ROLE_USER)
            .provider(Provider.APPLE)
            .build();
    ReflectionTestUtils.setField(saved, "id", 1L);
    given(userRepository.save(any(User.class))).willReturn(saved);

    authService.register(new OAuthRegisterRequestDto("nick", null), tempToken);

    verify(valueOperations)
        .set("apple:refresh:" + saved.getId(), "com.replan.service|apple-refresh-token");
    verify(redisTemplate).delete("apple-refresh-temp:" + email);
  }

  @Test
  @DisplayName("애플 가입인데 임시 refresh token이 없으면(만료 등) INVALID_TEMP_TOKEN으로 가입 실패")
  void register_apple_missingRefresh_throws() {
    String tempToken = "temp-uuid";
    String email = "apple-user@privaterelay.appleid.com";
    given(valueOperations.get("oauth-temp:" + tempToken)).willReturn(email + ":APPLE");
    given(valueOperations.get("apple-refresh-temp:" + email)).willReturn(null);
    given(userRepository.existsByNickname(anyString())).willReturn(false);
    User saved =
        User.builder()
            .email(email)
            .nickname("nick")
            .role(Role.ROLE_USER)
            .provider(Provider.APPLE)
            .build();
    ReflectionTestUtils.setField(saved, "id", 1L);
    given(userRepository.save(any(User.class))).willReturn(saved);

    assertThatThrownBy(
            () -> authService.register(new OAuthRegisterRequestDto("nick", null), tempToken))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.INVALID_TEMP_TOKEN);
  }

  @Test
  @DisplayName("닉네임 중복: DUPLICATE_NICKNAME 예외")
  void register_duplicateNickname_throws() {
    String tempToken = "valid-temp-token";
    given(valueOperations.get("oauth-temp:" + tempToken)).willReturn("test@kakao.com:KAKAO");
    given(userRepository.existsByNickname("중복닉네임")).willReturn(true);

    assertThatThrownBy(
            () -> authService.register(new OAuthRegisterRequestDto("중복닉네임", null), tempToken))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.DUPLICATE_NICKNAME));

    verify(userRepository, never()).save(any(User.class));
  }
}
