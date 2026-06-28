package plana.replan.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "기기 토큰 삭제 요청")
public record DeviceTokenDeleteRequest(
    @Schema(
            description = "삭제할 FCM 토큰",
            example = "fcm-token-xyz",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String token) {}
