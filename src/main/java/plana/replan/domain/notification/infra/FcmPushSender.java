package plana.replan.domain.notification.infra;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "firebase",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class FcmPushSender implements PushSender {

  private final FirebaseMessaging firebaseMessaging;

  @Override
  public PushResult send(String token, String title, String body, Map<String, String> data) {
    Message message =
        Message.builder()
            .setToken(token)
            .setNotification(Notification.builder().setTitle(title).setBody(body).build())
            .putAllData(data == null ? java.util.Map.of() : data)
            .build();
    try {
      firebaseMessaging.send(message);
      return PushResult.SUCCESS;
    } catch (FirebaseMessagingException e) {
      PushResult result = classify(e.getMessagingErrorCode());
      log.warn(
          "FCM 발송 실패 - token={}, code={}, result={}",
          mask(token),
          e.getMessagingErrorCode(),
          result);
      return result;
    }
  }

  // 토큰은 민감 식별자이므로 로그에는 앞뒤 일부만 남긴다.
  private static String mask(String token) {
    if (token == null || token.length() <= 8) {
      return "****";
    }
    return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
  }

  public static PushResult classify(MessagingErrorCode code) {
    if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
      return PushResult.DEAD_TOKEN;
    }
    return PushResult.FAILURE;
  }
}
