package plana.replan.domain.monthlyreport.controller;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.monthlyreport.dto.MonthlyReportResponse;
import plana.replan.domain.monthlyreport.service.MonthlyReportService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/monthly-reports")
@RequiredArgsConstructor
@Profile("local")
@Validated
public class DevMonthlyReportController {

  private final MonthlyReportService monthlyReportService;

  @PostMapping("/generate")
  public ResponseEntity<ApiResult<MonthlyReportResponse>> generateReport(
      @AuthenticationPrincipal Long userId,
      @RequestParam @Min(1) int year,
      @RequestParam @Min(1) @Max(12) int month) {
    return ResponseEntity.ok(
        ApiResult.ok(monthlyReportService.generateReport(userId, year, month)));
  }
}
