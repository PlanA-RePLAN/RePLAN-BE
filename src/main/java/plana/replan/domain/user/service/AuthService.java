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
}
