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
import org.springframework.test.util.ReflectionTestUtils;
import plana.replan.domain.auth.apple.AppleAuthClient;
import plana.replan.domain.auth.apple.AppleIdTokenPayload;
import plana.replan.domain.auth.apple.AppleTokenResponse;
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
  private static final String SUB = "apple-sub-001";

  @BeforeEach
  void setUp() {
    given(redisTemplate.opsForValue()).willReturn(valueOperations);
    given(jwtUtil.generateAccessToken(anyString(), anyString(), any())).willReturn("access-token");
    given(jwtUtil.generateRefreshToken(anyString())).willReturn("refresh-token");
    given(jwtUtil.getRefreshExpiration()).willReturn(604800000L);
    // 기본: 이메일이 포함된 토큰(최초 인증 또는 웹). 재로그인 케이스는 각 테스트에서 재정의한다.
    given(appleTokenVerifier.verify(anyString()))
        .willReturn(new AppleIdTokenPayload(EMAIL, AUD, SUB));
    given(appleAuthClient.exchangeRefreshToken(eq(AUD), anyString()))
        .willReturn(new AppleTokenResponse("apple-refresh-token", SUB));
  }

  private AppleLoginRequestDto request() {
    return new AppleLoginRequestDto("id-token", "auth-code");
  }

  private User appleUser(String email) {
    User user =
        User.builder()
            .email(email)
            .nickname("apple")
            .role(Role.ROLE_USER)
            .provider(Provider.APPLE)
            .build();
    ReflectionTestUtils.setField(user, "id", 1L);
    return user;
  }

  @Test
  @DisplayName("sub로 찾은 기존 애플 유저면 토큰쌍을 발급하고 refresh token을 userId 키에 저장한다")
  void existingUserBySub() {
    User user = appleUser(EMAIL);
    ReflectionTestUtils.setField(user, "appleSub", SUB);
    given(userRepository.findByAppleSub(SUB)).willReturn(Optional.of(user));

    OAuthLoginResponseDto res = authService.appleLogin(request());

    assertThat(res.getAccessToken()).isEqualTo("access-token");
    verify(valueOperations)
        .set(eq("apple:refresh:" + user.getId()), eq(AUD + "|apple-refresh-token"));
  }

  @Test
  @DisplayName("재로그인이라 이메일이 없어도 sub로 사용자를 찾아 로그인된다")
  void reloginWithoutEmail() {
    given(appleTokenVerifier.verify(anyString()))
        .willReturn(new AppleIdTokenPayload(null, AUD, SUB));
    User user = appleUser(EMAIL);
    ReflectionTestUtils.setField(user, "appleSub", SUB);
    given(userRepository.findByAppleSub(SUB)).willReturn(Optional.of(user));

    OAuthLoginResponseDto res = authService.appleLogin(request());

    assertThat(res.getAccessToken()).isEqualTo("access-token");
  }

  @Test
  @DisplayName("sub 저장 전 가입한 기존 애플 유저는 이메일로 찾아 sub를 채워 넣고(이관) 로그인된다")
  void backfillSubForExistingUser() {
    User user = appleUser(EMAIL); // appleSub 아직 없음
    given(userRepository.findByAppleSub(SUB)).willReturn(Optional.empty());
    given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));

    OAuthLoginResponseDto res = authService.appleLogin(request());

    assertThat(res.getAccessToken()).isEqualTo("access-token");
    assertThat(user.getAppleSub()).isEqualTo(SUB);
    verify(valueOperations)
        .set(eq("apple:refresh:" + user.getId()), eq(AUD + "|apple-refresh-token"));
  }

  @Test
  @DisplayName("신규 유저면 tempToken을 발급하고 refresh token과 sub를 email 임시 키에 저장한다")
  void newUser() {
    given(userRepository.findByAppleSub(SUB)).willReturn(Optional.empty());
    given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

    OAuthLoginResponseDto res = authService.appleLogin(request());

    assertThat(res.getTempToken()).isNotBlank();
    verify(valueOperations)
        .set(eq("apple-refresh-temp:" + EMAIL), eq(AUD + "|apple-refresh-token"), eq(300L), any());
    verify(valueOperations).set(eq("apple-sub-temp:" + EMAIL), eq(SUB), eq(300L), any());
  }

  @Test
  @DisplayName("우리 DB에 없는데 이메일도 없는 예외 상태면 가입을 막고 토큰 무효 예외")
  void newUserWithoutEmailFails() {
    given(appleTokenVerifier.verify(anyString()))
        .willReturn(new AppleIdTokenPayload(null, AUD, SUB));
    given(userRepository.findByAppleSub(SUB)).willReturn(Optional.empty());

    assertThatThrownBy(() -> authService.appleLogin(request()))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.APPLE_TOKEN_INVALID);
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
    given(userRepository.findByAppleSub(SUB)).willReturn(Optional.empty());
    given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(google));

    assertThatThrownBy(() -> authService.appleLogin(request()))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.OAUTH_PROVIDER_CONFLICT);
  }

  @Test
  @DisplayName("신분증과 인가코드의 sub가 다르면(다른 사용자) 토큰 무효 예외")
  void subMismatch() {
    given(userRepository.findByAppleSub(SUB)).willReturn(Optional.empty());
    given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());
    given(appleAuthClient.exchangeRefreshToken(eq(AUD), anyString()))
        .willReturn(new AppleTokenResponse("apple-refresh-token", "different-sub"));

    assertThatThrownBy(() -> authService.appleLogin(request()))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.APPLE_TOKEN_INVALID);
  }

  @Test
  @DisplayName("교환 응답에 sub가 없으면(검증 불가) 토큰 무효 예외")
  void subMissing() {
    given(userRepository.findByAppleSub(SUB)).willReturn(Optional.empty());
    given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());
    given(appleAuthClient.exchangeRefreshToken(eq(AUD), anyString()))
        .willReturn(new AppleTokenResponse("apple-refresh-token", null));

    assertThatThrownBy(() -> authService.appleLogin(request()))
        .isInstanceOf(CustomException.class)
        .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.APPLE_TOKEN_INVALID);
  }
}
