package plana.replan.domain.auth.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import plana.replan.domain.auth.dto.GoogleLoginRequestDto;
import plana.replan.domain.auth.dto.KakaoLoginRequestDto;
import plana.replan.domain.auth.dto.LoginRequestDto;
import plana.replan.domain.auth.dto.LoginResponseDto;
import plana.replan.domain.auth.dto.NaverLoginRequestDto;
import plana.replan.domain.auth.dto.SignUpRequestDto;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.domain.user.repository.UserRepository;
import plana.replan.global.exception.CustomException;
import plana.replan.global.jwt.JwtErrorCode;
import plana.replan.global.jwt.JwtUtil;

@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtUtil jwtUtil;
  private final StringRedisTemplate redisTemplate;
  private final GoogleIdTokenVerifier googleIdTokenVerifier;
  private final RestTemplate restTemplate;

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

    userRepository.save(user);
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
  public LoginResponseDto googleLogin(GoogleLoginRequestDto request) {

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
    String googleName = (String) payload.get("name");

    // 4. 같은 이메일로 이미 다른 방식으로 가입된 경우 차단
    Optional<User> existingUser = userRepository.findByEmail(email);
    if (existingUser.isPresent() && existingUser.get().getProvider() != Provider.GOOGLE) {
      throw new CustomException(UserErrorCode.OAUTH_PROVIDER_CONFLICT);
    }

    // 5. GOOGLE 유저 조회 (없으면 자동 회원가입)
    User user =
        userRepository
            .findByEmailAndProvider(email, Provider.GOOGLE)
            .orElseGet(() -> createNewGoogleUser(email, googleName));

    // 6. JWT 발급 (login()과 동일한 공통 메서드 사용)
    return issueTokenPair(user);
  }

  @Transactional
  public LoginResponseDto naverLogin(NaverLoginRequestDto request) {

    // 1. 네이버 프로필 API 호출
    Map<String, Object> naverProfile = fetchNaverUserInfo(request.getAccessToken());

    // 2. 이메일 추출 (사용자 동의 없으면 null)
    String email = (String) naverProfile.get("email");
    if (email == null) {
      throw new CustomException(UserErrorCode.NAVER_TOKEN_INVALID);
    }

    String naverName = (String) naverProfile.get("name");

    // 3. 같은 이메일로 이미 다른 방식으로 가입된 경우 차단
    Optional<User> existingUser = userRepository.findByEmail(email);
    if (existingUser.isPresent() && existingUser.get().getProvider() != Provider.NAVER) {
      throw new CustomException(UserErrorCode.OAUTH_PROVIDER_CONFLICT);
    }

    // 4. NAVER 유저 조회 (없으면 자동 회원가입)
    User user =
        userRepository
            .findByEmailAndProvider(email, Provider.NAVER)
            .orElseGet(() -> createNewNaverUser(email, naverName));

    // 5. JWT 발급
    return issueTokenPair(user);
  }

  @Transactional
  public LoginResponseDto kakaoLogin(KakaoLoginRequestDto request) {

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

    // 4. KAKAO 유저 조회 (없으면 자동 회원가입)
    User user =
        userRepository
            .findByEmailAndProvider(email, Provider.KAKAO)
            .orElseGet(() -> createNewKakaoUser(email));

    // 5. JWT 발급
    return issueTokenPair(user);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> fetchKakaoUserInfo(String accessToken) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(accessToken);
      HttpEntity<Void> entity = new HttpEntity<>(headers);

      ResponseEntity<Map> response =
          restTemplate.exchange(
              "https://kapi.kakao.com/v2/user/me", HttpMethod.GET, entity, Map.class);

      Map<String, Object> body = response.getBody();
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
    } catch (Exception e) {
      throw new CustomException(UserErrorCode.KAKAO_TOKEN_INVALID);
    }
  }

  private User createNewKakaoUser(String email) {
    return userRepository.save(
        User.builder()
            .email(email)
            .nickname(email.split("@")[0])
            .role(Role.ROLE_USER)
            .provider(Provider.KAKAO)
            .build());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> fetchNaverUserInfo(String accessToken) {
    try {
      HttpHeaders headers = new HttpHeaders();
      headers.setBearerAuth(accessToken);
      HttpEntity<Void> entity = new HttpEntity<>(headers);

      ResponseEntity<Map> response =
          restTemplate.exchange(
              "https://openapi.naver.com/v1/nid/me", HttpMethod.GET, entity, Map.class);

      Map<String, Object> body = response.getBody();
      if (body == null || !"00".equals(body.get("resultcode"))) {
        throw new CustomException(UserErrorCode.NAVER_TOKEN_INVALID);
      }
      return (Map<String, Object>) body.get("response");
    } catch (CustomException e) {
      throw e;
    } catch (Exception e) {
      throw new CustomException(UserErrorCode.NAVER_TOKEN_INVALID);
    }
  }

  private User createNewNaverUser(String email, String naverName) {
    String nickname = naverName != null ? naverName : email.split("@")[0];
    return userRepository.save(
        User.builder()
            .email(email)
            .nickname(nickname)
            .role(Role.ROLE_USER)
            .provider(Provider.NAVER)
            .build());
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
    } catch (Exception e) {
      throw new CustomException(UserErrorCode.GOOGLE_TOKEN_INVALID);
    }
  }

  private User createNewGoogleUser(String email, String googleName) {
    String nickname = googleName != null ? googleName : email.split("@")[0];
    return userRepository.save(
        User.builder()
            .email(email)
            .nickname(nickname)
            .role(Role.ROLE_USER)
            .provider(Provider.GOOGLE)
            .build());
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
