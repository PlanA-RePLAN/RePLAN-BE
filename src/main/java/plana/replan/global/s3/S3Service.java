package plana.replan.global.s3;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import plana.replan.domain.auth.dto.PresignedUrlResponseDto;
import plana.replan.domain.user.exception.UserErrorCode;
import plana.replan.global.exception.CustomException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class S3Service {

  private static final Set<String> ALLOWED_CONTENT_TYPES =
      Set.of("image/jpeg", "image/png", "image/webp", "image/gif");

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
  private final StringRedisTemplate redisTemplate;

  @Value("${cloud.aws.s3.bucket}")
  private String bucket;

  @Value("${cloud.aws.cloudfront.domain}")
  private String cloudFrontDomain;

  /** 신규 OAuth 유저용. tempToken 검증 후 presigned URL 발급. */
  public PresignedUrlResponseDto generatePresignedUrl(
      String originalFilename, String contentType, String tempToken) {
    validateTempToken(tempToken);
    return createPresignedUrl(originalFilename, contentType);
  }

  /** 로그인 유저용. 인증은 호출 측(JwtFilter)에서 끝났다는 전제로 presigned URL 발급. */
  public PresignedUrlResponseDto generatePresignedUrlForUser(
      String originalFilename, String contentType) {
    return createPresignedUrl(originalFilename, contentType);
  }

  private PresignedUrlResponseDto createPresignedUrl(String originalFilename, String contentType) {
    validateFilename(originalFilename);
    validateContentType(contentType);

    String key = "profiles/temp/" + UUID.randomUUID() + "_" + originalFilename;

    PutObjectRequest putObjectRequest =
        PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build();

    PutObjectPresignRequest presignRequest =
        PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(10))
            .putObjectRequest(putObjectRequest)
            .build();

    PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

    return new PresignedUrlResponseDto(presigned.url().toString(), key);
  }

  private void validateFilename(String filename) {
    if (filename == null
        || filename.isBlank()
        || filename.contains("..")
        || filename.contains("/")) {
      throw new CustomException(UserErrorCode.INVALID_FILENAME);
    }
  }

  private void validateContentType(String contentType) {
    if (contentType == null
        || contentType.isBlank()
        || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
      throw new CustomException(UserErrorCode.UNSUPPORTED_CONTENT_TYPE);
    }
  }

  public String moveToConfirmed(String tempKey) {
    if (tempKey == null || !tempKey.startsWith("profiles/temp/")) {
      throw new CustomException(UserErrorCode.INVALID_S3_KEY);
    }
    String confirmedKey = "profiles/confirmed/" + tempKey.substring("profiles/temp/".length());

    s3Client.copyObject(
        CopyObjectRequest.builder()
            .sourceBucket(bucket)
            .sourceKey(tempKey)
            .destinationBucket(bucket)
            .destinationKey(confirmedKey)
            .build());

    s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(tempKey).build());

    return "https://" + cloudFrontDomain + "/" + confirmedKey;
  }

  private void validateTempToken(String tempToken) {
    if (tempToken == null || tempToken.isBlank()) {
      throw new CustomException(UserErrorCode.INVALID_TEMP_TOKEN);
    }
    String value = redisTemplate.opsForValue().get("oauth-temp:" + tempToken);
    if (value == null) {
      throw new CustomException(UserErrorCode.INVALID_TEMP_TOKEN);
    }
  }
}
