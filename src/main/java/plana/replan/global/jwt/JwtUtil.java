package plana.replan.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import plana.replan.global.exception.CustomException;

@Component
public class JwtUtil {

  private final SecretKey key;
  private final long accessExpiration;
  private final long refreshExpiration;

  public JwtUtil(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-expiration}") long accessExpiration,
      @Value("${jwt.refresh-expiration}") long refreshExpiration) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessExpiration = accessExpiration;
    this.refreshExpiration = refreshExpiration;
  }

  // Access Token 발급
  public String generateAccessToken(String email, String role) {
    return Jwts.builder()
        .subject(email)
        .claim("role", role)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + accessExpiration))
        .signWith(key)
        .compact();
  }

  // Refresh Token 발급
  public String generateRefreshToken(String email) {
    return Jwts.builder()
        .subject(email)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
        .signWith(key)
        .compact();
  }

  // 토큰에서 이메일 추출
  public String getEmail(String token) {
    return getClaims(token).getSubject();
  }

  // 토큰에서 역할 추출
  public String getRole(String token) {
    return getClaims(token).get("role", String.class);
  }

  // 토큰 유효성 검증
  public boolean validateToken(String token) {
    getClaims(token); // 예외 던지면 알아서 터짐
    return true;
  }

  private Claims getClaims(String token) {
    try {
      return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    } catch (ExpiredJwtException e) {
      // 만료된 토큰
      throw new CustomException(JwtErrorCode.EXPIRED_TOKEN);
    } catch (JwtException e) {
      // 변조되거나 잘못된 토큰
      throw new CustomException(JwtErrorCode.INVALID_TOKEN);
    } catch (IllegalArgumentException e) {
      // 토큰이 null이거나 빈 문자열
      throw new CustomException(JwtErrorCode.EMPTY_TOKEN);
    }
  }
}
