package plana.replan.global.jwt;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import plana.replan.global.exception.CustomException;

@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    // 1. 헤더에서 토큰 꺼내기
    String token = resolveToken(request);

    // 2. 토큰 없으면 그냥 통과 (로그인, 회원가입 등 공개 API)
    if (token == null) {
      filterChain.doFilter(request, response);
      return;
    }

    // 3. 토큰 검증 및 인증 정보 저장
    try {
      jwtUtil.validateToken(token);

      Long userId = jwtUtil.getUserId(token);
      String role = jwtUtil.getRole(token);

      // 4. SecurityContext에 인증 정보 저장
      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(
              userId, // principal (현재 유저)
              null, // credentials (비밀번호, JWT에선 필요없음)
              List.of(new SimpleGrantedAuthority(role)) // 권한
              );

      SecurityContextHolder.getContext().setAuthentication(authentication);

    } catch (CustomException e) {
      SecurityContextHolder.clearContext();
    }

    filterChain.doFilter(request, response);
  }

  // Authorization 헤더에서 Bearer 토큰 추출
  private String resolveToken(HttpServletRequest request) {
    String bearerToken = request.getHeader("Authorization");
    if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
      return bearerToken.substring(7); // "Bearer " 이후 토큰만 추출
    }
    return null;
  }
}
