package plana.replan.domain.notification.infra;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import plana.replan.domain.notification.entity.Platform;

/**
 * Firebase를 끈 환경(firebase.enabled=false)에서 쓰는 발송기. 실제 푸시는 보내지 않고 로그만 남긴다. 푸시를 끈 상태로도 서버가 정상 기동하고
 * 알림함 저장 등 나머지 기능은 그대로 동작하게 하기 위한 것.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "firebase", name = "enabled", havingValue = "false")
public class NoOpPushSender implements PushSender {

  @Override
  public PushResult send(
      String token, String title, String body, Map<String, String> data, Platform platform) {
    log.debug("푸시 비활성(firebase.enabled=false) - 발송 생략. title={}", title);
    return PushResult.SUCCESS;
  }
}
