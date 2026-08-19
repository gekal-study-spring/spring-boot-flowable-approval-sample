package cn.gekal.spring.approval.presentation;

import cn.gekal.spring.approval.domain.model.ApprovalNotPermittedException;
import cn.gekal.spring.approval.domain.model.ApprovalTaskNotFoundException;
import cn.gekal.spring.approval.domain.model.ExpenseRequestNotFoundException;
import cn.gekal.spring.approval.domain.model.InvalidExpenseRequestException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 例外をエラーレスポンスへ変換する。個々の Controller では try/catch しない。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleValidation(
      MethodArgumentNotValidException e, HttpServletRequest request) {
    String detail =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));
    return build(HttpStatus.BAD_REQUEST, "入力値が不正です", detail, request);
  }

  @ExceptionHandler(InvalidExpenseRequestException.class)
  public ResponseEntity<ErrorResponse> handleInvalidRequest(
      InvalidExpenseRequestException e, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, "申請内容が不正です", e.getMessage(), request);
  }

  @ExceptionHandler({ExpenseRequestNotFoundException.class, ApprovalTaskNotFoundException.class})
  public ResponseEntity<ErrorResponse> handleNotFound(
      RuntimeException e, HttpServletRequest request) {
    return build(HttpStatus.NOT_FOUND, "対象が見つかりません", e.getMessage(), request);
  }

  @ExceptionHandler(ApprovalNotPermittedException.class)
  public ResponseEntity<ErrorResponse> handleNotPermitted(
      ApprovalNotPermittedException e, HttpServletRequest request) {
    return build(HttpStatus.FORBIDDEN, "この操作は許可されていません", e.getMessage(), request);
  }

  private ResponseEntity<ErrorResponse> build(
      HttpStatus status, String title, String detail, HttpServletRequest request) {
    ErrorResponse body =
        ErrorResponse.of(
            "https://example.com/errors/" + status.value(),
            title,
            status.value(),
            detail,
            request == null ? null : request.getRequestURI());
    return ResponseEntity.status(status).body(body);
  }
}
