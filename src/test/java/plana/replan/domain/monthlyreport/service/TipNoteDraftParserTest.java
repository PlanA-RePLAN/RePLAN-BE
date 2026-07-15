package plana.replan.domain.monthlyreport.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import plana.replan.domain.monthlyreport.entity.TipNoteAction;
import plana.replan.domain.monthlyreport.entity.TipNoteChangedField;
import plana.replan.domain.monthlyreport.service.TipNoteMaterials.RoutineSnapshot;
import plana.replan.domain.monthlyreport.service.TipNoteMaterials.TagOption;
import plana.replan.domain.routine.entity.RoutineType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class TipNoteDraftParserTest {

  private final TipNoteDraftParser parser = new TipNoteDraftParser();
  private final ObjectMapper mapper = JsonMapper.builder().build();

  // 오늘 = 2026-07-15 (한국시간 가정) → 이번 달 마지막 날 = 2026-07-31
  private static final LocalDate TODAY = LocalDate.of(2026, 7, 15);

  private TipNoteMaterials materials() {
    return new TipNoteMaterials(
        List.of(),
        List.of(),
        List.of(
            new RoutineSnapshot(
                5L,
                "모의고사 풀이",
                RoutineType.WEEKLY,
                List.of(5),
                LocalTime.of(10, 0),
                null,
                2L,
                "Project")),
        List.of(new TagOption(1L, "Study"), new TagOption(2L, "Project")));
  }

  private JsonNode node(String json) {
    return mapper.readTree(json);
  }

  // ── 정상 파싱 ──────────────────────────────────────────────

  @Test
  @DisplayName("정상 응답: 추가 카드 2장(투두/루틴) 파싱")
  void parse_validAddItems() {
    TipNoteDraft draft =
        parser.parse(
            node(
                """
                {"tip":"팁 텍스트","items":[
                  {"action":"ADD_TODO","title":"영어 단어 복습","tagId":1,"todoDueAt":"2026-07-20 21:00"},
                  {"action":"ADD_ROUTINE","title":"11시 이전 취침","tagId":1,"routineType":"DAILY","routineTime":"23:00"}
                ]}
                """),
            materials(),
            TODAY);

    assertThat(draft.tip()).isEqualTo("팁 텍스트");
    assertThat(draft.items()).hasSize(2);

    TipNoteDraft.Item todo = draft.items().get(0);
    assertThat(todo.action()).isEqualTo(TipNoteAction.ADD_TODO);
    assertThat(todo.todoDueAt()).isEqualTo(LocalDateTime.of(2026, 7, 20, 21, 0));
    assertThat(todo.tagId()).isEqualTo(1L);

    TipNoteDraft.Item routine = draft.items().get(1);
    assertThat(routine.action()).isEqualTo(TipNoteAction.ADD_ROUTINE);
    assertThat(routine.routineType()).isEqualTo(RoutineType.DAILY);
    assertThat(routine.routineTime()).isEqualTo(LocalTime.of(23, 0));
    assertThat(routine.routineDays()).isNull();
    assertThat(routine.routineEndAt()).isNull();
  }

  @Test
  @DisplayName("작성 팁이 없으면 팁노트 자체가 성립하지 않아 null")
  void parse_missingTip_returnsNull() {
    assertThat(parser.parse(node("""
        {"items":[]}
        """), materials(), TODAY))
        .isNull();
  }

  // ── 날짜 보정 ──────────────────────────────────────────────

  @Test
  @DisplayName("과거 마감일은 이번 달 마지막 날로 교정 (시간은 유지)")
  void parse_pastDueDate_correctedToEndOfMonth() {
    TipNoteDraft draft =
        parser.parse(
            node(
                """
                {"tip":"팁","items":[{"action":"ADD_TODO","title":"밀린 일 처리","todoDueAt":"2026-06-10 09:00"}]}
                """),
            materials(),
            TODAY);

    assertThat(draft.items().get(0).todoDueAt()).isEqualTo(LocalDateTime.of(2026, 7, 31, 9, 0));
  }

  @Test
  @DisplayName("마감일이 아예 없는 새 투두는 이번 달 마지막 날로 채움")
  void parse_missingDueDate_filledWithEndOfMonth() {
    TipNoteDraft draft =
        parser.parse(
            node(
                """
                {"tip":"팁","items":[{"action":"ADD_TODO","title":"할 일"}]}
                """),
            materials(),
            TODAY);

    assertThat(draft.items().get(0).todoDueAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 7, 31));
  }

  @Test
  @DisplayName("오늘이 월말이면 과거 날짜를 (곧바로 지나버릴 오늘 대신) 내일로 교정")
  void parse_pastDueDateOnMonthEnd_correctedToTomorrow() {
    LocalDate monthEnd = LocalDate.of(2026, 7, 31);
    TipNoteDraft draft =
        parser.parse(
            node(
                """
                {"tip":"팁","items":[{"action":"ADD_TODO","title":"밀린 일","todoDueAt":"2026-07-01 09:00"}]}
                """),
            materials(),
            monthEnd);

    assertThat(draft.items().get(0).todoDueAt()).isEqualTo(LocalDateTime.of(2026, 8, 1, 9, 0));
  }

  @Test
  @DisplayName("과거 반복 종료일도 이번 달 마지막 날로 교정")
  void parse_pastRoutineEndAt_corrected() {
    TipNoteDraft draft =
        parser.parse(
            node(
                """
                {"tip":"팁","items":[{"action":"ADD_ROUTINE","title":"운동","routineType":"WEEKLY","routineDays":[0,2],"routineEndAt":"2026-01-01"}]}
                """),
            materials(),
            TODAY);

    assertThat(draft.items().get(0).routineEndAt().toLocalDate())
        .isEqualTo(LocalDate.of(2026, 7, 31));
  }

  // ── 태그 검증 ──────────────────────────────────────────────

  @Test
  @DisplayName("없는 태그 id나 태그 '이름'이 오면 태그 없음(null)으로 교정하고 카드는 살림")
  void parse_invalidTagId_correctedToNull() {
    TipNoteDraft draft =
        parser.parse(
            node(
                """
                {"tip":"팁","items":[
                  {"action":"ADD_TODO","title":"a","tagId":999,"todoDueAt":"2026-07-20 09:00"},
                  {"action":"ADD_TODO","title":"b","tagId":"Study","todoDueAt":"2026-07-20 09:00"}
                ]}
                """),
            materials(),
            TODAY);

    assertThat(draft.items()).hasSize(2);
    assertThat(draft.items().get(0).tagId()).isNull();
    assertThat(draft.items().get(1).tagId()).isNull();
  }

  // ── 카드 버림 규칙 ─────────────────────────────────────────

  @Test
  @DisplayName("없는 루틴을 고치라는 카드는 버리고 나머지는 살림")
  void parse_unknownTargetRoutine_dropped() {
    TipNoteDraft draft =
        parser.parse(
            node(
                """
                {"tip":"팁","items":[
                  {"action":"MODIFY_ROUTINE","targetRoutineId":999,"title":"x"},
                  {"action":"ADD_TODO","title":"살아남을 카드","todoDueAt":"2026-07-20 09:00"}
                ]}
                """),
            materials(),
            TODAY);

    assertThat(draft.items()).hasSize(1);
    assertThat(draft.items().get(0).title()).isEqualTo("살아남을 카드");
  }

  @Test
  @DisplayName("이상한 action·제목 없음·타입/반복날짜 불일치 카드는 각각 버림")
  void parse_invalidCards_dropped() {
    TipNoteDraft draft =
        parser.parse(
            node(
                """
                {"tip":"팁","items":[
                  {"action":"DELETE_ROUTINE","title":"삭제 시도"},
                  {"action":"ADD_TODO","title":"","todoDueAt":"2026-07-20 09:00"},
                  {"action":"ADD_ROUTINE","title":"주간인데 요일 없음","routineType":"WEEKLY"},
                  {"action":"ADD_ROUTINE","title":"데일리인데 요일 있음","routineType":"DAILY","routineDays":[0]},
                  {"action":"ADD_TODO","title":"정상 카드","todoDueAt":"2026-07-20 09:00"}
                ]}
                """),
            materials(),
            TODAY);

    assertThat(draft.items()).hasSize(1);
    assertThat(draft.items().get(0).title()).isEqualTo("정상 카드");
  }

  @Test
  @DisplayName("카드는 최대 4장까지만, 같은 루틴 수정 카드는 첫 장만")
  void parse_capAndDedupe() {
    TipNoteDraft draft =
        parser.parse(
            node(
                """
                {"tip":"팁","items":[
                  {"action":"MODIFY_ROUTINE","targetRoutineId":5,"title":"수정1","routineTime":"11:00"},
                  {"action":"MODIFY_ROUTINE","targetRoutineId":5,"title":"수정2(중복)","routineTime":"12:00"},
                  {"action":"ADD_TODO","title":"t1","todoDueAt":"2026-07-20 09:00"},
                  {"action":"ADD_TODO","title":"t2","todoDueAt":"2026-07-20 09:00"},
                  {"action":"ADD_TODO","title":"t3","todoDueAt":"2026-07-20 09:00"},
                  {"action":"ADD_TODO","title":"t4","todoDueAt":"2026-07-20 09:00"}
                ]}
                """),
            materials(),
            TODAY);

    assertThat(draft.items()).hasSize(4);
    assertThat(draft.items().get(0).title()).isEqualTo("수정1");
    assertThat(draft.items().get(3).title()).isEqualTo("t3");
  }

  // ── 루틴 수정 카드 ─────────────────────────────────────────

  @Test
  @DisplayName("루틴 수정: 안 바뀐 필드는 기존 값으로 채우고 diff는 서버가 계산")
  void parse_modifyRoutine_fillsFinalStateAndComputesDiff() {
    TipNoteDraft draft =
        parser.parse(
            node(
                """
                {"tip":"팁","items":[{"action":"MODIFY_ROUTINE","targetRoutineId":5,
                  "title":"모의고사 1회분 풀이","tagId":1,"routineTime":"11:00",
                  "routineType":"MONTHLY","routineDays":[15]}]}
                """),
            materials(),
            TODAY);

    TipNoteDraft.Item item = draft.items().get(0);
    assertThat(item.action()).isEqualTo(TipNoteAction.MODIFY_ROUTINE);
    assertThat(item.targetRoutineId()).isEqualTo(5L);
    // 최종 상태 전체
    assertThat(item.title()).isEqualTo("모의고사 1회분 풀이");
    assertThat(item.routineType()).isEqualTo(RoutineType.MONTHLY);
    assertThat(item.routineDays()).containsExactly(15);
    assertThat(item.routineTime()).isEqualTo(LocalTime.of(11, 0));
    assertThat(item.tagId()).isEqualTo(1L);
    // 서버 계산 diff — before는 DB 스냅샷 값
    assertThat(item.changedFields())
        .extracting(TipNoteChangedField::field)
        .containsExactlyInAnyOrder("title", "routineType", "routineDays", "routineTime", "tag");
    TipNoteChangedField titleDiff =
        item.changedFields().stream().filter(c -> c.field().equals("title")).findFirst().get();
    assertThat(titleDiff.before()).isEqualTo("모의고사 풀이");
    assertThat(titleDiff.after()).isEqualTo("모의고사 1회분 풀이");
  }

  @Test
  @DisplayName("루틴 수정: 기존과 완전히 같으면(변경 없음) 의미 없는 카드라 버림")
  void parse_modifyRoutine_noChange_dropped() {
    TipNoteDraft draft =
        parser.parse(
            node(
                """
                {"tip":"팁","items":[{"action":"MODIFY_ROUTINE","targetRoutineId":5,
                  "title":"모의고사 풀이","routineType":"WEEKLY","routineDays":[5],"routineTime":"10:00"}]}
                """),
            materials(),
            TODAY);

    assertThat(draft.items()).isEmpty();
  }

  @Test
  @DisplayName("루틴 수정: 반복 타입을 바꾸면서 새 반복 날짜를 안 주면 버림 (DAILY 제외)")
  void parse_modifyRoutine_typeChangedWithoutDays_dropped() {
    TipNoteDraft draft =
        parser.parse(
            node(
                """
                {"tip":"팁","items":[
                  {"action":"MODIFY_ROUTINE","targetRoutineId":5,"title":"모의고사 풀이","routineType":"MONTHLY"},
                  {"action":"MODIFY_ROUTINE","targetRoutineId":5,"title":"모의고사 풀이","routineType":"DAILY"}
                ]}
                """),
            materials(),
            TODAY);

    // MONTHLY(날짜 없음)는 버려지고, DAILY 전환은 날짜가 필요 없어 살아남는다
    assertThat(draft.items()).hasSize(1);
    assertThat(draft.items().get(0).routineType()).isEqualTo(RoutineType.DAILY);
    assertThat(draft.items().get(0).routineDays()).isNull();
  }
}
