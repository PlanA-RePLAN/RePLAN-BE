package plana.replan.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "알림함 목록 (무한스크롤)")
public record NotificationListResponse(
    @Schema(description = "알림 목록") List<NotificationResponse> items,
    @Schema(description = "다음 cursor. 마지막이면 null", example = "37") Long nextCursor,
    @JsonProperty("hasNext") @Schema(description = "다음 페이지 존재 여부", example = "true")
        boolean hasNext) {}
