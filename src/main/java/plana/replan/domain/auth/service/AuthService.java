package plana.replan.domain.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import plana.replan.domain.auth.apple.AppleAuthClient;
import plana.replan.domain.auth.apple.AppleIdTokenPayload;
import plana.replan.domain.auth.apple.AppleTokenResponse;
import plana.replan.domain.auth.apple.AppleTokenVerifier;
import plana.replan.domain.auth.dto.AppleLoginRequestDto;
import plana.replan.domain.auth.dto.GoogleLoginRequestDto;
import plana.replan.domain.auth.dto.KakaoLoginRequestDto;
import plana.replan.domain.auth.dto.LoginRequestDto;
import plana.replan.domain.auth.dto.LoginResponseDto;
import plana.replan.domain.auth.dto.NaverLoginRequestDto;
import plana.replan.domain.auth.dto.OAuthLoginResponseDto;
import plana.replan.domain.auth.dto.OAuthRegisterRequestDto;
import plana.replan.domain.auth.dto.SignUpRequestDto;
import plana.replan.domain.tag.service.TagService;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;
import plana.replan.global.jwt.JwtErrorCode;
import plana.replan.global.jwt.JwtUtil;
import plana.replan.global.s3.S3Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final StringRedisTemplate redisTemplate;
  private final GoogleIdTokenVerifier googleIdTokenVerifier;
  private final RestClient restClient;
  private final S3Service s3Service;
  private final TagService tagService;
  private final AppleTokenVerifier appleTokenVerifier;
  private final AppleAuthClient appleAuthClient;

  @Transactional
  public void signUp(SignUpRequestDto request) {

    // 1. 이메일 중복 체크
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new CustomException(UserErrorCode.DUPLICATE_EMAIL);
    }

    // 2. 비밀번호 암호화
    String encodedPassword = passwordEncoder.encode(request.getPassword());

    // 3. User 엔티티 생성 및 저장
    User user =
        User.builder()
            .email(request.getEmail())
            .password(encodedPassword)
            .nickname(request.getNickname())
            .role(Role.ROLE_USER)
            .provider(Provider.LOCAL)
            .build();

    // 마케팅 정보 수신 동의(선택). 동의했으면 여부와 함께 동의 시각이 기록된다.
    user.updateMarketingAgreed(request.getAgreeMarketing());

    userRepository.save(user);
    tagService.createDefaultTags(user);
  }

  @Transactional(readOnly = true)
  public LoginResponseDto login(LoginRequestDto request) {

    // 1. 이메일로 유저 조회
    User user =
        userRepository
            .findByEmail(request.getEmail())
            .orElseThrow(() -> new CustomException(UserErrorCode.LOGIN_FAILED));

    // 2. 비밀번호 검증
    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      throw new CustomException(UserErrorCode.LOGIN_FAILED);
    }

    // 3. JWT 발급
    return issueTokenPair(user);
  }

  public LoginResponseDto reissue(String refreshToken) {

    // 1. JWT 서명 검증 및 이메일 추출
    jwtUtil.validateToken(refreshToken);
    String email = jwtUtil.getEmail(refreshToken);

    // 2. Redis에서 Refresh Token 조회
    String savedRefreshToken = redisTemplate.opsForValue().get("refresh:" + email);

    // 3. Redis에 없으면 (로그아웃 or 만료)
    if (savedRefreshToken == null) {
      throw new CustomException(JwtErrorCode.REFRESH_TOKEN_NOT_FOUND);
    }

    // 4. 클라이언트가 보낸 토큰이랑 Redis에 있는 토큰 비교
    if (!savedRefreshToken.equals(refreshToken)) {
      throw new CustomException(JwtErrorCode.INVALID_REFRESH_TOKEN);
    }

    // 5. 유저 조회 (role 가져오기 위해)
    User user =
        userRepository
            .findByEmail(email)
            .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

    // 6. 새 JWT 발급 (Rotation)
    return issueTokenPair(user);
  }

  public void logout(String accessToken) {

    // 1. Access Token 검증 및 이메일 추출
    jwtUtil.validateToken(accessToken);
    String email = jwtUtil.getEmail(accessToken);

    // 2. Redis에서 Refresh Token 삭제
    redisTemplate.delete("refresh:" + email);
  }

  @Transactional
  public LoginResponseDto register(OAuthRegisterRequestDto request, String tempToken) {

    // 1. Redis에서 tempToken 조회
    String value = redisTemplate.opsForValue().get("oauth-temp:" + tempToken);
    if (value == null) {
      throw new CustomException(UserErrorCode.INVALID_TEMP_TOKEN);
    }

    // 2. email, provider 파싱
    String[] parts = value.split(":");
    String email = parts[0];
    Provider provider = Provider.valueOf(parts[1]);

    // 3. 닉네임 중복 확인
    if (userRepository.existsByNickname(request.getNickname())) {
      throw new CustomException(UserErrorCode.DUPLICATE_NICKNAME);
    }

    // 4. 이미지 있으면 S3 temp → confirmed 이동
    String profileImageUrl = null;
    if (request.getS3Key() != null && !request.getS3Key().isBlank()) {
      profileImageUrl = s3Service.moveToConfirmed(request.getS3Key());
    }

    // 5. 유저 생성 및 저장
    User user =
        User.builder()
            .email(email)
            .nickname(request.getNickname())
            .role(Role.ROLE_USER)
            .provider(provider)
            .profileImage(profileImageUrl)
            .build();

    // 마케팅 정보 수신 동의(선택). 동의했으면 여부와 함께 동의 시각이 기록된다.
    user.updateMarketingAgreed(request.getAgreeMarketing());

    user = userRepository.save(user);
    tagService.createDefaultTags(user);

    // 5-1. 애플이면 임시 저장된 refresh token을 userId 키로 옮긴다
    if (provider == Provider.APPLE) {
      String appleRefresh = redisTemplate.opsForValue().get("apple-refresh-temp:" + email);
      // 임시 키가 없으면(만료 등) 탈퇴 시 애플 연동 해제를 못 하므로, 가입 상태 불일치로 보고 재로그인을 요구한다.
      if (appleRefresh == null) {
        throw new CustomException(UserErrorCode.INVALID_TEMP_TOKEN);
      }
      redisTemplate.opsForValue().set("apple:refresh:" + user.getId(), appleRefresh);
      redisTemplate.delete("apple-refresh-temp:" + email);

      // 애플 고유번호(sub)를 유저에 저장한다. 다음 로그인부턴 이메일 없이 sub로 찾을 수 있다.
      String appleSub = redisTemplate.opsForValue().get("apple-sub-temp:" + email);
      if (appleSub != null) {
        user.linkAppleSub(appleSub);
        redisTemplate.delete("apple-sub-temp:" + email);
      }
    }

    // 6. tempToken 삭제
    redisTemplate.delete("oauth-temp:" + tempToken);

    // 7. JWT 발급
    return issueTokenPair(user);
  }

  public boolean checkNickname(String nickname) {
    return !userRepository.existsByNickname(nickname);
  }

  @Transactional
  public OAuthLoginResponseDto googleLogin(GoogleLoginRequestDto request) {

    // 1. Google ID Token 검증 (서명, aud, iss, exp를 라이브러리가 자동으로 검증)
    GoogleIdToken idToken = verifyGoogleIdToken(request.getCredential());

    // 2. 페이로드에서 사용자 정보 추출
    GoogleIdToken.Payload payload = idToken.getPayload();

    // 3. 구글이 이메일 인증을 완료한 계정인지 확인
    boolean emailVerified = Boolean.TRUE.equals(payload.getEmailVerified());
    if (!emailVerified) {
      throw new CustomException(UserErrorCode.GOOGLE_TOKEN_INVALID);
    }

    String email = payload.getEmail();

    // 4. 같은 이메일로 이미 다른 방식으로 가입된 경우 차단
    Optional<User> existingUser = userRepository.findByEmail(email);
    if (existingUser.isPresent() && existingUser.get().getProvider() != Provider.GOOGLE) {
      throw new CustomException(UserErrorCode.OAUTH_PROVIDER_CONFLICT);
    }

    // 5. 기존유저: JWT 발급 / 신규유저: tempToken 발급
    return userRepository
        .findByEmailAndProvider(email, Provider.GOOGLE)
        .map(
            user -> {
              LoginResponseDto tokens = issueTokenPair(user);
              return OAuthLoginResponseDto.existingUser(
                  tokens.getAccessToken(), tokens.getRefreshToken());
            })
        .orElseGet(() -> OAuthLoginResponseDto.newUser(issueTempToken(email, Provider.GOOGLE)));
  }

  @Transactional
  public OAuthLoginResponseDto naverLogin(NaverLoginRequestDto request) {

    // 1. 네이버 프로필 API 호출
    Map<String, Object> naverProfile = fetchNaverUserInfo(request.getAccessToken());

    // 2. 이메일 추출 (사용자 동의 없으면 null)
    String email = (String) naverProfile.get("email");
    if (email == null) {
      throw new CustomException(UserErrorCode.NAVER_TOKEN_INVALID);
    }

    // 3. 같은 이메일로 이미 다른 방식으로 가입된 경우 차단
    Optional<User> existingUser = userRepository.findByEmail(email);
    if (existingUser.isPresent() && existingUser.get().getProvider() != Provider.NAVER) {
      throw new CustomException(UserErrorCode.OAUTH_PROVIDER_CONFLICT);
    }

    // 4. 기존유저: JWT 발급 / 신규유저: tempToken 발급
    return userRepository
        .findByEmailAndProvider(email, Provider.NAVER)
        .map(
            user -> {
              LoginResponseDto tokens = issueTokenPair(user);
              return OAuthLoginResponseDto.existingUser(
                  tokens.getAccessToken(), tokens.getRefreshToken());
            })
        .orElseGet(() -> OAuthLoginResponseDto.newUser(issueTempToken(email, Provider.NAVER)));
  }

  @Transactional
  public OAuthLoginResponseDto kakaoLogin(KakaoLoginRequestDto request) {

    // 1. 카카오 사용자 정보 API 호출
    Map<String, Object> kakaoProfile = fetchKakaoUserInfo(request.getAccessToken());

    // 2. 이메일 추출 (사용자 동의 없으면 null)
    String email = (String) kakaoProfile.get("email");
    if (email == null) {
      throw new CustomException(UserErrorCode.KAKAO_TOKEN_INVALID);
    }

    // 3. 같은 이메일로 이미 다른 방식으로 가입된 경우 차단
    Optional<User> existingUser = userRepository.findByEmail(email);
    if (existingUser.isPresent() && existingUser.get().getProvider() != Provider.KAKAO) {
      throw new CustomException(UserErrorCode.OAUTH_PROVIDER_CONFLICT);
    }

    // 4. 기존유저: JWT 발급 / 신규유저: tempToken 발급
    return userRepository
        .findByEmailAndProvider(email, Provider.KAKAO)
        .map(
            user -> {
              LoginResponseDto tokens = issueTokenPair(user);
              return OAuthLoginResponseDto.existingUser(
                  tokens.getAccessToken(), tokens.getRefreshToken());
            })
        .orElseGet(() -> OAuthLoginResponseDto.newUser(issueTempToken(email, Provider.KAKAO)));
  }

  @Transactional
  public OAuthLoginResponseDto appleLogin(AppleLoginRequestDto request) {

    // 1. identityToken 검증 → sub(고유 식별번호), 이메일(최초 인증 때만 옴), aud(=client_id) 추출
    AppleIdTokenPayload payload = appleTokenVerifier.verify(request.getIdentityToken());
    String sub = payload.sub();
    String email = payload.email(); // 네이티브 재로그인 시 null일 수 있음
    String clientId = payload.aud();

    // 2. 사용자 조회: 애플이 항상 주는 sub로 먼저 찾는다.
    Optional<User> found = userRepository.findByAppleSub(sub);

    // 2-1. sub로 못 찾았고 이메일이 있으면 이메일로 찾아본다. (Apple 네트워크 호출 전에 확인)
    //      - sub 저장 전에 가입한 기존 애플 회원이면 sub를 채워 넣어 이관한다.
    //      - 이미 다른 방식(카카오/구글/네이버)으로 가입한 이메일이면 충돌로 막는다.
    if (found.isEmpty() && email != null) {
      Optional<User> byEmail = userRepository.findByEmail(email);
      if (byEmail.isPresent()) {
        User u = byEmail.get();
        if (u.getProvider() != Provider.APPLE) {
          throw new CustomException(UserErrorCode.OAUTH_PROVIDER_CONFLICT);
        }
        u.linkAppleSub(sub);
        found = byEmail;
      }
    }

    // 2-2. sub로도 못 찾고 이메일도 없으면(우리 DB에 없는데 재로그인이라 이메일도 안 온 예외 상태)
    //      찾을 수도 새로 만들 수도 없다. 애플 토큰 교환(refresh token 발급) '전에' 미리 거절해서,
    //      저장도 철회도 못 하는 고아 refresh token이 생기지 않게 한다.
    if (found.isEmpty() && email == null) {
      throw new CustomException(UserErrorCode.APPLE_TOKEN_INVALID);
    }

    // 3. authorizationCode 교환 → refresh token 확보(탈퇴 시 철회용)
    AppleTokenResponse tokenResponse =
        appleAuthClient.exchangeRefreshToken(clientId, request.getAuthorizationCode());

    // 3-1. 신분증(identityToken)과 인가코드가 같은 사용자에게서 왔는지 확인(bind).
    //      교환 응답의 sub가 없거나(파싱 불가) 신분증 sub와 다르면 거부한다.
    //      이미 발급받은 애플 refresh token은 저장하지 않고 버리므로, 고아 연동이 남지 않게 best-effort로 철회한다.
    if (tokenResponse.sub() == null || !tokenResponse.sub().equals(sub)) {
      try {
        appleAuthClient.revoke(clientId, tokenResponse.refreshToken());
      } catch (Exception e) {
        log.warn("bind 실패로 거부된 애플 토큰 철회 실패(무시하고 진행)", e);
      }
      throw new CustomException(UserErrorCode.APPLE_TOKEN_INVALID);
    }

    String storedValue = clientId + "|" + tokenResponse.refreshToken();

    // 4. 기존 유저: JWT 발급 + refresh token을 userId 키에 저장
    if (found.isPresent()) {
      User user = found.get();
      redisTemplate.opsForValue().set("apple:refresh:" + user.getId(), storedValue);
      LoginResponseDto tokens = issueTokenPair(user);
      return OAuthLoginResponseDto.existingUser(tokens.getAccessToken(), tokens.getRefreshToken());
    }

    // 5. 신규 유저: 여기 도달하면 email은 반드시 있다(이메일 없는 경우는 위 2-2에서 이미 걸러짐).
    //    가입 완료(register) 시점에 옮겨 쓸 수 있게 refresh token과 sub를 email 임시 키로 저장한다.
    redisTemplate
        .opsForValue()
        .set("apple-refresh-temp:" + email, storedValue, 300, TimeUnit.SECONDS);
    redisTemplate.opsForValue().set("apple-sub-temp:" + email, sub, 300, TimeUnit.SECONDS);
    return OAuthLoginResponseDto.newUser(issueTempToken(email, Provider.APPLE));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> fetchKakaoUserInfo(String accessToken) {
    try {
      Map<String, Object> body =
          restClient
              .get()
              .uri("https://kapi.kakao.com/v2/user/me")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
              .retrieve()
              .body(Map.class);

      if (body == null) {
        throw new CustomException(UserErrorCode.KAKAO_TOKEN_INVALID);
      }

      Map<String, Object> kakaoAccount = (Map<String, Object>) body.get("kakao_account");
      if (kakaoAccount == null) {
        throw new CustomException(UserErrorCode.KAKAO_TOKEN_INVALID);
      }

      Map<String, Object> profile = (Map<String, Object>) kakaoAccount.get("profile");

      Map<String, Object> result = new java.util.HashMap<>();
      result.put("email", kakaoAccount.get("email"));
      result.put("nickname", profile != null ? profile.get("nickname") : null);
      return result;
    } catch (CustomException e) {
      throw e;
    } catch (ResourceAccessException e) {
      throw new CustomException(UserErrorCode.OAUTH_SERVER_UNAVAILABLE);
    } catch (Exception e) {
      throw new CustomException(UserErrorCode.KAKAO_TOKEN_INVALID);
    }
  }

  private String issueTempToken(String email, Provider provider) {
    String token = UUID.randomUUID().toString();
    redisTemplate
        .opsForValue()
        .set("oauth-temp:" + token, email + ":" + provider.name(), 300, TimeUnit.SECONDS);
    return token;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> fetchNaverUserInfo(String accessToken) {
    try {
      Map<String, Object> body =
          restClient
              .get()
              .uri("https://openapi.naver.com/v1/nid/me")
              .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
              .retrieve()
              .body(Map.class);

      if (body == null || !"00".equals(body.get("resultcode"))) {
        throw new CustomException(UserErrorCode.NAVER_TOKEN_INVALID);
      }
      return (Map<String, Object>) body.get("response");
    } catch (CustomException e) {
      throw e;
    } catch (ResourceAccessException e) {
      throw new CustomException(UserErrorCode.OAUTH_SERVER_UNAVAILABLE);
    } catch (Exception e) {
      throw new CustomException(UserErrorCode.NAVER_TOKEN_INVALID);
    }
  }

  private GoogleIdToken verifyGoogleIdToken(String credential) {
    try {
      GoogleIdToken idToken = googleIdTokenVerifier.verify(credential);
      if (idToken == null) {
        throw new CustomException(UserErrorCode.GOOGLE_TOKEN_INVALID);
      }
      return idToken;
    } catch (CustomException e) {
      throw e;
    } catch (ResourceAccessException e) {
      throw new CustomException(UserErrorCode.OAUTH_SERVER_UNAVAILABLE);
    } catch (Exception e) {
      throw new CustomException(UserErrorCode.GOOGLE_TOKEN_INVALID);
    }
  }

  private LoginResponseDto issueTokenPair(User user) {
    String accessToken =
        jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name(), user.getId());
    String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());
    redisTemplate
        .opsForValue()
        .set(
            "refresh:" + user.getEmail(),
            refreshToken,
            jwtUtil.getRefreshExpiration(),
            TimeUnit.MILLISECONDS);
    return new LoginResponseDto(accessToken, refreshToken);
  }
}
