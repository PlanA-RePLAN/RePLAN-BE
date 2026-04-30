package plana.replan.global.s3;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.auth.dto.PresignedUrlResponseDto;
import plana.replan.global.common.ApiResult;
import plana.replan.global.exception.CustomException;
import plana.replan.global.jwt.JwtErrorCode;

@Tag(name = "S3", description = "S3 파일 업로드 관련 API")
@RestController
@RequestMapping("/api/s3")
@RequiredArgsConstructor
public class S3Controller {

  private final S3Service s3Service;

  @Operation(
      summary = "프로필 이미지 업로드용 Presigned URL 발급",
      description =
          """
              **호출 주체**: 신규 OAuth 유저 (tempToken 보유)

              **비즈니스 로직**
              1. Authorization 헤더에서 tempToken 추출
              2. Redis에서 tempToken 유효성 검증
              3. S3 `profiles/temp/` 경로에 PUT 업로드용 presigned URL 발급 (10분 유효)
              4. presignedUrl과 s3Key 반환

              **클라이언트 사용 방법**
              1. presignedUrl로 이미지를 직접 PUT 업로드 (Content-Type 헤더 필수)
              2. s3Key를 `POST /api/auth/oauth/register` 요청 시 전달
              """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Presigned URL 발급 성공"),
    @ApiResponse(responseCode = "401", description = "tempToken 없음 또는 만료")
  })
  @GetMapping("/presigned-url")
  public ResponseEntity<ApiResult<PresignedUrlResponseDto>> getPresignedUrl(
      @RequestHeader(value = "Authorization", required = false) String authHeader,
      @RequestParam String filename,
      @RequestParam String contentType) {

    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
      throw new CustomException(JwtErrorCode.EMPTY_TOKEN);
    }
    String tempToken = authHeader.substring(7);
    if (tempToken.isBlank()) {
      throw new CustomException(JwtErrorCode.EMPTY_TOKEN);
    }

    return ResponseEntity.ok(
        ApiResult.ok(s3Service.generatePresignedUrl(filename, contentType, tempToken)));
  }
}
