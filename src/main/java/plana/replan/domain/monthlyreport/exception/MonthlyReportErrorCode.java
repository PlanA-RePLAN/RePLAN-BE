package plana.replan.domain.monthlyreport.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import plana.replan.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum MonthlyReportErrorCode implements ErrorCode {
  REPORT_NOT_FOUND(404, "해당 월의 통계 리포트가 없습니다."),
  TIP_NOTE_NOT_FOUND(404, "해당 월의 팁노트가 없습니다."),
  TIP_NOTE_ITEM_NOT_FOUND(404, "팁노트 추천 카드를 찾을 수 없습니다."),
  TIP_NOTE_NOT_LATEST(400, "가장 최근 팁노트에서만 반영하거나 끝낼 수 있습니다."),
  TIP_NOTE_ITEM_NOT_APPLICABLE(400, "이미 처리됐거나 기한이 지나 반영할 수 없는 카드입니다.");

  private final int status;
  private final String message;
}
