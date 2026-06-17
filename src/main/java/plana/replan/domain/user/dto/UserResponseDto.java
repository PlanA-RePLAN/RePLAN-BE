package plana.replan.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;

@Schema(description = "유저 정보 응답")
@Getter
@AllArgsConstructor
public class UserResponseDto {

  @Schema(description = "유저 ID", example = "1")
  private Long userId;

  @Schema(description = "이메일", example = "user@example.com")
  private String email;

  @Schema(description = "닉네임", example = "일규")
  private String nickname;

  @Schema(description = "역할", example = "ROLE_USER")
  private Role role;

  @Schema(description = "가입 경로", example = "LOCAL")
  private Provider provider;

  @Schema(
      description = "프로필 이미지 URL. 미설정 시 null",
      example = "https://cdn.example.com/profiles/confirmed/abc.png")
  private String profileImage;

  public static UserResponseDto from(User user) {
    return new UserResponseDto(
        user.getId(),
        user.getEmail(),
        user.getNickname(),
        user.getRole(),
        user.getProvider(),
        user.getProfileImage());
  }
}
