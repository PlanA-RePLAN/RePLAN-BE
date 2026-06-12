package plana.replan.domain.monthlyreport.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
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
public class MonthlyReportController implements MonthlyReportControllerDocs {

  private final MonthlyReportService monthlyReportService;

  @Override
  @GetMapping
  public ResponseEntity<ApiResult<MonthlyReportResponse>> getReport(
      @AuthenticationPrincipal Long userId, @RequestParam int year, @RequestParam int month) {
    return ResponseEntity.ok(ApiResult.ok(monthlyReportService.getReport(userId, year, month)));
  }

  @Override
  @PostMapping("/generate")
  public ResponseEntity<ApiResult<MonthlyReportResponse>> generateReport(
      @AuthenticationPrincipal Long userId, @RequestParam int year, @RequestParam int month) {
    return ResponseEntity.ok(
        ApiResult.ok(monthlyReportService.generateReport(userId, year, month)));
  }
}
