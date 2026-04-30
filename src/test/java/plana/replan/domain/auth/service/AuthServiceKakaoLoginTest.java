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
import plana.replan.domain.auth.dto.KakaoLoginRequestDto;
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
class AuthServiceKakaoLoginTest {

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

  private void setupValidKakaoResponse(String email, String nickname, String profileImageUrl) {
    Map<String, Object> profile = new HashMap<>();
    profile.put("nickname", nickname);
    profile.put("profile_image_url", profileImageUrl);

    Map<String, Object> kakaoAccount = new HashMap<>();
    kakaoAccount.put("email", email);
    kakaoAccount.put("profile", profile);

    Map<String, Object> body = new HashMap<>();
    body.put("id", 1234567890L);
    body.put("kakao_account", kakaoAccount);

    given(
            restTemplate.exchange(
                eq("https://kapi.kakao.com/v2/user/me"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)))
        .willReturn(ResponseEntity.ok(body));
  }

  @Test
  @DisplayName("신규 Kakao 유저 최초 로그인 시 자동 회원가입 후 토큰 반환")
  void kakaoLogin_newUser_success() {
    setupValidKakaoResponse("new@kakao.com", "신규유저", "https://profile.url/photo.jpg");

    given(userRepository.findByEmail("new@kakao.com")).willReturn(Optional.empty());
    given(userRepository.findByEmailAndProvider("new@kakao.com", Provider.KAKAO))
        .willReturn(Optional.empty());

    User savedUser =
        User.builder()
            .email("new@kakao.com")
            .nickname("신규유저")
            .role(Role.ROLE_USER)
            .provider(Provider.KAKAO)
            .profileImage("https://profile.url/photo.jpg")
            .build();
    given(userRepository.save(any(User.class))).willReturn(savedUser);

    LoginResponseDto result =
        authService.kakaoLogin(new KakaoLoginRequestDto("valid-access-token"));

    assertThat(result.getAccessToken()).isEqualTo("access-token");
    assertThat(result.getRefreshToken()).isEqualTo("refresh-token");
    verify(userRepository).save(any(User.class));
    verify(valueOperations).set(anyString(), anyString(), any(Long.class), any(TimeUnit.class));
  }

  @Test
  @DisplayName("기존 Kakao 유저 재로그인 시 save 호출 없이 토큰 반환")
  void kakaoLogin_existingUser_success() {
    setupValidKakaoResponse("existing@kakao.com", "기존유저", null);

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

    LoginResponseDto result =
        authService.kakaoLogin(new KakaoLoginRequestDto("valid-access-token"));

    assertThat(result.getAccessToken()).isEqualTo("access-token");
    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  @DisplayName("카카오 API 호출 중 예외 발생 시: KAKAO_TOKEN_INVALID 예외")
  void kakaoLogin_apiCallThrows_throws() {
    given(
            restTemplate.exchange(
                eq("https://kapi.kakao.com/v2/user/me"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)))
        .willThrow(new RestClientException("network error"));

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
    // kakao_account 없음

    given(
            restTemplate.exchange(
                eq("https://kapi.kakao.com/v2/user/me"),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(Map.class)))
        .willReturn(ResponseEntity.ok(body));

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
    setupValidKakaoResponse(null, "이름있는유저", null);

    assertThatThrownBy(() -> authService.kakaoLogin(new KakaoLoginRequestDto("valid-token")))
        .isInstanceOf(CustomException.class)
        .satisfies(
            e ->
                assertThat(((CustomException) e).getErrorCode())
                    .isEqualTo(UserErrorCode.KAKAO_TOKEN_INVALID));
  }

  @Test
  @DisplayName("nickname이 null이면 nickname을 email prefix(@앞)로 저장")
  void kakaoLogin_nullNickname_usesEmailPrefixAsNickname() {
    setupValidKakaoResponse("nonick@kakao.com", null, null);

    given(userRepository.findByEmail("nonick@kakao.com")).willReturn(Optional.empty());
    given(userRepository.findByEmailAndProvider("nonick@kakao.com", Provider.KAKAO))
        .willReturn(Optional.empty());

    given(userRepository.save(any(User.class)))
        .willAnswer(
            invocation -> {
              User saved = invocation.getArgument(0);
              assertThat(saved.getNickname()).isEqualTo("nonick");
              return saved;
            });

    authService.kakaoLogin(new KakaoLoginRequestDto("valid-token"));

    verify(userRepository).save(any(User.class));
  }

  @Test
  @DisplayName("동일 이메일이 LOCAL Provider로 가입된 경우: OAUTH_PROVIDER_CONFLICT 예외")
  void kakaoLogin_providerConflict_throws() {
    setupValidKakaoResponse("conflict@kakao.com", "충돌유저", null);

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
