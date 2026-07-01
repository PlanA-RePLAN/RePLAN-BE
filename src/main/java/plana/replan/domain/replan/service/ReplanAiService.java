package plana.replan.domain.replan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import plana.replan.domain.replan.dto.ChangedField;
import plana.replan.domain.replan.dto.RecommendInput;
import plana.replan.domain.replan.dto.ReplanAction;
import plana.replan.domain.replan.dto.ReplanOperation;
import plana.replan.domain.replan.exception.ReplanErrorCode;
import plana.replan.global.exception.CustomException;

/**
 * 리플랜 추천 생성부. HTTP/세션/엔티티에 의존하지 않는 순수 서비스라 배치에서도 재사용할 수 있다. "추가 질문이 필요한지"는 백엔드가 {@link
 * ReplanQuestionRegistry}로 결정하고, 이 서비스는 (질문이 필요 없거나 답변이 모인 뒤) 카테고리별 개선 로직에 따른 추천만 생성한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplanAiService {

  private static final String GEMINI_URL =
      "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-flash-lite:generateContent";

  @Value("${gemini.api-key}")
  private String apiKey;

  private final RestClient geminiRestClient;
  private final ObjectMapper objectMapper = new ObjectMapper();

  /** 카테고리별 로직에 따른 추천 작업 목록을 생성한다. */
  public List<ReplanOperation> generateRecommend(RecommendInput input) {
    return parseRecommend(callGemini(buildRecommendPrompt(input)));
  }

  public String buildRecommendPrompt(RecommendInput input) {
    String answers =
        input.answers().isEmpty()
            ? "없음"
            : input.answers().stream().map(this::formatAnswer).collect(Collectors.joining("\n"));
    String routineInfo =
        input.routine() ? "반복 투두(루틴). routineType=" + input.routineType() : "일반 투두";
    String prompt =
        """
        당신은 일정 재계획(리플랜) 도우미입니다.
        사용자가 아래 투두를 끝내지 못한 이유에 맞춰, 투두 수정·추가 제안을 만드세요.

        오늘 날짜: %s
        대상 투두 ID: %d
        대상 투두: %s
        투두 종류: %s
        마감일: %s
        태그: %s
        실패 이유: %s
        추가 질문 답변:
        %s

        [카테고리별 개선 로직 — 실패 이유와 답변에 맞는 것을 적용]
        ▷ 심리적 저항
        - 무엇부터 시작할지 몰라서: 마일스톤 쪼개기 — 진입장벽이 가장 낮은 첫 마이크로 액션을 포함해 2~3개로 분할하고 마감기한을 순차 설정해 순서가 보이게 한다.
        - 시간이나 에너지가 많이 들 것 같아서: 타임박싱 — 첫 투두에 '15분' 제한을 명시하고 전체를 구역/단계로 나눠 순차 제시.
        - 완벽하게 해내고 싶어서: 초안 유도 — 1단계 '퀄리티 무관 초안 쓰기', 2단계 수정/완성 투두로 분리.
        - 딴짓을 하다가 미뤘어요: 답변을 디지털/물리·공간/생산적 회피로 분류해 기존 투두 앞단에 환경 통제 조건 추가.
          · 디지털 → 앱 잠금/비행기모드/타임랩스. · 물리·공간 → '카페·도서관 등으로 이동' 선행 태스크. · 생산적 회피 → '딴짓 말고 빈 수첩에 적어만 두기' 룰을 타이틀이나 tipNote에.
        - 의욕·동기가 부족했어요 / 당장 안 해도 되서 미루다 미뤘어요: 부담 없이 시작할 아주 작은 첫 행동 1개를 앞에 두어 진입 장벽을 낮춘다.
        ▷ 컨디션 난조
        - 체력 방전/에너지 소모: 오늘 일정은 '눈으로 훑어보기' 등 가벼운 형태로 수정하고, 메인 실행 투두는 답변한 집중 시간대(내일)로 이관.
        - 수면부족/피로 누적: 오늘 일정 중단 + 취침 투두 추가(가장 권장 A안). 미룬 할 일은 답변한 날짜로 마감 수정.
        - 신체적 통증: 답변 분석. · 일회성(몸살) → 전면 중단·휴식. · 반복성(손목·허리) → 보호대/스트레칭 추가 + 50분 작업/10분 휴식 뽀모도로. · 일시적(안구건조) → 50분/10분 뽀모도로 + 시각 매체를 청각·아날로그로 전환 제안.
        - 번아웃이 왔어요: 무리한 추가 없이 휴식·회복을 우선한다. '성과·변화가 안 보여 무기력'이면 큰 목표를 잘게 쪼개 작은 성취가 바로 보이는 1~2개만, '방향성 상실'이면 목표를 가볍게 점검하는 투두를 제안한다.
        ▷ 목표/계획 개선
        - 구체적 계획 수립 실패: 답변한 분량·마감일로 일간/주간/단계별 로드맵으로 분할.
        - 하루에 계획한 할 일 개수가 많았어요: 답변에서 고른 덜 급한 투두를 다른 날짜로 미루는 수정.
        - 특정 할 일의 분량이 많았어요: 마감을 '기존 배치 다음날, 기존 시간 +1시간'으로 수정.
        - 우선순위를 정하지 못했어요: 답변에서 고른 투두들을 마감·소요시간·중요도로 판단해 각 제목 앞에 [1] [2] [3]을 붙여 수정.
        - 시간이 예측보다 더 소요됐어요: 마감을 '기존 배치 다음날, 기존 시간 +30분'으로 수정해 여유를 준다.
        ▷ 예상치 못한 방해
        - 돌발 상황: 해당 투두 마감을 +2시간 또는 다음 날로 미룸.
        - 시끄럽거나 작업할 공간이 마땅치 않았어요: '조용한 장소/소음 차단 세팅' 선행 투두 추가.
        - 타인의 요청·연락이 계속 들어왔어요: '방해금지 모드/메신저 끄기' 조건 삽입.
        - 더 급한 일이 생겼어요: 답변으로 받은 마감으로 수정.
        - 다른 일정이 늦게 끝났어요: 반복 투두면 MODIFY_ROUTINE으로 기준 시작 시간을 15분 늦추고, 단일 투두면 '내일 오전 첫 할 일로 일정 간격 15분씩 벌려두기' 단일 투두 하나만 제안.
        ▷ 위 1~4에 매핑되지 않는 복합/직접입력: 억지로 끼워 맞추지 말고 입력 맥락을 분석해 맞춤 tipNote와 투두를 제로베이스로 도출(아래 공통 포맷 제약은 유지).

        [공통 규칙]
        1. 결과는 기존 투두 형식(제목/마감기한/태그/반복)만 사용. 새로운 형태 금지.
        2. 억지스러운 멘탈케어성 내용(명언 읽기 등)은 투두로 만들지 말 것.
        3. 상호 배타적인 안(완전 휴식 vs 타협 실행)을 동시에 넣지 말 것. 가장 권장하는 한 가지 방향(A안)만.
        4. action은 ADD/MODIFY_TODO/MODIFY_ROUTINE/CREATE_ROUTINE 중 하나.
           - 일반 투두를 그 자리에서 고치면 MODIFY_TODO(targetTodoId=대상), 새 투두는 ADD(targetTodoId=null).
           - 마감이 이미 지난 일반 투두는 MODIFY_TODO 대신 ADD로 새 투두를 만든다.
           - 반복 투두 규칙 변경은 MODIFY_ROUTINE(targetTodoId=대상 투두 ID), 새 루틴은 CREATE_ROUTINE.
           - MODIFY_TODO의 targetTodoId에는 위 '대상 투두 ID'를, 다른 기존 투두 수정(우선순위 등)은 아래 '선택 투두' 목록의 실제 ID만 사용. ID를 임의로 만들지 않는다.
        5. changedFields: 수정(MODIFY_TODO/MODIFY_ROUTINE)에서 바뀐 필드만 {field, before, after}로 채운다. field는 title/dueTime/tag/routineType.
           새로 만드는 ADD·CREATE_ROUTINE은 changedFields를 빈 배열([])로 둔다.
        6. dueDate는 yyyy-MM-dd 또는 null, dueTime은 HH:mm 또는 null. routineType은 DAILY/WEEKLY/MONTHLY 또는 null. routineDays는 정수 배열 또는 null (DAILY는 null / WEEKLY는 요일 인덱스 배열: 월=0·화=1·수=2·목=3·금=4·토=5·일=6, 예 월·수·금=[0,2,4] / MONTHLY는 일자 배열 1~31, 예 3일·20일=[3,20]).
           - CREATE_ROUTINE의 dueDate는 '반복을 끝낼 종료일'을 뜻한다(이 날짜 이후로는 회차를 만들지 않음). 무기한 반복이면 null로 둔다. 새 루틴의 반복 시각은 dueTime으로 지정한다.

        반드시 아래 JSON만 출력 (다른 설명 없이):
        {"operations":[{"action":"","targetTodoId":null,"title":"","dueDate":null,"dueTime":null,"tagId":null,"routineType":null,"routineDays":null,"changedFields":[{"field":"","before":null,"after":""}]}]}
        """
            .formatted(
                input.today(),
                input.anchorTodoId(),
                input.anchorTitle(),
                routineInfo,
                input.anchorDueDate() != null ? input.anchorDueDate() : "없음",
                input.anchorTagName() != null ? input.anchorTagName() : "없음",
                String.join(", ", input.reasonLabels()),
                answers);
    return prompt + refreshStyleBlock(input.refreshCount());
  }

  /** 새로고침 회차(1~3)에 맞는 스타일 안내 블록. 0/그 외는 빈 문자열(0회차는 프롬프트 변경 없음). */
  static String refreshStyleBlock(int refreshCount) {
    String line =
        switch (refreshCount) {
          case 1 -> "1회차(여유): 기간은 기존 예상 소요의 1.5배 이상으로 넉넉히 잡고, 할 일은 아주 잘게(마이크로) 쪼개며, 가장 쉽고 만만한 것부터 시작하도록 정순으로 배치한다.";
          case 2 -> "2회차(벼락치기): 버퍼 없이 짧게(15~25분 타임박싱) 잡고, 핵심 1개(1-Pick)만 남기며, 가장 어렵고 중요한 것부터 처리하도록 역순으로 배치한다.";
          case 3 -> "3회차(환경 제약): 기간·덩어리·난이도는 적정 수준으로 두되, 시간대를 옮기거나(다른 시간/요일) 장소·환경을 통제하는(조용한 곳 이동, 방해금지 등) 방향으로 제안한다.";
          default -> null;
        };
    if (line == null) {
      return "";
    }
    return "\n\n[이번 새로고침 스타일]\n"
        + line
        + "\n위 스타일을 우선으로 적용하되, 결과는 위 공통 규칙(투두 형식·action 종류·JSON 포맷)을 그대로 따른다.";
  }

  private String formatAnswer(RecommendInput.AnswerInput a) {
    StringBuilder sb = new StringBuilder("- ").append(a.key()).append(": ");
    if (a.text() != null) sb.append(a.text());
    if (a.selectedTodoLabels() != null && !a.selectedTodoLabels().isEmpty()) {
      sb.append(" [선택 투두: ").append(String.join(", ", a.selectedTodoLabels())).append("]");
    } else if (a.selectedTodoIds() != null && !a.selectedTodoIds().isEmpty()) {
      sb.append(" [선택투두ID: ").append(a.selectedTodoIds()).append("]");
    }
    return sb.toString();
  }

  String callGemini(String prompt) {
    Map<String, Object> body =
        Map.of(
            "tools", new Object[] {Map.of("google_search", Map.of())},
            "contents", new Object[] {Map.of("parts", new Object[] {Map.of("text", prompt)})});
    try {
      String response =
          geminiRestClient
              .post()
              .uri(GEMINI_URL + "?key=" + apiKey)
              .contentType(MediaType.APPLICATION_JSON)
              .body(body)
              .retrieve()
              .body(String.class);
      JsonNode root = objectMapper.readTree(response);
      return root.path("candidates")
          .path(0)
          .path("content")
          .path("parts")
          .path(0)
          .path("text")
          .asText();
    } catch (RestClientException e) {
      log.error("리플랜 Gemini API 호출 실패", e);
      throw new CustomException(ReplanErrorCode.REPLAN_GEMINI_API_ERROR);
    } catch (Exception e) {
      log.error("리플랜 Gemini 응답 처리 실패", e);
      throw new CustomException(ReplanErrorCode.REPLAN_GEMINI_PARSE_ERROR);
    }
  }

  public List<ReplanOperation> parseRecommend(String raw) {
    try {
      JsonNode root = objectMapper.readTree(extractJson(raw));
      List<ReplanOperation> operations = new ArrayList<>();
      for (JsonNode op : root.path("operations")) {
        ReplanAction action = ReplanAction.valueOf(op.path("action").asText());
        // 새로 만드는 작업(ADD/CREATE_ROUTINE)은 before/after diff가 없으므로 changedFields를 비운다.
        List<ChangedField> changed = new ArrayList<>();
        if (action == ReplanAction.MODIFY_TODO || action == ReplanAction.MODIFY_ROUTINE) {
          for (JsonNode cf : op.path("changedFields")) {
            changed.add(
                new ChangedField(
                    cf.path("field").asText(),
                    textOrNull(cf.path("before")),
                    textOrNull(cf.path("after"))));
          }
        }
        operations.add(
            new ReplanOperation(
                action,
                longOrNull(op.path("targetTodoId")),
                textOrNull(op.path("title")),
                textOrNull(op.path("dueDate")),
                textOrNull(op.path("dueTime")),
                longOrNull(op.path("tagId")),
                textOrNull(op.path("routineType")),
                intListOrNull(op.path("routineDays")),
                changed));
      }
      return operations;
    } catch (Exception e) {
      // 원문(raw)에는 사용자의 투두/사유 텍스트가 들어 있어 그대로 로깅하지 않고 길이만 남긴다.
      log.error("리플랜 추천 응답 파싱 실패 (rawLength={})", raw == null ? 0 : raw.length(), e);
      throw new CustomException(ReplanErrorCode.REPLAN_GEMINI_PARSE_ERROR);
    }
  }

  private String textOrNull(JsonNode node) {
    return node.isNull() || node.isMissingNode() ? null : node.asText(null);
  }

  // 숫자 필드는 숫자/숫자문자열만 허용한다. 그 외 값은 0으로 묵시 변환하지 않고 파싱 실패로 처리한다.
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
    throw new IllegalArgumentException("숫자 필드(targetTodoId/tagId)가 숫자가 아닙니다: " + node);
  }

  /** routineDays: 정수 배열. 없으면 null. 각 원소는 숫자/숫자문자열만 허용. */
  private List<Integer> intListOrNull(JsonNode node) {
    if (node.isNull() || node.isMissingNode()) {
      return null;
    }
    if (!node.isArray()) {
      throw new IllegalArgumentException("routineDays가 배열이 아닙니다: " + node);
    }
    List<Integer> days = new ArrayList<>();
    for (JsonNode e : node) {
      if (e.isIntegralNumber()) {
        days.add(e.intValue());
      } else if (e.isTextual()) {
        days.add(Integer.parseInt(e.asText().trim()));
      } else {
        throw new IllegalArgumentException("routineDays 원소가 숫자가 아닙니다: " + e);
      }
    }
    return days;
  }

  private String extractJson(String text) {
    int start = text.indexOf('{');
    int end = text.lastIndexOf('}');
    if (start == -1 || end == -1 || end < start) {
      throw new IllegalArgumentException("JSON 블록을 찾을 수 없습니다: " + text);
    }
    return text.substring(start, end + 1);
  }
}
