package plana.replan.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 설정 변경 요청 (보낼 항목만 포함, 생략/null은 기존값 유지)")
public record NotificationSettingUpdateRequest(
    @Schema(description = "마감 임박 알림 받기", example = "false") Boolean todoDue,
    @Schema(description = "실패 리플랜 알림 받기", example = "true") Boolean todoFailed,
    @Schema(description = "리포트 도착 알림 받기", example = "true") Boolean report) {}
