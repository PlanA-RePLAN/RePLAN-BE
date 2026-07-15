package plana.replan.domain.monthlyreport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Schema(description = "팁노트 조회 응답. 최신 팁노트가 아니면(지난 달) items는 항상 빈 배열이고 작성 팁만 내려간다.")
public record TipNoteResponse(
    @Schema(description = "팁노트 ID. 반영/끝내기 요청에 사용", example = "17") Long noteId,
    @Schema(description = "팁노트가 근거로 삼은 연도(지난달 기준)", example = "2026") int year,
    @Schema(description = "팁노트가 근거로 삼은 월(지난달 기준)", example = "6") int month,
    @Schema(
            description = "투두리스트 작성 팁 카드 텍스트",
            example = "과부하 + 컨디션 저하 + 계획력 부족이 지난달의 주된 패턴이에요. 다음 달에는 무리하지 않는 구조를 제안합니다.")
        String tip,
    @Schema(description = "추천 투두 카드 목록. 남은(반영 안 한) 카드만 내려간다") List<TipNoteItemResponse> items) {

  @Schema(description = "추천 투두 카드 1장")
  public record TipNoteItemResponse(
      @Schema(description = "카드 ID. 반영 요청의 itemIds에 사용", example = "3") Long id,
      @Schema(
              description = "카드 종류. ADD_TODO=새 일반 투두, ADD_ROUTINE=새 루틴, MODIFY_ROUTINE=기존 루틴 수정",
              example = "ADD_ROUTINE")
          String action,
      @Schema(description = "투두/루틴 제목 (수정 카드는 수정 후 제목)", example = "11시 이전 취침") String title,
      @Schema(description = "태그 이름. 없으면 null", example = "Study") String tagName,
      @Schema(description = "태그 색상. 없으면 null", example = "#FF5733") String tagColor,
      @Schema(
              description = "새 일반 투두의 마감일시 (ISO 8601 형식). ADD_TODO에만 값이 있음",
              example = "2026-07-20T23:00:00")
          LocalDateTime todoDueAt,
      @Schema(
              description = "루틴 반복 종료일 (ISO 8601 형식). 루틴 카드 전용, 무기한이면 null",
              example = "2026-08-31T00:00:00")
          LocalDateTime routineEndAt,
      @Schema(description = "루틴 반복 시각 (HH:mm:ss). 루틴 카드 전용", example = "23:00:00")
          LocalTime routineTime,
      @Schema(description = "반복 유형 DAILY/WEEKLY/MONTHLY. 루틴 카드 전용", example = "DAILY")
          String routineType,
      @Schema(description = "반복 날짜 배열. WEEKLY=요일 인덱스(월=0…일=6), MONTHLY=일자(1~31), DAILY=null")
          List<Integer> routineDays,
      @Schema(description = "수정 카드의 변경 내역(변경된 필드만). 추가 카드는 빈 배열")
          List<ChangedFieldResponse> changedFields) {}

  @Schema(description = "루틴 수정 카드의 변경 내역 한 줄 (before → after)")
  public record ChangedFieldResponse(
      @Schema(
              description = "바뀐 필드 이름: title/routineType/routineDays/routineTime/routineEndAt/tag",
              example = "routineTime")
          String field,
      @Schema(description = "변경 전 값. 없었으면 \"없음\"", example = "10:00") String before,
      @Schema(description = "변경 후 값", example = "11:00") String after) {}
}
