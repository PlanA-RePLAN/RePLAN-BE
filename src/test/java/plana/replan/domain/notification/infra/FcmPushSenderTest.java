package plana.replan.domain.notification.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import plana.replan.domain.notification.entity.Platform;

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

  @Test
  @DisplayName("WEB은 notification 블록 없이 data-only로 보낸다(제목·본문을 data에 담음)")
  void webSendsDataOnly() throws Exception {
    FirebaseMessaging fm = mock(FirebaseMessaging.class);
    given(fm.send(any(Message.class))).willReturn("msg-id");

    new FcmPushSender(fm).send("tok", "제목", "본문", Map.of("type", "TODO_DUE_SOON"), Platform.WEB);

    Message sent = captureSent(fm);
    // 브라우저 자동표시를 막기 위해 notification 블록이 없어야 한다.
    assertThat(ReflectionTestUtils.getField(sent, "notification")).isNull();
    @SuppressWarnings("unchecked")
    Map<String, String> data = (Map<String, String>) ReflectionTestUtils.getField(sent, "data");
    assertThat(data)
        .containsEntry("title", "제목")
        .containsEntry("body", "본문")
        .containsEntry("type", "TODO_DUE_SOON");
  }

  @Test
  @DisplayName("네이티브(IOS/ANDROID)는 OS 트레이 표시를 위해 notification 블록을 담는다")
  void nativeSendsNotificationBlock() throws Exception {
    FirebaseMessaging fm = mock(FirebaseMessaging.class);
    given(fm.send(any(Message.class))).willReturn("msg-id");

    new FcmPushSender(fm).send("tok", "제목", "본문", Map.of("type", "TODO_DUE_SOON"), Platform.IOS);

    Message sent = captureSent(fm);
    assertThat(ReflectionTestUtils.getField(sent, "notification")).isNotNull();
  }

  private Message captureSent(FirebaseMessaging fm) throws Exception {
    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(fm).send(captor.capture());
    return captor.getValue();
  }
}
