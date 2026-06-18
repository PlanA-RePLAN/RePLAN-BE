package plana.replan.domain.replan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "리플랜 추천 결과")
public record ReplanRecommendResponse(
    @Schema(description = "AI가 입력을 정리한 요약('이렇게 정리했어요')", nullable = true) String summary,
    @Schema(description = "줄글 가이드(멘탈케어/조율 방향)", nullable = true) String tipNote,
    @Schema(description = "추천 작업 목록") List<ReplanOperation> operations) {}
