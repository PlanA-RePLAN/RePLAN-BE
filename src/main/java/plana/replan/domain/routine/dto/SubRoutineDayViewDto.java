package plana.replan.domain.routine.dto;

/** 특정 날짜 기준 하위 루틴 예정분 — 그날 개인화(제목/완료)가 반영된 뷰. */
public record SubRoutineDayViewDto(Long routineId, String title, boolean isCompleted) {}
