package plana.replan.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import plana.replan.global.common.ApiResult;
import plana.replan.global.exception.ErrorDetail;
import plana.replan.global.jwt.JwtErrorCode;
import plana.replan.global.jwt.JwtFilter;
import plana.replan.global.jwt.JwtUtil;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtUtil jwtUtil;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        // 1. CSRF 비활성화 (JWT는 세션 안 쓰니까 불필요)
        .csrf(AbstractHttpConfigurer::disable)

        // 2. 세션 Stateless 설정
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 3. URL 권한 설정
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers(
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/v3/api-docs",
                        "/swagger-ui.html",
                        "/actuator/health",
                        "/api/auth/**" // 로그인, 회원가입은 인증 없이 허용
                        )
                    .permitAll()
                    .anyRequest()
                    .authenticated())

        // 4. 미인증 요청 → 401, 인가 실패 → 403
        .exceptionHandling(
            ex ->
                ex.authenticationEntryPoint(
                    (request, response, authException) -> {
                      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                      response.setCharacterEncoding("UTF-8");
                      ApiResult<?> body =
                          ApiResult.error(401, ErrorDetail.of(JwtErrorCode.EMPTY_TOKEN));
                      response.getWriter().write(objectMapper.writeValueAsString(body));
                    }))

        // 5. JwtFilter를 UsernamePasswordAuthenticationFilter 앞에 등록
        .addFilterBefore(new JwtFilter(jwtUtil), UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  // 5. PasswordEncoder Bean 등록
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }
}
