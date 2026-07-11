package plana.replan.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "알림 설정 변경 요청 (보낼 항목만 포함, 생략/null은 기존값 유지)")
public record NotificationSettingUpdateRequest(
    @Schema(description = "투두 알림 받기 (마감 임박, 실패 리플랜)", example = "false") Boolean todo,
    @Schema(description = "통계 알림 받기 (리포트 도착)", example = "true") Boolean stats,
    @Schema(description = "공지 알림 받기", example = "true") Boolean notice,
    @Schema(description = "마케팅(광고성) 정보 수신 동의. 바꾸면 동의/철회 시각이 함께 기록된다", example = "true")
        Boolean marketing) {}
