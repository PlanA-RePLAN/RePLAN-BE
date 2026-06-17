package plana.replan.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "프로필 수정 요청. 닉네임, 프로필 이미지 모두 선택 항목으로 전달된 값만 수정됩니다.")
public record ProfileUpdateRequestDto(
    @Schema(description = "변경할 닉네임. 생략 또는 null이면 닉네임을 변경하지 않음", example = "새닉네임") String nickname,
    @Schema(
            description =
                "변경할 프로필 이미지의 S3 임시 key (presigned URL로 업로드한 profiles/temp/... key). 생략 또는 null이면 이미지를 변경하지 않음",
            example = "profiles/temp/4f3a_avatar.png")
        String profileImageKey) {}
