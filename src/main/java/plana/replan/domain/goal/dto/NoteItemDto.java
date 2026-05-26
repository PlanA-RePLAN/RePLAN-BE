package plana.replan.domain.goal.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "특이사항 항목")
public record NoteItemDto(
    @Schema(description = "항목 제목", example = "교재 및 컨텐츠") String title,
    @Schema(description = "항목 내용", example = "해커스 토익 기출 VOCA, 해커스 토익 기출문제집 1000제 (LC/RC)")
        String content) {}
