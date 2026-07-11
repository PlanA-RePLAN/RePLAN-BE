package plana.replan.domain.notification.entity;

/** 알림 카테고리. 수신 설정(켜기/끄기)과 알림 목록 필터가 이 단위로 동작한다. */
public enum NotificationCategory {
  TODO, // 투두 (마감 임박, 실패 리플랜)
  STATS, // 통계 (리포트 도착)
  NOTICE, // 공지 (발송 기능은 추후 작업)
  MARKETING // 광고 (수신 동의한 회원에게만, 발송 기능은 추후 작업)
}
