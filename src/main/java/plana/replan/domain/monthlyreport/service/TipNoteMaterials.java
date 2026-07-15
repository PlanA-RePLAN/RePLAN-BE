package plana.replan.domain.monthlyreport.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import plana.replan.domain.routine.entity.RoutineType;

/**
 * 팁노트 생성에 쓰는 재료 묶음. 지난달 것(미완료 투두·리플랜 실패 이유)과 현재 것(살아있는 루틴·태그 목록)이 섞여 있다. 루틴 스냅샷은 수정 제안의 검증·최종 상태
 * 채움·diff 계산의 기준값으로도 쓴다.
 */
public record TipNoteMaterials(
    List<UncompletedTodo> uncompletedTodos,
    List<ReplanRecord> replanRecords,
    List<RoutineSnapshot> routines,
    List<TagOption> tags) {

  /** 지난달 미완료 투두 1건 (추천의 직접 재료). */
  public record UncompletedTodo(
      String title, LocalDateTime dueDate, String tagName, boolean routine) {}

  /** 지난달 리플랜 기록 1건 — 실패 이유로 사용자의 실패 성향을 파악하는 용도. */
  public record ReplanRecord(String todoTitle, List<String> reasonLabels) {}

  /** 현재 살아있는(종료 안 된) 엄마 루틴의 스냅샷 — 수정 제안 대상. */
  public record RoutineSnapshot(
      Long id,
      String title,
      RoutineType routineType,
      List<Integer> routineDays,
      LocalTime routineTime,
      LocalDateTime endAt,
      Long tagId,
      String tagName) {}

  /** 유저가 가진 태그 — AI가 실존 태그만 배정하게 하는 화이트리스트. */
  public record TagOption(Long id, String name) {}
}
