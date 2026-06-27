package plana.replan.domain.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.Optional;
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
import plana.replan.domain.auth.apple.AppleAuthClient;
import plana.replan.domain.auth.apple.AppleIdTokenPayload;
import plana.replan.domain.auth.apple.AppleTokenVerifier;
import plana.replan.domain.auth.dto.AppleLoginRequestDto;
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
class AuthServiceAppleLoginTest {

  @Mock private UserRepository userRepository;
  @Mock private JwtUtil jwtUtil;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private AppleTokenVerifier appleTokenVerifier;
  @Mock private AppleAuthClient appleAuthClient;
  @Mock private ValueOperations<String, String> valueOperations;

  @InjectMocks private AuthService authService;

  private static final String EMAIL = "apple-user@privaterelay.appleid.com";
  private static final String AUD = "com.replan.service";

  @BeforeEach
  void setUp() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(jwtUtil.generateAccessToken(anyString(), anyString(), any())).willReturn("access-token");
    given(jwtUtil.generateRefreshToken(anyString())).willReturn("refresh-token");
    given(jwtUtil.getRefreshExpiration()).willReturn(604800000L);
    given(appleTokenVerifier.verify(anyString())).willReturn(new AppleIdTokenPayload(EMAIL, AUD));
    given(appleAuthClient.exchangeRefreshToken(eq(AUD), anyString()))
        .willReturn("apple-refresh-token");
  }

  private AppleLoginRequestDto request() {
    return new AppleLoginRequestDto("id-token", "auth-code");
  }

  @Test
  @DisplayName("기존 애플 유저면 토큰쌍을 발급하고 refresh token을 userId 키에 저장한다")
  void existingUser() {
    User user =
        User.builder()
            .email(EMAIL)
            .nickname("apple")
            .role(Role.ROLE_USER)
            .provider(Provider.APPLE)
            .build();
    given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
    given(userRepository.findByEmailAndProvider(EMAIL, Provider.APPLE))
        .willReturn(Optional.of(user));

    OAuthLoginResponseDto res = authService.appleLogin(request());

    assertThat(res.getAccessToken()).isEqualTo("access-token");
    verify(valueOperations)
        .set(eq("apple:refresh:" + user.getId()), eq(AUD + "|apple-refresh-token"));
  }

  @Test
  @DisplayName("신규 유저면 tempToken을 발급하고 refresh token을 email 임시 키에 저장한다")
  void newUser() {
    given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());
    given(userRepository.findByEmailAndProvider(EMAIL, Provider.APPLE))
        .willReturn(Optional.empty());

    OAuthLoginResponseDto res = authService.appleLogin(request());

    assertThat(res.getTempToken()).isNotBlank();
    verify(valueOperations)
        .set(
            eq("apple-refresh-temp:" + EMAIL),
            eq(AUD + "|apple-refresh-token"),
            org.mockito.ArgumentMatchers.eq(300L),
            any());
  }

  @Test
  @DisplayName("같은 이메일이 다른 provider로 가입돼 있으면 충돌 예외")
  void providerConflict() {
    User google =
        User.builder()
            .email(EMAIL)
            .nickname("g")
            .role(Role.ROLE_USER)
            .provider(Provider.GOOGLE)
            .build();
    given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(google));

    assertThatThrownBy(() -> authService.appleLogin(request()))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.OAUTH_PROVIDER_CONFLICT);
  }
}
