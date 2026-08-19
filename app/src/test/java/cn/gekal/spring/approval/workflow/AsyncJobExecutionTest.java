package cn.gekal.spring.approval.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gekal.spring.approval.application.command.ApprovalDecisionCommand;
import cn.gekal.spring.approval.application.command.StartExpenseRequestCommand;
import cn.gekal.spring.approval.application.service.ApprovalTaskService;
import cn.gekal.spring.approval.application.service.ExpenseRequestService;
import cn.gekal.spring.approval.application.service.ReminderTriggerService;
import cn.gekal.spring.approval.domain.model.ApprovalDecision;
import cn.gekal.spring.approval.domain.model.ApprovalTask;
import cn.gekal.spring.approval.domain.model.ApproverRole;
import cn.gekal.spring.approval.domain.model.ExpenseRequest;
import cn.gekal.spring.approval.domain.model.ExpenseRequestState;
import cn.gekal.spring.approval.domain.model.ExpenseRequestStatus;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 非同期エグゼキュータを動かした状態の結合テスト。
 *
 * <p>リマインドの強制発火を外側のトランザクションで包むと、プロセスインスタンスの排他ロックが残って以降の非同期ジョブが 「Could not lock process
 * instance」で永久に実行できなくなる。その退行を検知するためのテスト。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "flowable.async-executor-activate=true",
      "spring.datasource.url=jdbc:h2:mem:expense-approval-async;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
class AsyncJobExecutionTest {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  @Autowired private ExpenseRequestService expenseRequestService;
  @Autowired private ApprovalTaskService approvalTaskService;
  @Autowired private ReminderTriggerService reminderTriggerService;

  @Test
  @DisplayName("リマインド発火後に承認しても、基幹システム連携の非同期ジョブが実行される")
  void erpIntegrationRunsAfterReminder() throws Exception {
    ExpenseRequest request =
        expenseRequestService.start(
            new StartExpenseRequestCommand("yamada", "会議費", 30_000L, LocalDate.now(), "会議費", null));
    String processInstanceId = request.processInstanceId();

    // タイマーを期限切れ状態にすると、実行は非同期エグゼキュータが担当する
    assertThat(reminderTriggerService.fireReminders(processInstanceId)).isEqualTo(1);
    awaitReminder(processInstanceId);

    ApprovalTask task =
        approvalTaskService.findMyTasks("sato", List.of(ApproverRole.MANAGER.groupId())).stream()
            .filter(candidate -> candidate.processInstanceId().equals(processInstanceId))
            .findFirst()
            .orElseThrow();
    approvalTaskService.decide(
        new ApprovalDecisionCommand(task.taskId(), "sato", ApprovalDecision.APPROVED, "承認"));

    ExpenseRequestState state = awaitCompletion(processInstanceId);
    assertThat(state.status()).isEqualTo(ExpenseRequestStatus.APPROVED);
    assertThat(state.erpVoucherNo()).startsWith("ERP-");
    assertThat(state.reminderCount()).isEqualTo(1);
  }

  /** リマインドが送信されるまで待つ。 */
  private void awaitReminder(String processInstanceId) throws Exception {
    long deadline = System.nanoTime() + TIMEOUT.toNanos();
    while (expenseRequestService.findState(processInstanceId).reminderCount() == 0
        && System.nanoTime() < deadline) {
      Thread.sleep(200);
    }
  }

  /** 非同期ジョブの完了を待つ。 */
  private ExpenseRequestState awaitCompletion(String processInstanceId) throws Exception {
    long deadline = System.nanoTime() + TIMEOUT.toNanos();
    ExpenseRequestState state = expenseRequestService.findState(processInstanceId);
    while (state.erpVoucherNo() == null && System.nanoTime() < deadline) {
      Thread.sleep(200);
      state = expenseRequestService.findState(processInstanceId);
    }
    return state;
  }
}
