package plana.replan.domain.monthlyreport.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import plana.replan.domain.monthlyreport.entity.TipNoteAction;
import plana.replan.domain.monthlyreport.entity.TipNoteChangedField;
import plana.replan.domain.monthlyreport.service.TipNoteMaterials.RoutineSnapshot;
import plana.replan.domain.monthlyreport.service.TipNoteMaterials.TagOption;
import plana.replan.domain.routine.entity.RoutineType;
import plana.replan.domain.routine.util.RoutineDays;
import tools.jackson.databind.JsonNode;

/**
 * Gemini가 준 tip_note JSON을 검증·보정해 초안으로 만든다. 원칙: 고칠 수 있으면 교정(과거 날짜 → 이번 달 마지막 날, 없는 태그 → 태그 없음),
 * 남의/없는 데이터를 건드릴 위험이면 그 카드만 버림(없는 루틴 id, 이상한 action, 타입↔반복날짜 불일치). 카드 하나가 불량이어도 나머지는 살린다.
 */
@Slf4j
@Component
public class TipNoteDraftParser {

  private static final int MAX_ITEMS = 4;
  private static final int MAX_TITLE_LENGTH = 255;
  private static final DateTimeFormatter DATE_TIME_FORMAT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  /**
   * @param tipNoteNode 응답 JSON의 tip_note 노드
   * @param materials 검증 기준 재료 (유저의 태그·루틴 스냅샷)
   * @param today 한국시간 기준 오늘 — 날짜 보정 기준
   * @return 검증된 초안. 작성 팁 텍스트가 없으면 팁노트 자체가 성립하지 않으므로 null
   */
  public TipNoteDraft parse(JsonNode tipNoteNode, TipNoteMaterials materials, LocalDate today) {
    String tip = textOrNull(tipNoteNode.path("tip"));
    if (tip == null || tip.isBlank()) {
      return null;
    }

    Map<Long, TagOption> tagsById = new LinkedHashMap<>();
    for (TagOption tag : materials.tags()) {
      tagsById.put(tag.id(), tag);
    }
    Map<Long, RoutineSnapshot> routinesById = new LinkedHashMap<>();
    for (RoutineSnapshot routine : materials.routines()) {
      routinesById.put(routine.id(), routine);
    }

    List<TipNoteDraft.Item> items = new ArrayList<>();
    Set<Long> modifiedRoutineIds = new HashSet<>();
    Set<String> seenTitles = new HashSet<>();
    for (JsonNode node : tipNoteNode.path("items")) {
      if (items.size() >= MAX_ITEMS) {
        break;
      }
      try {
        TipNoteDraft.Item item = parseItem(node, tagsById, routinesById, today);
        if (item == null) {
          continue;
        }
        // 같은 루틴에 수정 카드 2장 / 같은 제목의 추가 카드 2장 → 첫 번째만 남긴다.
        if (item.action() == TipNoteAction.MODIFY_ROUTINE
            && !modifiedRoutineIds.add(item.targetRoutineId())) {
          continue;
        }
        if (item.action() != TipNoteAction.MODIFY_ROUTINE && !seenTitles.add(item.title())) {
          continue;
        }
        items.add(item);
      } catch (Exception e) {
        // 카드 하나가 불량이어도 나머지는 살린다. 원문에는 사용자 데이터가 있어 로그에 남기지 않는다.
        log.warn("팁노트 카드 1건 파싱 실패로 해당 카드만 제외", e);
      }
    }
    return new TipNoteDraft(tip, items);
  }

  /** 카드 1장을 파싱·검증한다. 버려야 하는 카드는 null 반환 또는 예외. */
  private TipNoteDraft.Item parseItem(
      JsonNode node,
      Map<Long, TagOption> tagsById,
      Map<Long, RoutineSnapshot> routinesById,
      LocalDate today) {
    TipNoteAction action = TipNoteAction.valueOf(node.path("action").asText());

    String title = textOrNull(node.path("title"));
    Long tagId = validTagIdOrNull(node.path("tagId"), tagsById);

    return switch (action) {
      case ADD_TODO -> {
        requireTitle(title);
        LocalDateTime dueAt = parseDateTime(textOrNull(node.path("todoDueAt")));
        if (dueAt == null) {
          // 마감 없는 추천은 허용하지 않는다(모든 투두는 마감기한이 있어야 한다) → 이번 달 마지막 날로 채운다.
          dueAt = endOfMonth(today).atTime(23, 59, 59);
        }
        yield new TipNoteDraft.Item(
            action,
            null,
            truncate(title),
            tagId,
            correctPastDate(dueAt, today),
            null,
            null,
            null,
            null,
            List.of());
      }
      case ADD_ROUTINE -> {
        requireTitle(title);
        RoutineType type = RoutineType.valueOf(node.path("routineType").asText());
        List<Integer> days = normalizeDays(type, intListOrNull(node.path("routineDays")));
        LocalDateTime endAt = parseEndDate(textOrNull(node.path("routineEndAt")));
        yield new TipNoteDraft.Item(
            action,
            null,
            truncate(title),
            tagId,
            null,
            endAt != null ? correctPastDate(endAt, today) : null,
            parseTime(textOrNull(node.path("routineTime"))),
            type,
            days,
            List.of());
      }
      case MODIFY_ROUTINE -> parseModifyRoutine(node, tagsById, routinesById, today, title, tagId);
    };
  }

