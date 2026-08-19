package cn.gekal.spring.approval.presentation.api;

import cn.gekal.spring.approval.domain.model.ExpenseRequestState;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 経費精算申請の状態レスポンス。 */
public record ExpenseRequestResponse(
    String processInstanceId,
    String applicantId,
    String title,
    long amount,
    LocalDate expenseDate,
    String category,
    String remarks,
    String status,
    String currentTaskName,
    String approverId,
    String approvalComment,
    String erpVoucherNo,
    int reminderCount,
    LocalDateTime startedAt,
    LocalDateTime endedAt) {

  public static ExpenseRequestResponse from(ExpenseRequestState state) {
    return new ExpenseRequestResponse(
        state.request().processInstanceId(),
        state.request().applicantId(),
        state.request().title(),
        state.request().amount().yen(),
        state.request().expenseDate(),
        state.request().category(),
        state.request().remarks(),
        state.status().name(),
        state.currentTaskName(),
        state.approverId(),
        state.approvalComment(),
        state.erpVoucherNo(),
        state.reminderCount(),
        state.startedAt(),
        state.endedAt());
  }
}
