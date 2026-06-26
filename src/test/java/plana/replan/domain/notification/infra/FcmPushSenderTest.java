package plana.replan.domain.notification.infra;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FcmPushSenderTest {

  @Test
  @DisplayName("UNREGISTERED/INVALID_ARGUMENT 는 죽은 토큰으로 분류한다")
  void classifiesDeadTokens() {
    assertThat(FcmPushSender.classify(MessagingErrorCode.UNREGISTERED))
        .isEqualTo(PushResult.DEAD_TOKEN);
    assertThat(FcmPushSender.classify(MessagingErrorCode.INVALID_ARGUMENT))
        .isEqualTo(PushResult.DEAD_TOKEN);
  }

  @Test
  @DisplayName("그 외 에러 코드는 일시적 실패로 분류한다")
  void classifiesOtherFailures() {
    assertThat(FcmPushSender.classify(MessagingErrorCode.INTERNAL)).isEqualTo(PushResult.FAILURE);
    assertThat(FcmPushSender.classify(null)).isEqualTo(PushResult.FAILURE);
  }
}
