package plana.replan.domain.auth.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import plana.replan.domain.auth.dto.LoginRequestDto;
import plana.replan.domain.auth.dto.LoginResponseDto;
import plana.replan.domain.auth.dto.SignUpRequestDto;
import plana.replan.domain.auth.service.AuthService;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  @PostMapping("/signup")
  public ResponseEntity<Void> signUp(@Valid @RequestBody SignUpRequestDto request) {
    authService.signUp(request);
    return ResponseEntity.ok().build();
  }

  @PostMapping("/login")
  public ResponseEntity<LoginResponseDto> login(@Valid @RequestBody LoginRequestDto request) {
    return ResponseEntity.ok(authService.login(request));
  }

  @PostMapping("/reissue")
  public ResponseEntity<LoginResponseDto> reissue(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    String refreshToken = authHeader.substring(7);
    return ResponseEntity.ok(authService.reissue(refreshToken));
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(HttpServletRequest request) {
    String authHeader = request.getHeader("Authorization");
    String accessToken = authHeader.substring(7);
    authService.logout(accessToken);
    return ResponseEntity.ok().build();
  }
}
