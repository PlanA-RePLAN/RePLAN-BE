package plana.replan.domain.notification.event;

public record MonthlyReportCreatedEvent(Long userId, Long reportId, int month) {}
