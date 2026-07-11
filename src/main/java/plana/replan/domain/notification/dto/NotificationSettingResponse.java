package plana.replan.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import plana.replan.domain.user.entity.User;

@Schema(description = "알림 설정")
public record NotificationSettingResponse(
    @JsonProperty("todo") @Schema(description = "투두 알림 받기 (마감 임박, 실패 리플랜)", example = "true")
        boolean todo,
    @JsonProperty("stats") @Schema(description = "통계 알림 받기 (리포트 도착)", example = "true")
        boolean stats,
    @JsonProperty("notice") @Schema(description = "공지 알림 받기", example = "true") boolean notice,
    @JsonProperty("marketing") @Schema(description = "마케팅(광고성) 정보 수신 동의 여부", example = "false")
        boolean marketing) {

  public static NotificationSettingResponse from(User user) {
    return new NotificationSettingResponse(
        user.isNotifyTodo(), user.isNotifyStats(), user.isNotifyNotice(), user.isMarketingAgreed());
  }
}
