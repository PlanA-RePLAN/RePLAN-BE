package plana.replan.domain.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import plana.replan.domain.notification.entity.Platform;

@Schema(description = "기기 토큰 등록 요청")
public record DeviceTokenRegisterRequest(
    @Schema(
            description = "FCM 토큰",
            example = "fcm-token-xyz",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String token,
    @Schema(description = "기기 종류", example = "WEB", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        Platform platform) {}
