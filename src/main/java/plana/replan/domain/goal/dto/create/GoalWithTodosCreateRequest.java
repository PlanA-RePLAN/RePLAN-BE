package plana.replan.domain.goal.dto.create;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "목표+투두 일괄 생성 요청")
public record GoalWithTodosCreateRequest(
    @Schema(
            description = "목표 제목",
            example = "토익 900점 달성",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "제목은 필수입니다.")
        String title,
    @Schema(description = "목표 기한 날짜 (yyyy-MM-dd 형식)", example = "2025-12-31") String dueDate,
    @Schema(description = "목표 기한 시간 (HH:mm 형식). dueDate 없이 단독 사용 불가.", example = "09:00")
        String dueTime,
    @Schema(description = "참고 자료 (URL 또는 메모)", example = "https://toeic.ets.org") String reference,
    @Schema(description = "생성할 투두 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "투두 목록은 필수입니다.")
        @Valid
        List<TodoItemRequest> todos) {}
