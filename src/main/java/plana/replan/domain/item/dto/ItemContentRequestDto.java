package plana.replan.domain.item.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import plana.replan.domain.routine.entity.RoutineType;

@Schema(
    description =
        """
        통합 아이템 내용 수정 요청.
        - kind=TODO: todoId 필수. title/dueDate/tagId 수정 (반복 필드를 주면 투두가 반복으로 전환됨 — 기존 투두 수정 API와 동일)
        - kind=ROUTINE + scope=THIS: routineId+date 필수. 그 날짜 회차만 title/tagId/routineTime 수정
          (routineTime은 그 회차만의 마감시간이 됨. null이면 루틴 기본 시간 유지)
        - kind=ROUTINE + scope=ALL: routineId 필수. 반복 전체(엄마 루틴) 수정 — title, routineType 필수
        """)
public record ItemContentRequestDto(
    @Schema(description = "아이템 종류", example = "TODO") @NotNull ItemKind kind,
    @Schema(description = "투두 ID (kind=TODO일 때 필수)", example = "42") Long todoId,
    @Schema(description = "루틴 ID (kind=ROUTINE일 때 필수)", example = "7") Long routineId,
    @Schema(description = "회차 날짜 (kind=ROUTINE + scope=THIS일 때 필수)", example = "2026-07-10")
        LocalDate date,
    @Schema(description = "적용 범위. kind=ROUTINE일 때 필수 (THIS=이 회차만 / ALL=반복 전체)") ItemScope scope,
    @Schema(description = "제목", example = "영어 단어 100개 외우기") String title,
    @Schema(description = "마감 일시 (TODO 전용). null이면 마감일 제거") LocalDateTime dueDate,
    @Schema(description = "태그 ID. null이면 태그 제거(TODO/전체수정) 또는 기본값 유지(회차수정)") Long tagId,
    @Schema(description = "반복 유형 (TODO의 반복 전환 또는 ROUTINE 전체 수정용)") RoutineType routineType,
    @Schema(description = "반복 날짜 배열") List<Integer> routineDays,
    @Schema(description = "반복 시각 (HH:mm:ss). ROUTINE+THIS일 땐 그 회차만의 마감시간") LocalTime routineTime,
    @Schema(description = "반복 종료일 (ROUTINE 전체 수정 전용). null이면 종료일 제거")
        LocalDateTime repeatEndDate) {}
