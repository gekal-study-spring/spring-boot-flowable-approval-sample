package cn.gekal.spring.approval.presentation.api;

import cn.gekal.spring.approval.domain.model.ApprovalHistoryEntry;
import java.time.LocalDateTime;
import java.util.List;

/** 承認履歴1件のレスポンス。 */
public record ApprovalHistoryResponse(
    String type,
    String name,
    String activityId,
    String assignee,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    Long durationMillis,
    boolean running,
    List<ApprovalCommentResponse> comments) {

  public static ApprovalHistoryResponse from(ApprovalHistoryEntry entry) {
    return new ApprovalHistoryResponse(
        entry.type().name(),
        entry.name(),
        entry.activityId(),
        entry.assignee(),
        entry.startedAt(),
        entry.endedAt(),
        entry.durationMillis(),
        entry.isRunning(),
        entry.comments().stream().map(ApprovalCommentResponse::from).toList());
  }
}
