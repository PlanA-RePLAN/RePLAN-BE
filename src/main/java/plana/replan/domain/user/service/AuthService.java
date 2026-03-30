package plana.replan.domain.user.service;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import plana.replan.domain.user.dto.LoginRequestDto;
import plana.replan.domain.user.dto.LoginResponseDto;
import plana.replan.domain.user.dto.SignUpRequestDto;
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
            .orElseThrow(() -> new CustomException(UserErrorCode.USER_NOT_FOUND));

    // 2. 비밀번호 검증
    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      throw new CustomException(UserErrorCode.INVALID_PASSWORD);
    }

    // 3. 토큰 발급
    String accessToken = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name());
    String refreshToken = jwtUtil.generateRefreshToken(user.getEmail());

    // 4. Refresh Token Redis에 저장 (7일)
    redisTemplate
        .opsForValue()
        .set(
            "refresh:" + user.getEmail(),
            refreshToken,
            jwtUtil.getRefreshExpiration(),
            TimeUnit.MILLISECONDS);

    return new LoginResponseDto(accessToken, refreshToken);
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

    // 6. 새 토큰 발급
    String newAccessToken = jwtUtil.generateAccessToken(email, user.getRole().name());
    String newRefreshToken = jwtUtil.generateRefreshToken(email);

    // 7. Redis Refresh Token 덮어쓰기 (Rotation)
    redisTemplate
        .opsForValue()
        .set(
            "refresh:" + email,
            newRefreshToken,
            jwtUtil.getRefreshExpiration(),
            TimeUnit.MILLISECONDS);

    return new LoginResponseDto(newAccessToken, newRefreshToken);
  }

  public void logout(String accessToken) {

    // 1. Access Token 검증 및 이메일 추출
    jwtUtil.validateToken(accessToken);
    String email = jwtUtil.getEmail(accessToken);

    // 2. Redis에서 Refresh Token 삭제
    redisTemplate.delete("refresh:" + email);
  }
}
