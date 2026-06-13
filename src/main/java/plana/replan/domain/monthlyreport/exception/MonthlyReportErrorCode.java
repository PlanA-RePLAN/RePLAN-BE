package plana.replan.domain.monthlyreport.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import plana.replan.global.exception.ErrorCode;

@Getter
@RequiredArgsConstructor
public enum MonthlyReportErrorCode implements ErrorCode {
  REPORT_NOT_FOUND(404, "해당 월의 통계 리포트가 없습니다.");

  private final int status;
  private final String message;
}
