package plana.replan.domain.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import plana.replan.domain.user.dto.LoginRequestDto;
import plana.replan.domain.user.dto.LoginResponseDto;
import plana.replan.domain.user.dto.SignUpRequestDto;
import plana.replan.domain.user.service.AuthService;

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
  public ResponseEntity<LoginResponseDto> reissue(
      @RequestHeader("Authorization") String authHeader) {
    String refreshToken = authHeader.substring(7); // "Bearer " 제거
    return ResponseEntity.ok(authService.reissue(refreshToken));
  }
}
