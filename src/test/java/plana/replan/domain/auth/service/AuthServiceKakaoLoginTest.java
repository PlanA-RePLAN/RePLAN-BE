package plana.replan.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import java.util.HashMap;
import java.util.Map;
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
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import plana.replan.domain.auth.dto.KakaoLoginRequestDto;
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
class AuthServiceKakaoLoginTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtUtil jwtUtil;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private GoogleIdTokenVerifier googleIdTokenVerifier;
  @Mock private RestClient restClient;

  @SuppressWarnings("rawtypes")
  @Mock
  private RestClient.RequestHeadersUriSpec requestHeadersUriSpec;

  @SuppressWarnings("rawtypes")
  @Mock
  private RestClient.RequestHeadersSpec requestHeadersSpec;

  @Mock private RestClient.ResponseSpec responseSpec;

  @InjectMocks private AuthService authService;

  @Mock private ValueOperations<String, String> valueOperations;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(jwtUtil.generateAccessToken(anyString(), anyString(), any())).willReturn("access-token");
    given(jwtUtil.generateRefreshToken(anyString())).willReturn("refresh-token");
    given(jwtUtil.getRefreshExpiration()).willReturn(604800000L);

    lenient().when(restClient.get()).thenReturn(requestHeadersUriSpec);
    lenient().when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
    lenient()
        .when(requestHeadersSpec.header(anyString(), anyString()))
        .thenReturn(requestHeadersSpec);
    lenient().when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
  }

  private void setupValidKakaoResponse(String email) {
    Map<String, Object> kakaoAccount = new HashMap<>();
    kakaoAccount.put("email", email);

    Map<String, Object> body = new HashMap<>();
    body.put("id", 1234567890L);
    body.put("kakao_account", kakaoAccount);

    given(responseSpec.body(Map.class)).willReturn(body);
  }

  @Test
  @DisplayName("신규 Kakao 유저 최초 로그인 시 tempToken 반환")
  void kakaoLogin_newUser_returnsTempToken() {
    setupValidKakaoResponse("new@kakao.com");

    given(userRepository.findByEmail("new@kakao.com")).willReturn(Optional.empty());
    given(userRepository.findByEmailAndProvider("new@kakao.com", Provider.KAKAO))
        .willReturn(Optional.empty());

    OAuthLoginResponseDto result =
        authService.kakaoLogin(new KakaoLoginRequestDto("valid-access-token"));

    assertThat(result.isNewUser()).isTrue();
    assertThat(result.getTempToken()).isNotNull();
    assertThat(result.getAccessToken()).isNull();
    verify(userRepository, never()).save(any(User.class));
    verify(valueOperations).set(anyString(), anyString(), any(Long.class), any(TimeUnit.class));
  }

  @Test
  @DisplayName("기존 Kakao 유저 재로그인 시 JWT 반환")
  void kakaoLogin_existingUser_returnsJwt() {
    setupValidKakaoResponse("existing@kakao.com");

    User existingUser =
        User.builder()
            .email("existing@kakao.com")
            .nickname("기존유저")
            .role(Role.ROLE_USER)
            .provider(Provider.KAKAO)
            .build();

    given(userRepository.findByEmail("existing@kakao.com")).willReturn(Optional.of(existingUser));
    given(userRepository.findByEmailAndProvider("existing@kakao.com", Provider.KAKAO))
        .willReturn(Optional.of(existingUser));

    OAuthLoginResponseDto result =
        authService.kakaoLogin(new KakaoLoginRequestDto("valid-access-token"));

    assertThat(result.isNewUser()).isFalse();
    assertThat(result.getAccessToken()).isEqualTo("access-token");
    assertThat(result.getTempToken()).isNull();
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  @DisplayName("카카오 API 타임아웃 발생 시: OAUTH_SERVER_UNAVAILABLE 예외")
  void kakaoLogin_timeout_throwsOAuthServerUnavailable() {
    given(responseSpec.body(Map.class)).willThrow(new ResourceAccessException("Read timed out"));

    assertThatThrownBy(() -> authService.kakaoLogin(new KakaoLoginRequestDto("valid-token")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.OAUTH_SERVER_UNAVAILABLE));
  }

  @Test
  @DisplayName("카카오 API 호출 중 예외 발생 시: KAKAO_TOKEN_INVALID 예외")
  void kakaoLogin_apiCallThrows_throws() {
    given(responseSpec.body(Map.class)).willThrow(new RestClientException("network error"));

    assertThatThrownBy(() -> authService.kakaoLogin(new KakaoLoginRequestDto("bad-token")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.KAKAO_TOKEN_INVALID));
  }

  @Test
  @DisplayName("응답 body가 null이거나 kakao_account가 null인 경우: KAKAO_TOKEN_INVALID 예외")
  void kakaoLogin_nullKakaoAccount_throws() {
    Map<String, Object> body = new HashMap<>();
    body.put("id", 1234567890L);

    given(responseSpec.body(Map.class)).willReturn(body);

    assertThatThrownBy(() -> authService.kakaoLogin(new KakaoLoginRequestDto("valid-token")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.KAKAO_TOKEN_INVALID));
  }

  @Test
  @DisplayName("이메일이 null인 경우 (사용자 동의 안 함): KAKAO_TOKEN_INVALID 예외")
  void kakaoLogin_nullEmail_throws() {
    setupValidKakaoResponse(null);

    assertThatThrownBy(() -> authService.kakaoLogin(new KakaoLoginRequestDto("valid-token")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.KAKAO_TOKEN_INVALID));
  }

  @Test
  @DisplayName("동일 이메일이 LOCAL Provider로 가입된 경우: OAUTH_PROVIDER_CONFLICT 예외")
  void kakaoLogin_providerConflict_throws() {
    setupValidKakaoResponse("conflict@kakao.com");

    User localUser =
        User.builder()
            .email("conflict@kakao.com")
            .password("encodedPassword")
            .nickname("충돌유저")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();

    given(userRepository.findByEmail("conflict@kakao.com")).willReturn(Optional.of(localUser));

    assertThatThrownBy(() -> authService.kakaoLogin(new KakaoLoginRequestDto("valid-token")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.OAUTH_PROVIDER_CONFLICT));
  }
}
