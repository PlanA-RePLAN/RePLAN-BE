package plana.replan.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
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
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import plana.replan.domain.auth.dto.LoginResponseDto;
import plana.replan.domain.auth.dto.NaverLoginRequestDto;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;
import plana.replan.global.jwt.JwtUtil;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceNaverLoginTest {

  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @Mock private JwtUtil jwtUtil;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private GoogleIdTokenVerifier googleIdTokenVerifier;
  @Mock private RestTemplate restTemplate;

  @InjectMocks private AuthService authService;

  @Mock private ValueOperations<String, String> valueOperations;

  @BeforeEach
  void setUp() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(jwtUtil.generateAccessToken(anyString(), anyString(), any())).willReturn("access-token");
    given(jwtUtil.generateRefreshToken(anyString())).willReturn("refresh-token");
    given(jwtUtil.getRefreshExpiration()).willReturn(604800000L);
  }

  private void setupValidNaverResponse(String email, String name) {
    Map<String, Object> response = new HashMap<>();
    response.put("id", "naver-user-id");
    response.put("email", email);
    response.put("name", name);

    Map<String, Object> body = new HashMap<>();
    body.put("resultcode", "00");
    body.put("message", "success");
    body.put("response", response);

    given(
            restTemplate.exchange(
                eq("https://openapi.naver.com/v1/nid/me"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)))
        .willReturn(ResponseEntity.ok(body));
  }

  @Test
  @DisplayName("신규 Naver 유저 최초 로그인 시 자동 회원가입 후 토큰 반환")
  void naverLogin_newUser_success() {
    setupValidNaverResponse("new@naver.com", "신규유저");

    given(userRepository.findByEmail("new@naver.com")).willReturn(Optional.empty());
    given(userRepository.findByEmailAndProvider("new@naver.com", Provider.NAVER))
        .willReturn(Optional.empty());

    User savedUser =
        User.builder()
            .email("new@naver.com")
            .nickname("신규유저")
            .role(Role.ROLE_USER)
            .provider(Provider.NAVER)
            .build();
    given(userRepository.save(any(User.class))).willReturn(savedUser);

    LoginResponseDto result =
        authService.naverLogin(new NaverLoginRequestDto("valid-access-token"));

    assertThat(result.getAccessToken()).isEqualTo("access-token");
    assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
    verify(userRepository).save(any(User.class));
    verify(valueOperations).set(anyString(), anyString(), any(Long.class), any(TimeUnit.class));
  }

  @Test
  @DisplayName("기존 Naver 유저 재로그인 시 save 호출 없이 토큰 반환")
  void naverLogin_existingUser_success() {
    setupValidNaverResponse("existing@naver.com", "기존유저");

    User existingUser =
        User.builder()
            .email("existing@naver.com")
            .nickname("기존유저")
            .role(Role.ROLE_USER)
            .provider(Provider.NAVER)
            .build();

    given(userRepository.findByEmail("existing@naver.com")).willReturn(Optional.of(existingUser));
    given(userRepository.findByEmailAndProvider("existing@naver.com", Provider.NAVER))
        .willReturn(Optional.of(existingUser));

    LoginResponseDto result =
        authService.naverLogin(new NaverLoginRequestDto("valid-access-token"));

    assertThat(result.getAccessToken()).isEqualTo("access-token");
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  @DisplayName("네이버 API resultcode가 00이 아닌 경우: NAVER_TOKEN_INVALID 예외")
  void naverLogin_invalidResultcode_throws() {
    Map<String, Object> body = new HashMap<>();
    body.put("resultcode", "024");
    body.put("message", "Authentication failed");

    given(
            restTemplate.exchange(
                eq("https://openapi.naver.com/v1/nid/me"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)))
        .willReturn(ResponseEntity.ok(body));

    assertThatThrownBy(() -> authService.naverLogin(new NaverLoginRequestDto("invalid-token")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.NAVER_TOKEN_INVALID));
  }

  @Test
  @DisplayName("네이버 API 호출 중 예외 발생 시: NAVER_TOKEN_INVALID 예외")
  void naverLogin_apiCallThrows_throws() {
    given(
            restTemplate.exchange(
                eq("https://openapi.naver.com/v1/nid/me"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)))
        .willThrow(new RestClientException("network error"));

    assertThatThrownBy(() -> authService.naverLogin(new NaverLoginRequestDto("bad-token")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.NAVER_TOKEN_INVALID));
  }

  @Test
  @DisplayName("이메일이 null인 경우 (사용자 동의 안 함): NAVER_TOKEN_INVALID 예외")
  void naverLogin_nullEmail_throws() {
    setupValidNaverResponse(null, "이름있는유저");

    assertThatThrownBy(() -> authService.naverLogin(new NaverLoginRequestDto("valid-token")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.NAVER_TOKEN_INVALID));
  }

  @Test
  @DisplayName("name이 null이면 nickname을 email prefix(@앞)로 저장")
  void naverLogin_nullName_usesEmailPrefixAsNickname() {
    setupValidNaverResponse("nonick@naver.com", null);

    given(userRepository.findByEmail("nonick@naver.com")).willReturn(Optional.empty());
    given(userRepository.findByEmailAndProvider("nonick@naver.com", Provider.NAVER))
        .willReturn(Optional.empty());

    given(userRepository.save(any(User.class)))
        .willAnswer(
            invocation -> {
              User saved = invocation.getArgument(0);
              assertThat(saved.getNickname()).isEqualTo("nonick");
              return saved;
            });

    authService.naverLogin(new NaverLoginRequestDto("valid-token"));

    verify(userRepository).save(any(User.class));
  }

  @Test
  @DisplayName("동일 이메일이 LOCAL Provider로 가입된 경우: OAUTH_PROVIDER_CONFLICT 예외")
  void naverLogin_providerConflict_throws() {
    setupValidNaverResponse("conflict@naver.com", "충돌유저");

    User localUser =
        User.builder()
            .email("conflict@naver.com")
            .password("encodedPassword")
            .nickname("충돌유저")
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();

    given(userRepository.findByEmail("conflict@naver.com")).willReturn(Optional.of(localUser));

    assertThatThrownBy(() -> authService.naverLogin(new NaverLoginRequestDto("valid-token")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.OAUTH_PROVIDER_CONFLICT));
  }
}
