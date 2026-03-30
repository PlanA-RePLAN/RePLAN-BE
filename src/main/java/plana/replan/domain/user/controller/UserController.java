package plana.replan.domain.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.user.dto.UserResponseDto;
import plana.replan.domain.user.service.UserService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

  private final UserService userService;

  @GetMapping("/me")
  public ResponseEntity<UserResponseDto> getMyInfo(@AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(userService.getMyInfo(userId));
  }
}
