package plana.replan.domain.monthlyreport.entity;

/** 팁노트 추천 카드의 처리 상태. */
public enum TipNoteItemStatus {
  /** 아직 처리 안 됨 — 화면에 보이는 상태 */
  PENDING,
  /** "투두 반영하기"로 실제 투두/루틴에 반영됨 */
  APPLIED,
  /** "반영 없이 끝내기"로 접음 */
  DISMISSED
}
