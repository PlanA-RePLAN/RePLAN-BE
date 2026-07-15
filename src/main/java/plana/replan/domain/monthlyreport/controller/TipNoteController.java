package plana.replan.domain.monthlyreport.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import plana.replan.domain.monthlyreport.dto.TipNoteApplyRequest;
import plana.replan.domain.monthlyreport.dto.TipNoteApplyResponse;
import plana.replan.domain.monthlyreport.dto.TipNoteResponse;
import plana.replan.domain.monthlyreport.service.TipNoteService;
import plana.replan.global.common.ApiResult;

@RestController
@RequestMapping("/api/tip-notes")
@RequiredArgsConstructor
@Validated
public class TipNoteController implements TipNoteControllerDocs {

  private final TipNoteService tipNoteService;

  @Override
  @GetMapping
  public ResponseEntity<ApiResult<TipNoteResponse>> getTipNote(
      @AuthenticationPrincipal Long userId,
      @RequestParam @Min(1) int year,
      @RequestParam @Min(1) @Max(12) int month) {
    return ResponseEntity.ok(ApiResult.ok(tipNoteService.getTipNote(userId, year, month)));
  }

  @Override
  @PostMapping("/{noteId}/apply")
  public ResponseEntity<ApiResult<TipNoteApplyResponse>> applyTipNote(
      @AuthenticationPrincipal Long userId,
      @PathVariable Long noteId,
      @Valid @RequestBody TipNoteApplyRequest request) {
    return ResponseEntity.ok(ApiResult.ok(tipNoteService.apply(userId, noteId, request)));
  }

  @Override
  @PostMapping("/{noteId}/dismiss")
  public ResponseEntity<ApiResult<Void>> dismissTipNote(
      @AuthenticationPrincipal Long userId, @PathVariable Long noteId) {
    tipNoteService.dismiss(userId, noteId);
    return ResponseEntity.ok(ApiResult.ok());
  }
}
