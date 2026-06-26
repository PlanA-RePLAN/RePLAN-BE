package plana.replan.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import plana.replan.domain.user.entity.User;

@Schema(description = "알림 설정")
public record NotificationSettingResponse(
    @JsonProperty("todoDue") @Schema(description = "마감 임박 알림 받기", example = "true") boolean todoDue,
    @JsonProperty("todoFailed") @Schema(description = "실패 리플랜 알림 받기", example = "true")
        boolean todoFailed,
    @JsonProperty("report") @Schema(description = "리포트 도착 알림 받기", example = "true")
        boolean report) {

  public static NotificationSettingResponse from(User user) {
    return new NotificationSettingResponse(
        user.isNotifyTodoDue(), user.isNotifyTodoFailed(), user.isNotifyReport());
  }
}
