package plana.replan.domain.monthlyreport.entity;

/**
 * 루틴 수정 카드의 "변경된 사항만 표시" 한 줄. AI가 준 값을 믿지 않고, 서버가 DB의 실제 루틴 값과 AI의 수정 후 값을 비교해 계산한다. field는
 * title/routineType/routineDays/routineTime/routineEndAt/tag.
 */
public record TipNoteChangedField(String field, String before, String after) {}
