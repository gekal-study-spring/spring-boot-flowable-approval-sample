package cn.gekal.spring.approval.presentation.api;

import cn.gekal.spring.approval.domain.model.ApprovalTask;
import java.time.LocalDateTime;

/** 承認タスクのレスポンス。 */
public record ApprovalTaskResponse(
    String taskId,
    String name,
    String role,
    String processInstanceId,
    String assignee,
    LocalDateTime createdAt,
    String title,
    long amount,
    String applicantId) {

  public static ApprovalTaskResponse from(ApprovalTask task) {
    return new ApprovalTaskResponse(
        task.taskId(),
        task.name(),
        task.role().name(),
        task.processInstanceId(),
        task.assignee(),
        task.createdAt(),
        task.title(),
        task.amount().yen(),
        task.applicantId());
  }
}
