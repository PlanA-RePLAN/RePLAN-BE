package plana.replan.global.s3;

import java.time.Duration;
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

  private final S3Client s3Client;
  private final S3Presigner s3Presigner;
  private final StringRedisTemplate redisTemplate;

  @Value("${cloud.aws.s3.bucket}")
  private String bucket;

  @Value("${cloud.aws.cloudfront.domain}")
  private String cloudFrontDomain;

  public PresignedUrlResponseDto generatePresignedUrl(
      String originalFilename, String contentType, String tempToken) {
    validateTempToken(tempToken);

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

  public String moveToConfirmed(String tempKey) {
    if (tempKey == null || !tempKey.startsWith("profiles/temp/")) {
      throw new CustomException(UserErrorCode.INVALID_S3_KEY);
    }
    String confirmedKey = tempKey.replace("profiles/temp/", "profiles/confirmed/");

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
