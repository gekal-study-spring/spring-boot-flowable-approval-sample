package cn.gekal.spring.approval.presentation.api;

import cn.gekal.spring.approval.domain.model.ApprovalComment;
import java.time.LocalDateTime;

/** 承認タスクに残されたコメントのレスポンス。 */
public record ApprovalCommentResponse(String author, String message, LocalDateTime at) {

  public static ApprovalCommentResponse from(ApprovalComment comment) {
    return new ApprovalCommentResponse(comment.author(), comment.message(), comment.at());
  }
}
