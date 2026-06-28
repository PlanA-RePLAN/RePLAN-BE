package plana.replan.domain.notification.infra;

import java.util.Map;

/** 푸시 발송 포트. 단위 테스트에서는 mock으로 대체한다. */
public interface PushSender {
  PushResult send(String token, String title, String body, Map<String, String> data);
}
