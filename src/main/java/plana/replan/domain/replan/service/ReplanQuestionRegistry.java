package plana.replan.domain.replan.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import plana.replan.domain.replan.dto.QuestionType;
import plana.replan.domain.replan.dto.ReplanQuestion;
import plana.replan.domain.replan.entity.FailureReasonCode;

/**
 * 실패 사유 코드 → 추가 질문 고정 매핑. "이 사유면 어떤 질문이 필요한가"는 리플랜 로직 문서에 정의된 결정론적 비즈니스 규칙이므로, LLM 판단이나 프론트 선택이 아니라
 * 백엔드가 이 표로 결정한다. 질문이 필요 없는 사유는 매핑에 없으며(추가 질문 없이 바로 추천), 매핑에 없는 코드(직접입력 등)도 질문 없이 추천으로 넘어간다.
 */
public final class ReplanQuestionRegistry {

  private ReplanQuestionRegistry() {}

  private static final Map<String, ReplanQuestion> QUESTIONS = new LinkedHashMap<>();

  static {
    // 심리적 저항
    QUESTIONS.put(
        "MENTAL_HARD_TO_START",
        new ReplanQuestion(
            "mental_start_block",
            QuestionType.CHIP,
            "무엇이 가장 막막했나요?",
            List.of("무엇부터 시작할지 몰라서", "시간이나 에너지가 많이 들 것 같아서", "완벽하게 해내고 싶어서")));
    QUESTIONS.put(
        "MENTAL_DISTRACTION",
        new ReplanQuestion(
            "distraction_type",
            QuestionType.TEXT,
            "어떤 유혹에 시간을 뺏기셨나요? (예: 스마트폰, 유튜브, 갑자기 방 청소 등)",
            null));
    // 컨디션 난조
    QUESTIONS.put(
        "CONDITION_EXHAUSTED",
        new ReplanQuestion("focus_time", QuestionType.TEXT, "내일 언제가 가장 집중이 잘 되시나요?", null));
    QUESTIONS.put(
        "CONDITION_SLEEP",
        new ReplanQuestion("reschedule_date", QuestionType.TEXT, "미룬 할 일은 언제 처리 가능하세요?", null));
    QUESTIONS.put(
        "CONDITION_PAIN", new ReplanQuestion("pain_area", QuestionType.TEXT, "어디가 불편하신가요?", null));
    // 목표/계획 개선
    QUESTIONS.put(
        "GOAL_NO_PLAN",
        new ReplanQuestion("volume_deadline", QuestionType.TEXT, "전체 분량과 마감일이 어떻게 되나요?", null));
    QUESTIONS.put(
        "GOAL_TOO_MANY_TODOS",
        new ReplanQuestion(
            "defer_todos", QuestionType.TODO_SELECT, "다른 날로 미룰 덜 급한 투두를 선택하세요", null));
    QUESTIONS.put(
        "GOAL_NO_PRIORITY",
        new ReplanQuestion(
            "priority_targets", QuestionType.TODO_SELECT, "우선순위를 매길 투두를 선택하세요", null));
    // 예상치 못한 방해
    QUESTIONS.put(
        "INTERRUPT_URGENT",
        new ReplanQuestion("defer_deadline", QuestionType.TEXT, "이 일을 언제로 미룰까요? (직접 마감 입력)", null));
  }

  /** 선택한 실패 사유들에 대해 필요한 추가 질문 목록을 반환한다. 같은 질문 key는 중복 제거. 질문이 필요 없으면 빈 목록. */
  public static List<ReplanQuestion> forReasonCodes(List<String> reasonCodes) {
    List<ReplanQuestion> result = new ArrayList<>();
    if (reasonCodes == null) {
      return result;
    }
    for (String code : reasonCodes) {
      ReplanQuestion q = resolve(code);
      if (q != null && result.stream().noneMatch(r -> r.key().equals(q.key()))) {
        result.add(q);
      }
    }
    return result;
  }

  /**
   * 코드에 직접 매핑된 질문을 찾고, 없으면 상위 사유(부모)로 거슬러 올라가 찾는다. 예: 수면 하위 코드(CONDITION_SLEEP_3H_UNDER)는 부모
   * CONDITION_SLEEP의 질문(reschedule_date)을 상속한다.
   */
  private static ReplanQuestion resolve(String code) {
    ReplanQuestion direct = QUESTIONS.get(code);
    if (direct != null) {
      return direct;
    }
    try {
      FailureReasonCode fc = FailureReasonCode.valueOf(code);
      while (fc != null) {
        ReplanQuestion q = QUESTIONS.get(fc.name());
        if (q != null) {
          return q;
        }
        fc = fc.getParent();
      }
    } catch (IllegalArgumentException ignored) {
      // enum에 없는 코드(직접입력 등)는 질문 없음
    }
    return null;
  }
}
