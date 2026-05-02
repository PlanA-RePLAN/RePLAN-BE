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
import org.springframework.web.client.RestClient;
import plana.replan.domain.auth.dto.GoogleLoginRequestDto;
import plana.replan.domain.auth.dto.OAuthLoginResponseDto;
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
  @Mock private RestClient restClient;

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

  private void setupValidToken(String email) {
    try {
      given(googleIdTokenVerifier.verify(anyString())).willReturn(mockIdToken);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    given(mockIdToken.getPayload()).willReturn(mockPayload);
    given(mockPayload.getEmailVerified()).willReturn(true);
    given(mockPayload.getEmail()).willReturn(email);
  }

  @Test
  @DisplayName("신규 Google 유저 최초 로그인 시 tempToken 반환")
  void googleLogin_newUser_returnsTempToken() {
    setupValidToken("new@gmail.com");

    given(userRepository.findByEmail("new@gmail.com")).willReturn(Optional.empty());
    given(userRepository.findByEmailAndProvider("new@gmail.com", Provider.GOOGLE))
        .willReturn(Optional.empty());

    OAuthLoginResponseDto result =
        authService.googleLogin(new GoogleLoginRequestDto("valid-credential"));

    assertThat(result.isNewUser()).isTrue();
    assertThat(result.getTempToken()).isNotNull();
    assertThat(result.getAccessToken()).isNull();
    verify(userRepository, never()).save(any(User.class));
    verify(valueOperations).set(anyString(), anyString(), any(Long.class), any(TimeUnit.class));
  }

  @Test
  @DisplayName("기존 Google 유저 재로그인 시 JWT 반환")
  void googleLogin_existingUser_returnsJwt() {
    setupValidToken("existing@gmail.com");

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

    OAuthLoginResponseDto result =
        authService.googleLogin(new GoogleLoginRequestDto("valid-credential"));

    assertThat(result.isNewUser()).isFalse();
    assertThat(result.getAccessToken()).isEqualTo("access-token");
    assertThat(result.getTempToken()).isNull();
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
    setupValidToken("conflict@gmail.com");

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
}
