package plana.replan.domain.replan.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "변경된 필드(파란색 강조용). before→after")
public record ChangedField(
    @Schema(description = "필드명. title(내용)/dueTime(시간)/tag(태그)/routineType(반복)",
            example = "title") String field,
    @Schema(description = "원래 값. ADD면 null", nullable = true, example = "데이터 분석 공부하기")
        String before,
    @Schema(description = "바뀐 값", example = "데이터 분석 1~2강 수강") String after) {}
