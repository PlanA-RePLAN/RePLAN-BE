package plana.replan.domain.monthlyreport.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import plana.replan.domain.monthlyreport.entity.TipNoteAction;
import plana.replan.domain.monthlyreport.entity.TipNoteChangedField;
import plana.replan.domain.routine.entity.RoutineType;

/**
 * 검증·보정을 마친 팁노트 초안. AI 원문이 아니라 서버가 확정한 값만 담는다 — 태그/루틴 id는 유저 소유로 검증됐고, 날짜는 과거면 이번 달 마지막 날로 교정됐고,
 * changedFields는 DB 스냅샷과 비교해 서버가 계산한 것이다.
 */
public record TipNoteDraft(String tip, List<Item> items) {

  /** 추천 카드 1장의 초안. 수정 카드는 "수정 후 최종 상태 전체"가 채워져 있다. */
  public record Item(
      TipNoteAction action,
      Long targetRoutineId,
      String title,
      Long tagId,
      LocalDateTime todoDueAt,
      LocalDateTime routineEndAt,
      LocalTime routineTime,
      RoutineType routineType,
      List<Integer> routineDays,
      List<TipNoteChangedField> changedFields) {}
}
