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
import java.time.LocalDate;
import java.util.List;
import org.flowable.engine.ManagementService;
import org.flowable.job.api.Job;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 承認プロセス全体の結合テスト。
 *
 * <p>非同期ジョブ（ERP連携・リマインドタイマー）が勝手に走ると結果が不安定になるため、非同期エグゼキュータを止めて明示的にジョブを実行する。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "flowable.async-executor-activate=false")
class ExpenseApprovalProcessTest {

  @Autowired private ExpenseRequestService expenseRequestService;
  @Autowired private ApprovalTaskService approvalTaskService;
  @Autowired private ReminderTriggerService reminderTriggerService;
  @Autowired private ManagementService managementService;

  private static final List<String> MANAGERS = List.of(ApproverRole.MANAGER.groupId());
  private static final List<String> DIRECTORS = List.of(ApproverRole.DIRECTOR.groupId());

  private ExpenseRequest start(String title, long amountYen) {
    return expenseRequestService.start(
        new StartExpenseRequestCommand(
            "yamada", title, amountYen, LocalDate.now().minusDays(1), "旅費交通費", "備考"));
  }

  private ApprovalTask taskOf(String processInstanceId, List<String> groups, String userId) {
    return approvalTaskService.findMyTasks(userId, groups).stream()
        .filter(task -> task.processInstanceId().equals(processInstanceId))
        .findFirst()
        .orElseThrow(() -> new AssertionError("承認タスクが見つかりません: " + processInstanceId));
  }

  /** 非同期 Service Task のジョブを実行する。 */
  private void executeAsyncJobs(String processInstanceId) {
    List<Job> jobs = managementService.createJobQuery().processInstanceId(processInstanceId).list();
    jobs.forEach(job -> managementService.executeJob(job.getId()));
  }

  @Test
  @DisplayName("10万円未満は課長承認へ回り、承認すると基幹システムへ連携される")
  void managerApprovalRoute() {
    ExpenseRequest request = start("近距離出張旅費", 50_000L);
    String processInstanceId = request.processInstanceId();

    // 部長には見えず、課長にだけ承認タスクが割り当たる
    assertThat(approvalTaskService.findMyTasks("tanaka", DIRECTORS))
        .noneMatch(task -> task.processInstanceId().equals(processInstanceId));

    ApprovalTask task = taskOf(processInstanceId, MANAGERS, "sato");
    assertThat(task.name()).isEqualTo("課長承認");
    assertThat(task.role()).isEqualTo(ApproverRole.MANAGER);
    assertThat(task.assignee()).isNull();

    approvalTaskService.decide(
        new ApprovalDecisionCommand(task.taskId(), "sato", ApprovalDecision.APPROVED, "問題ありません"));

    // 基幹システム連携は flowable:async="true" のため、ジョブとして後から実行される
    assertThat(expenseRequestService.findState(processInstanceId).erpVoucherNo()).isNull();
    executeAsyncJobs(processInstanceId);

    ExpenseRequestState state = expenseRequestService.findState(processInstanceId);
    assertThat(state.status()).isEqualTo(ExpenseRequestStatus.APPROVED);
    assertThat(state.approverId()).isEqualTo("sato");
    assertThat(state.approvalComment()).isEqualTo("問題ありません");
    assertThat(state.erpVoucherNo()).startsWith("ERP-");
    assertThat(state.endedAt()).isNotNull();
    assertThat(state.currentTaskName()).isNull();
  }

  @Test
  @DisplayName("10万円以上は部長承認へ回り、却下すると基幹システムへ連携されない")
  void directorRejectionRoute() {
    ExpenseRequest request = start("海外出張旅費", 150_000L);
    String processInstanceId = request.processInstanceId();

    assertThat(approvalTaskService.findMyTasks("sato", MANAGERS))
        .noneMatch(task -> task.processInstanceId().equals(processInstanceId));

    ApprovalTask task = taskOf(processInstanceId, DIRECTORS, "tanaka");
    assertThat(task.name()).isEqualTo("部長承認");

    approvalTaskService.decide(
        new ApprovalDecisionCommand(task.taskId(), "tanaka", ApprovalDecision.REJECTED, "見積根拠が不足"));
    executeAsyncJobs(processInstanceId);

    ExpenseRequestState state = expenseRequestService.findState(processInstanceId);
    assertThat(state.status()).isEqualTo(ExpenseRequestStatus.REJECTED);
    assertThat(state.approverId()).isEqualTo("tanaka");
    assertThat(state.erpVoucherNo()).isNull();
    assertThat(state.endedAt()).isNotNull();
  }

  @Test
  @DisplayName("境界値の10万円ちょうどは部長承認へ回る")
  void thresholdGoesToDirector() {
    ExpenseRequest request = start("備品購入", 100_000L);

    ApprovalTask task = taskOf(request.processInstanceId(), DIRECTORS, "tanaka");
    assertThat(task.role()).isEqualTo(ApproverRole.DIRECTOR);
  }

  @Test
  @DisplayName("タイマーが発火するとリマインドが送られ、承認タスクは残る（非中断型）")
  void reminderDoesNotCancelTask() {
    ExpenseRequest request = start("会議費", 30_000L);
    String processInstanceId = request.processInstanceId();

    assertThat(expenseRequestService.findState(processInstanceId).reminderCount()).isZero();

    int fired = reminderTriggerService.fireReminders(processInstanceId);
    assertThat(fired).isEqualTo(1);

    ExpenseRequestState state = expenseRequestService.findState(processInstanceId);
    assertThat(state.reminderCount()).isEqualTo(1);
    assertThat(state.status()).isEqualTo(ExpenseRequestStatus.IN_PROGRESS);
    assertThat(state.currentTaskName()).isEqualTo("課長承認");

    // 承認タスクは残っているので、リマインド後も通常どおり承認できる
    ApprovalTask task = taskOf(processInstanceId, MANAGERS, "sato");
    approvalTaskService.decide(
        new ApprovalDecisionCommand(task.taskId(), "sato", ApprovalDecision.APPROVED, null));
    executeAsyncJobs(processInstanceId);

    assertThat(expenseRequestService.findState(processInstanceId).status())
        .isEqualTo(ExpenseRequestStatus.APPROVED);
  }

  @Test
  @DisplayName("申請者は自分の申請一覧を取得できる")
  void findMyRequests() {
    ExpenseRequest request = start("消耗品費", 3_000L);

    assertThat(expenseRequestService.findMyRequests("yamada"))
        .anyMatch(state -> state.request().processInstanceId().equals(request.processInstanceId()));
    assertThat(expenseRequestService.findMyRequests("suzuki"))
        .noneMatch(
            state -> state.request().processInstanceId().equals(request.processInstanceId()));
  }
}
