package cn.gekal.spring.approval.presentation;

import java.time.LocalDateTime;

/** RFC 7807 風のエラーレスポンス。 */
public record ErrorResponse(
    String type,
    String title,
    int status,
    String detail,
    String instance,
    LocalDateTime timestamp) {

  public static ErrorResponse of(
      String type, String title, int status, String detail, String path) {
    return new ErrorResponse(type, title, status, detail, path, LocalDateTime.now());
  }
}
