package plana.replan.domain.user.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.auth.dto.PresignedUrlResponseDto;
import plana.replan.domain.user.dto.ProfileUpdateRequestDto;
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

  @Override
  @PatchMapping("/profile")
  public ResponseEntity<ApiResult<UserResponseDto>> updateMyProfile(
      @AuthenticationPrincipal Long userId, @RequestBody ProfileUpdateRequestDto request) {
    return ResponseEntity.ok(ApiResult.ok(userService.updateProfile(userId, request)));
  }

  @Override
  @DeleteMapping
  public ResponseEntity<ApiResult<Void>> deleteMyAccount(@AuthenticationPrincipal Long userId) {
    userService.deleteAccount(userId);
    return ResponseEntity.ok(ApiResult.ok());
  }

  @Override
  @GetMapping("/profile/image/presigned-url")
  public ResponseEntity<ApiResult<PresignedUrlResponseDto>> getProfileImagePresignedUrl(
      @AuthenticationPrincipal Long userId,
      @RequestParam String filename,
      @RequestParam String contentType) {
    return ResponseEntity.ok(
        ApiResult.ok(userService.createProfileImagePresignedUrl(userId, filename, contentType)));
  }
}
