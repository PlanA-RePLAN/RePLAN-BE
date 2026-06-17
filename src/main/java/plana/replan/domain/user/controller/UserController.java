package plana.replan.domain.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.user.dto.UserResponseDto;
import plana.replan.domain.user.service.UserService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {

  private final UserService userService;

  @Override
  @GetMapping("/profile")
  public ResponseEntity<ApiResult<UserResponseDto>> getMyProfile(
      @AuthenticationPrincipal Long userId) {
    return ResponseEntity.ok(ApiResult.ok(userService.getMyInfo(userId)));
  }
}
