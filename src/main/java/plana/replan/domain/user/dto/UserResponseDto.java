package plana.replan.domain.user.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import plana.replan.domain.user.entity.Provider;
import plana.replan.domain.user.entity.Role;
import plana.replan.domain.user.entity.User;

@Getter
@AllArgsConstructor
public class UserResponseDto {

  private Long userId;
  private String email;
  private String nickname;
  private Role role;
  private Provider provider;

  public static UserResponseDto from(User user) {
    return new UserResponseDto(
        user.getId(), user.getEmail(), user.getNickname(), user.getRole(), user.getProvider());
  }
}
