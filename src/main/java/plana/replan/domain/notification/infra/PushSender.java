package plana.replan.domain.notification.infra;

import java.util.Map;
import plana.replan.domain.notification.entity.Platform;

/** 푸시 발송 포트. 단위 테스트에서는 mock으로 대체한다. */
public interface PushSender {

  /**
   * 푸시를 발송한다.
   *
   * <p>플랫폼에 따라 메시지 구성이 다르다. WEB은 브라우저 자동표시와 서비스워커 표시가 겹쳐 알림이 2개 뜨는 것을 막기 위해 {@code notification} 블록
   * 없이 data-only로 보내고(서비스워커가 data를 읽어 단독 표시), 네이티브(IOS/ANDROID)는 OS 트레이 자동표시를 위해 {@code
   * notification} 블록을 담는다.
   */
  PushResult send(
      String token, String title, String body, Map<String, String> data, Platform platform);
}
