package plana.replan.global.exception;

public interface ErrorCode {
  int getStatus();

  String getMessage();

  String name();
}
