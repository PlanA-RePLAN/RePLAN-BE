package plana.replan.domain.monthlyreport.service;

import plana.replan.domain.monthlyreport.entity.AiInsight;

/**
 * 한 번의 Gemini 호출로 함께 만들어지는 결과 묶음. 인사이트와 팁노트는 따로 파싱하므로 한쪽만 성공할 수 있다 — tipNote가 null이면 그 달 팁노트는 작성 팁
 * 없이 저장되지 않는다(리포트만 저장).
 */
public record MonthlyAiResult(AiInsight aiInsight, TipNoteDraft tipNote) {}
