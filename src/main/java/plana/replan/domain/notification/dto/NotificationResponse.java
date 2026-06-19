package plana.replan.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import plana.replan.domain.notification.entity.Notification;

@Schema(description = "알림 한 건")
public record NotificationResponse(
    @Schema(description = "알림 id", example = "12") Long id,
    @Schema(description = "탭 분류 (TODO/STATS/ETC)", example = "TODO") String category,
    @Schema(description = "알림 종류", example = "TODO_DUE_SOON") String type,
    @Schema(description = "제목", example = "'영단어 100개 암기' 투두") String title,
    @Schema(description = "내용", example = "주요 투두로 설정한 투두의 마감 시간이 하루 남았어요.") String body,
    @Schema(description = "누르면 갈 화면 종류 (없으면 null)", example = "TODO") String targetType,
    @Schema(description = "누르면 갈 대상 id (없으면 null)", example = "9") Long targetId,
    @JsonProperty("read") @Schema(description = "읽음 여부", example = "false") boolean read,
    @Schema(description = "생성 시각 (ISO 8601 형식)", example = "2026-06-19T00:00:00")
        String createdAt) {

  public static NotificationResponse from(Notification n) {
    return new NotificationResponse(
        n.getId(),
        n.getCategory().name(),
        n.getType().name(),
        n.getTitle(),
        n.getBody(),
        n.getTargetType() == null ? null : n.getTargetType().name(),
        n.getTargetId(),
        n.isRead(),
        n.getCreatedAt() == null ? null : n.getCreatedAt().toString());
  }
}
