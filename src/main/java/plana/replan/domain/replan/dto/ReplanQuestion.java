package plana.replan.domain.replan.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "리플랜 추가 질문 1건")
public record ReplanQuestion(
    @Schema(description = "질문 식별 키", example = "priority_targets") String key,
    @Schema(description = "질문 타입. TEXT(자유입력)/TODO_SELECT(내 투두 다중선택)/CHIP(칩 선택)", example = "CHIP")
        QuestionType type,
    @Schema(description = "질문 제목", example = "우선순위 설정을 위한 참고 사항") String title,
    @Schema(description = "CHIP 타입일 때 선택지. 그 외 null", nullable = true) List<String> chips) {}
