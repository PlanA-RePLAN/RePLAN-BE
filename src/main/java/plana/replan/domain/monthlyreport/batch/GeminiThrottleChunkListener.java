package plana.replan.domain.monthlyreport.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@StepScope
public class GeminiThrottleChunkListener implements ChunkListener {

  @Value("${statistics.batch.gemini-call-delay-ms:2500}")
  private long geminiCallDelayMs;

  private boolean aiCalled;

  void markAiCalled() {
    aiCalled = true;
  }

  @Override
  public void beforeChunk(ChunkContext context) {
    aiCalled = false;
  }

  @Override
  public void afterChunk(ChunkContext context) {
    if (!aiCalled) return;
    try {
      Thread.sleep(geminiCallDelayMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      log.warn("Gemini throttle sleep 인터럽트");
    }
  }

  @Override
  public void afterChunkError(ChunkContext context) {
    // Gemini 호출 후 실패한 경우에도 throttle이 작동해야 429 연쇄를 막을 수 있으므로 리셋하지 않음
  }
}
