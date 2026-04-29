package plana.replan.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import java.util.Optional;
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
import plana.replan.domain.auth.dto.GoogleLoginRequestDto;
import plana.replan.domain.auth.dto.LoginResponseDto;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;
import plana.replan.global.jwt.JwtUtil;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceGoogleLoginTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtUtil jwtUtil;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private GoogleIdTokenVerifier googleIdTokenVerifier;

  @InjectMocks private AuthService authService;

  @Mock private ValueOperations<String, String> valueOperations;

  private GoogleIdToken mockIdToken;
  private GoogleIdToken.Payload mockPayload;

  @BeforeEach
  void setUp() {
    mockIdToken = mock(GoogleIdToken.class);
    mockPayload = mock(GoogleIdToken.Payload.class);

    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(jwtUtil.generateAccessToken(anyString(), anyString(), any())).willReturn("access-token");
    given(jwtUtil.generateRefreshToken(anyString())).willReturn("refresh-token");
    given(jwtUtil.getRefreshExpiration()).willReturn(604800000L);
  }

  private void setupValidToken(String email, String name, String picture) {
    try {
      given(googleIdTokenVerifier.verify(anyString())).willReturn(mockIdToken);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    given(mockIdToken.getPayload()).willReturn(mockPayload);
    given(mockPayload.getEmailVerified()).willReturn(true);
    given(mockPayload.getEmail()).willReturn(email);
    given(mockPayload.get("name")).willReturn(name);
    given(mockPayload.get("picture")).willReturn(picture);
  }

  @Test
  @DisplayName("신규 Google 유저 최초 로그인 시 자동 회원가입 후 토큰 반환")
  void googleLogin_newUser_success() {
    setupValidToken("new@gmail.com", "신규유저", "https://picture.url/photo.jpg");

    given(userRepository.findByEmail("new@gmail.com")).willReturn(Optional.empty());
    given(userRepository.findByEmailAndProvider("new@gmail.com", Provider.GOOGLE))
        .willReturn(Optional.empty());

    User savedUser =
        User.builder()
            .email("new@gmail.com")
            .nickname("신규유저")
            .role(Role.ROLE_USER)
            .provider(Provider.GOOGLE)
            .profileImage("https://picture.url/photo.jpg")
            .build();
    given(userRepository.save(any(User.class))).willReturn(savedUser);

    LoginResponseDto result =
        authService.googleLogin(new GoogleLoginRequestDto("valid-credential"));

    assertThat(result.getAccessToken()).isEqualTo("access-token");
    assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
    verify(userRepository).save(any(User.class));
    verify(valueOperations).set(anyString(), anyString(), any(Long.class), any(TimeUnit.class));
  }

  @Test
  @DisplayName("기존 Google 유저 재로그인 시 save 호출 없이 토큰 반환")
  void googleLogin_existingUser_success() {
    setupValidToken("existing@gmail.com", "기존유저", null);

    User existingUser =
        User.builder()
            .email("existing@gmail.com")
            .nickname("기존유저")
            .role(Role.ROLE_USER)
            .provider(Provider.GOOGLE)
            .build();

    given(userRepository.findByEmail("existing@gmail.com")).willReturn(Optional.of(existingUser));
    given(userRepository.findByEmailAndProvider("existing@gmail.com", Provider.GOOGLE))
        .willReturn(Optional.of(existingUser));

    LoginResponseDto result =
        authService.googleLogin(new GoogleLoginRequestDto("valid-credential"));

    assertThat(result.getAccessToken()).isEqualTo("access-token");
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  @DisplayName("verifier가 null 반환 시 GOOGLE_TOKEN_INVALID 예외")
  void googleLogin_invalidCredential_throws() throws Exception {
    given(googleIdTokenVerifier.verify(anyString())).willReturn(null);

    assertThatThrownBy(() -> authService.googleLogin(new GoogleLoginRequestDto("invalid-token")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.GOOGLE_TOKEN_INVALID));
  }

  @Test
  @DisplayName("verifier 내부 예외 발생 시 GOOGLE_TOKEN_INVALID 예외")
  void googleLogin_verifierThrows_throws() throws Exception {
    given(googleIdTokenVerifier.verify(anyString()))
        .willThrow(new RuntimeException("network error"));

    assertThatThrownBy(() -> authService.googleLogin(new GoogleLoginRequestDto("bad-token")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.GOOGLE_TOKEN_INVALID));
  }

  @Test
  @DisplayName("emailVerified=false 인 Google 계정: GOOGLE_TOKEN_INVALID 예외")
  void googleLogin_emailNotVerified_throws() throws Exception {
    given(googleIdTokenVerifier.verify(anyString())).willReturn(mockIdToken);
    given(mockIdToken.getPayload()).willReturn(mockPayload);
    given(mockPayload.getEmailVerified()).willReturn(false);

    assertThatThrownBy(() -> authService.googleLogin(new GoogleLoginRequestDto("unverified-token")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.GOOGLE_TOKEN_INVALID));
  }

  @Test
  @DisplayName("동일 이메일이 LOCAL Provider로 가입된 경우: OAUTH_PROVIDER_CONFLICT 예외")
  void googleLogin_providerConflict_throws() {
    setupValidToken("conflict@gmail.com", "충돌유저", null);

    User localUser =
        User.builder()
            .email("conflict@gmail.com")
            .password("encodedPassword")
            .nickname("충돌유저")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();

    given(userRepository.findByEmail("conflict@gmail.com")).willReturn(Optional.of(localUser));

    assertThatThrownBy(() -> authService.googleLogin(new GoogleLoginRequestDto("valid-credential")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.OAUTH_PROVIDER_CONFLICT));
  }

  @Test
  @DisplayName("Google name이 null이면 nickname을 email로 저장")
  void googleLogin_nullName_usesEmailAsNickname() {
    setupValidToken("nonick@gmail.com", null, null);

    given(userRepository.findByEmail("nonick@gmail.com")).willReturn(Optional.empty());
    given(userRepository.findByEmailAndProvider("nonick@gmail.com", Provider.GOOGLE))
        .willReturn(Optional.empty());

    given(userRepository.save(any(User.class)))
        .willAnswer(
            invocation -> {
              User saved = invocation.getArgument(0);
              // nickname이 email과 동일한지 검증
              assertThat(saved.getNickname()).isEqualTo("nonick@gmail.com");
              return saved;
            });

    authService.googleLogin(new GoogleLoginRequestDto("valid-credential"));

    verify(userRepository).save(any(User.class));
  }
}
