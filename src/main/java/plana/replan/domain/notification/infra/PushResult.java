package plana.replan.domain.notification.infra;

public enum PushResult {
  SUCCESS,
  DEAD_TOKEN, // 토큰이 더 이상 유효하지 않음(앱 삭제 등) → 저장소에서 지운다
  FAILURE // 일시적/기타 실패 → 토큰은 유지
}
