package cn.gekal.spring.approval.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.gekal.spring.approval.application.command.ApprovalDecisionCommand;
import cn.gekal.spring.approval.application.service.ApprovalTaskService;
import cn.gekal.spring.approval.domain.model.ApprovalDecision;
import cn.gekal.spring.approval.domain.model.ApprovalNotPermittedException;
import cn.gekal.spring.approval.domain.model.ApprovalTask;
import cn.gekal.spring.approval.domain.model.ApprovalTaskNotFoundException;
import cn.gekal.spring.approval.domain.model.ApproverRole;
import cn.gekal.spring.approval.domain.model.ExpenseAmount;
import cn.gekal.spring.approval.domain.model.InvalidExpenseRequestException;
import cn.gekal.spring.approval.domain.repository.ApprovalTaskRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 承認ユースケースのテスト。Repository はモックにする。 */
@ExtendWith(MockitoExtension.class)
class ApprovalTaskServiceTest {

  @Mock private ApprovalTaskRepository approvalTaskRepository;

  @InjectMocks private ApprovalTaskService approvalTaskService;

  private static ApprovalTask task(String assignee) {
    return new ApprovalTask(
        "task-1",
        "課長承認",
        ApproverRole.MANAGER,
        "proc-1",
        assignee,
        LocalDateTime.now(),
        "出張旅費",
        ExpenseAmount.of(50_000L),
        "yamada");
  }

  @Test
  @DisplayName("未引き受けのタスクは承認できる")
  void approve() {
    when(approvalTaskRepository.findById("task-1")).thenReturn(Optional.of(task(null)));

    approvalTaskService.decide(
        new ApprovalDecisionCommand("task-1", "sato", ApprovalDecision.APPROVED, "確認しました"));

    verify(approvalTaskRepository).complete("task-1", "sato", ApprovalDecision.APPROVED, "確認しました");
  }

  @Test
  @DisplayName("他人が引き受け済みのタスクは操作できない")
  void rejectWhenClaimedByOthers() {
    when(approvalTaskRepository.findById("task-1")).thenReturn(Optional.of(task("suzuki")));

    assertThatThrownBy(
            () ->
                approvalTaskService.decide(
                    new ApprovalDecisionCommand("task-1", "sato", ApprovalDecision.APPROVED, null)))
        .isInstanceOf(ApprovalNotPermittedException.class);

    verify(approvalTaskRepository, never()).complete(any(), any(), any(), any());
  }

  @Test
  @DisplayName("却下はコメントが必須である")
  void rejectWithoutComment() {
    when(approvalTaskRepository.findById("task-1")).thenReturn(Optional.of(task(null)));

    assertThatThrownBy(
            () ->
                approvalTaskService.decide(
                    new ApprovalDecisionCommand("task-1", "sato", ApprovalDecision.REJECTED, " ")))
        .isInstanceOf(InvalidExpenseRequestException.class);

    verify(approvalTaskRepository, never()).complete(any(), any(), any(), any());
  }

  @Test
  @DisplayName("存在しないタスクは例外になる")
  void notFound() {
    when(approvalTaskRepository.findById("task-x")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                approvalTaskService.decide(
                    new ApprovalDecisionCommand("task-x", "sato", ApprovalDecision.APPROVED, null)))
        .isInstanceOf(ApprovalTaskNotFoundException.class);
  }

  @Test
  @DisplayName("所属グループがなければ承認タスクは0件になる")
  void noGroups() {
    assertThat(approvalTaskService.findMyTasks("yamada", List.of())).isEmpty();
    verify(approvalTaskRepository, never()).findOperableTasks(eq("yamada"), any());
  }
}