  /**
   * 루틴 수정 카드: AI가 안 채운 필드는 기존 루틴 값으로 메워 "수정 후 최종 상태 전체"를 만들고, changedFields는 AI 말을 믿지 않고 DB 스냅샷과
   * 비교해 서버가 직접 계산한다. 결과적으로 아무것도 안 바뀌면 의미 없는 카드이므로 버린다.
   */
  private TipNoteDraft.Item parseModifyRoutine(
      JsonNode node,
      Map<Long, TagOption> tagsById,
      Map<Long, RoutineSnapshot> routinesById,
      LocalDate today,
      String title,
      Long validTagId) {
    Long targetId = longOrNull(node.path("targetRoutineId"));
    RoutineSnapshot snapshot = targetId != null ? routinesById.get(targetId) : null;
    if (snapshot == null) {
      // 없는/남의/끝난 루틴 — 어느 루틴을 말한 건지 알 수 없으니 보정하지 않고 버린다.
      return null;
    }

    String finalTitle = title != null && !title.isBlank() ? truncate(title) : snapshot.title();

    String typeText = textOrNull(node.path("routineType"));
    RoutineType finalType =
        typeText != null ? RoutineType.valueOf(typeText) : snapshot.routineType();
    boolean typeChanged = finalType != snapshot.routineType();

    List<Integer> aiDays = intListOrNull(node.path("routineDays"));
    List<Integer> finalDays;
    if (aiDays != null) {
      finalDays = normalizeDays(finalType, aiDays);
    } else if (typeChanged) {
      // 반복 타입을 바꾸면 기존 반복 날짜는 의미가 없어지므로 새 타입의 날짜를 반드시 요구한다.
      if (finalType != RoutineType.DAILY) {
        return null;
      }
      finalDays = null;
    } else {
      finalDays = snapshot.routineDays();
    }

    LocalTime aiTime = parseTime(textOrNull(node.path("routineTime")));
    LocalTime finalTime = aiTime != null ? aiTime : snapshot.routineTime();

    LocalDateTime aiEndAt = parseEndDate(textOrNull(node.path("routineEndAt")));
    LocalDateTime finalEndAt = aiEndAt != null ? correctPastDate(aiEndAt, today) : snapshot.endAt();

    // 태그는 유효한 tagId가 왔을 때만 변경 제안으로 보고, 아니면 기존 태그 유지.
    Long finalTagId = validTagId != null ? validTagId : snapshot.tagId();
    String finalTagName = validTagId != null ? tagsById.get(validTagId).name() : snapshot.tagName();

    List<TipNoteChangedField> changed =
        computeChangedFields(
            snapshot, finalTitle, finalType, finalDays, finalTime, finalEndAt, finalTagName);
    if (changed.isEmpty()) {
      return null; // 기존과 완전히 같은 "수정" — 의미 없는 카드
    }

    return new TipNoteDraft.Item(
        TipNoteAction.MODIFY_ROUTINE,
        snapshot.id(),
        finalTitle,
        finalTagId,
        null,
        finalEndAt,
        finalTime,
        finalType,
        finalDays,
        changed);
  }

  /** DB 스냅샷과 최종 상태를 비교해 바뀐 필드만 before→after로 만든다. 화면 diff의 유일한 출처. */
  private List<TipNoteChangedField> computeChangedFields(
      RoutineSnapshot snapshot,
      String title,
      RoutineType type,
      List<Integer> days,
      LocalTime time,
      LocalDateTime endAt,
      String tagName) {
    List<TipNoteChangedField> changed = new ArrayList<>();
    if (!Objects.equals(snapshot.title(), title)) {
      changed.add(new TipNoteChangedField("title", snapshot.title(), title));
    }
    if (snapshot.routineType() != type) {
      changed.add(
          new TipNoteChangedField("routineType", snapshot.routineType().name(), type.name()));
    }
    if (!Objects.equals(snapshot.routineDays(), days)) {
      changed.add(
          new TipNoteChangedField("routineDays", daysText(snapshot.routineDays()), daysText(days)));
    }
    if (!Objects.equals(snapshot.routineTime(), time)) {
      changed.add(
          new TipNoteChangedField("routineTime", timeText(snapshot.routineTime()), timeText(time)));
    }
    if (!Objects.equals(snapshot.endAt(), endAt)) {
      changed.add(
          new TipNoteChangedField("routineEndAt", dateText(snapshot.endAt()), dateText(endAt)));
    }
    if (!Objects.equals(snapshot.tagName(), tagName)) {
      changed.add(new TipNoteChangedField("tag", snapshot.tagName(), tagName));
    }
    return changed;
  }

  // ---------- 필드별 파싱·보정 ----------

  /** AI가 준 tagId를 유저의 실제 태그로 검증한다. 숫자가 아니거나 목록에 없으면 태그 없음(null)으로 교정. */
  private Long validTagIdOrNull(JsonNode node, Map<Long, TagOption> tagsById) {
    Long candidate = longOrNullLenient(node);
    return candidate != null && tagsById.containsKey(candidate) ? candidate : null;
  }

  /** 과거 날짜를 이번 달 마지막 날로 교정한다(시간 부분은 유지). */
  private LocalDateTime correctPastDate(LocalDateTime dateTime, LocalDate today) {
    if (dateTime.toLocalDate().isBefore(today)) {
      return endOfMonth(today).atTime(dateTime.toLocalTime());
    }
    return dateTime;
  }

  private LocalDate endOfMonth(LocalDate today) {
    return today.withDayOfMonth(today.lengthOfMonth());
  }

  /** DAILY는 날짜 배열이 없어야 정상(null로 정규화), WEEKLY/MONTHLY는 유효한 배열 필수 — 아니면 카드 버림. */
  private List<Integer> normalizeDays(RoutineType type, List<Integer> days) {
    if (type == RoutineType.DAILY) {
      if (days != null && !days.isEmpty()) {
        throw new IllegalArgumentException("DAILY 루틴에 반복 날짜가 지정됨");
      }
      return null;
    }
    if (!RoutineDays.isValid(type, days)) {
      throw new IllegalArgumentException("반복 타입과 반복 날짜가 맞지 않음: " + type);
    }
    return days;
  }

  /** "yyyy-MM-dd HH:mm" / ISO / "yyyy-MM-dd"(→ 23:59:59)를 받아준다. 그 외 형식은 예외 → 카드 버림. */
  private LocalDateTime parseDateTime(String text) {
    if (text == null) {
      return null;
    }
    try {
      return LocalDateTime.parse(text, DATE_TIME_FORMAT);
    } catch (DateTimeParseException ignored) {
      // fall through
    }
    try {
      return LocalDateTime.parse(text);
    } catch (DateTimeParseException ignored) {
      // fall through
    }
    return LocalDate.parse(text).atTime(23, 59, 59);
  }

  /** 반복 종료일(yyyy-MM-dd). 없으면 null(무기한). 시각은 기존 루틴 종료일 관례(자정)를 따른다. */
  private LocalDateTime parseEndDate(String text) {
    if (text == null) {
      return null;
    }
    try {
      return LocalDate.parse(text).atStartOfDay();
    } catch (DateTimeParseException e) {
      return parseDateTime(text).toLocalDate().atStartOfDay();
    }
  }

  private LocalTime parseTime(String text) {
    return text != null ? LocalTime.parse(text) : null;
  }

  private void requireTitle(String title) {
    if (title == null || title.isBlank()) {
      throw new IllegalArgumentException("제목 없는 추천 카드");
    }
  }

  private String truncate(String title) {
    return title.length() > MAX_TITLE_LENGTH ? title.substring(0, MAX_TITLE_LENGTH) : title;
  }

  private String daysText(List<Integer> days) {
    return days == null ? "없음" : days.toString();
  }

  private String timeText(LocalTime time) {
    return time == null ? "없음" : time.format(DateTimeFormatter.ofPattern("HH:mm"));
  }

  private String dateText(LocalDateTime dateTime) {
    return dateTime == null ? "없음" : dateTime.toLocalDate().toString();
  }

  private String textOrNull(JsonNode node) {
    return node.isNull() || node.isMissingNode() ? null : node.asText(null);
  }

  /** 숫자/숫자문자열만 허용, 그 외는 예외(→ 카드 버림). targetRoutineId처럼 틀리면 위험한 필드용. */
  private Long longOrNull(JsonNode node) {
    if (node.isNull() || node.isMissingNode()) {
      return null;
    }
    if (node.isIntegralNumber()) {
      return node.longValue();
    }
    if (node.isTextual()) {
      return Long.parseLong(node.asText().trim());
    }
    throw new IllegalArgumentException("숫자 필드가 숫자가 아님");
  }

  /** 숫자로 못 읽으면 조용히 null. tagId처럼 틀려도 교정(태그 없음)하면 되는 필드용. */
  private Long longOrNullLenient(JsonNode node) {
    try {
      return longOrNull(node);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private List<Integer> intListOrNull(JsonNode node) {
    if (node.isNull() || node.isMissingNode()) {
      return null;
    }
    if (!node.isArray()) {
      throw new IllegalArgumentException("반복 날짜가 배열이 아님");
    }
    List<Integer> days = new ArrayList<>();
    for (JsonNode e : node) {
      if (e.isIntegralNumber()) {
        days.add(e.intValue());
      } else if (e.isTextual()) {
        days.add(Integer.parseInt(e.asText().trim()));
      } else {
        throw new IllegalArgumentException("반복 날짜 원소가 숫자가 아님");
      }
    }
    return days;
  }
}
