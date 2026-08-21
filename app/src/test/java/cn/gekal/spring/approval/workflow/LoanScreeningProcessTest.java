package cn.gekal.spring.approval.workflow;

import static org.assertj.core.api.Assertions.assertThat;

import cn.gekal.spring.approval.domain.model.CreditDecision;
import cn.gekal.spring.approval.infrastructure.workflow.LoanProcessVariables;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flowable.engine.HistoryService;
import org.flowable.engine.ManagementService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.job.api.Job;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * 個人ローン審査プロセスの結合テスト。
 *
 * <p>この段階では申込の入口（API・画面）を作っていないため、Flowable のサービスを直接叩いてフローの骨格が通ることを確かめる。
 *
 * <p>非同期ジョブ（信用情報照会・反社チェック・融資実行）が勝手に走ると結果が不安定になるため、非同期エグゼキュータを止めて明示的に実行する。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
    properties = {
      "flowable.async-executor-activate=false",
      "spring.datasource.url=jdbc:h2:mem:loan-screening;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE"
    })
class LoanScreeningProcessTest {

  @Autowired private RuntimeService runtimeService;
  @Autowired private TaskService taskService;
  @Autowired private HistoryService historyService;
  @Autowired private ManagementService managementService;

  /**
   * 申込を起票する。
   *
   * <p>信用スコアは applicantId から決まる（スタブが乱数を使わない）ため、申込者名でルートを選べる。 {@code yamada} は中位スコアで人手審査、{@code
   * watanabe} は高スコアで自動承認、 {@code sanctioned-*} はリスト照合に該当して自動謝絶になる。
   */
  private String start(String applicantId, long amountYen, long annualIncomeYen) {
    Map<String, Object> variables = new HashMap<>();
    variables.put(LoanProcessVariables.APPLICANT_ID, applicantId);
    variables.put(LoanProcessVariables.PRODUCT_TYPE, "CARD_LOAN");
    variables.put(LoanProcessVariables.REQUESTED_AMOUNT_YEN, amountYen);
    variables.put(LoanProcessVariables.ANNUAL_INCOME_YEN, annualIncomeYen);
    variables.put(LoanProcessVariables.PURPOSE, "生活資金");
    return runtimeService
        .startProcessInstanceByKey(LoanProcessVariables.PROCESS_DEFINITION_KEY, variables)
        .getId();
  }

  private Task currentTask(String processInstanceId) {
    List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstanceId).list();
    assertThat(tasks).hasSize(1);
    return tasks.getFirst();
  }

  private void complete(String processInstanceId, Map<String, Object> variables) {
    taskService.complete(currentTask(processInstanceId).getId(), variables);
  }

  /** 未実行の非同期ジョブをすべて実行する。並行に積まれるため、無くなるまで繰り返す。 */
  private void executeAsyncJobs(String processInstanceId) {
    for (int i = 0; i < 10; i++) {
      List<Job> jobs =
          managementService.createJobQuery().processInstanceId(processInstanceId).list();
      if (jobs.isEmpty()) {
        return;
      }
      jobs.forEach(job -> managementService.executeJob(job.getId()));
    }
  }

  /** 書類確認を「不備なし」で通し、外部照会（並行）まで進める。 */
  private void passDocumentCheck(String processInstanceId) {
    assertThat(currentTask(processInstanceId).getTaskDefinitionKey())
        .isEqualTo(LoanProcessVariables.TASK_DOCUMENT_CHECK);
    complete(processInstanceId, Map.of(LoanProcessVariables.DOCUMENTS_COMPLETE, true));
    executeAsyncJobs(processInstanceId);
  }

  private Object variable(String processInstanceId, String name) {
    return historyService
        .createHistoricVariableInstanceQuery()
        .processInstanceId(processInstanceId)
        .variableName(name)
        .singleResult()
        .getValue();
  }

  private String endActivity(String processInstanceId) {
    HistoricProcessInstance instance =
        historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .singleResult();
    assertThat(instance.getEndTime()).as("プロセスが完了していること").isNotNull();
    return instance.getEndActivityId();
  }

  @Test
  @DisplayName("書類不備なら申込者へ差し戻し、再提出すると書類確認へ戻る")
  void documentsAreReturnedForCorrection() {
    String id = start("yamada", 500_000L, 6_000_000L);

    // 不備ありで差戻し
    complete(id, Map.of(LoanProcessVariables.DOCUMENTS_COMPLETE, false));
    assertThat(currentTask(id).getTaskDefinitionKey())
        .isEqualTo(LoanProcessVariables.TASK_ADDITIONAL_DOCUMENTS);

    // 申込者が再提出すると書類確認へ戻る
    complete(id, Map.of());
    assertThat(currentTask(id).getTaskDefinitionKey())
        .isEqualTo(LoanProcessVariables.TASK_DOCUMENT_CHECK);

    // 何度でも回れる
    complete(id, Map.of(LoanProcessVariables.DOCUMENTS_COMPLETE, false));
    assertThat(currentTask(id).getTaskDefinitionKey())
        .isEqualTo(LoanProcessVariables.TASK_ADDITIONAL_DOCUMENTS);
  }

  @Test
  @DisplayName("信用情報照会と反社チェックは並行に進み、両方そろってからスコアリングへ進む")
  void inquiriesRunInParallel() {
    String id = start("yamada", 500_000L, 6_000_000L);
    complete(id, Map.of(LoanProcessVariables.DOCUMENTS_COMPLETE, true));

    // 2つの Service Task が同時に非同期ジョブとして積まれている
    assertThat(managementService.createJobQuery().processInstanceId(id).list())
        .as("並行する2つの照会が同時に待機していること")
        .hasSize(2);

    executeAsyncJobs(id);
    assertThat(variable(id, LoanProcessVariables.CREDIT_SCORE)).isNotNull();
    assertThat(variable(id, LoanProcessVariables.SANCTION_HIT)).isEqualTo(false);
    assertThat(variable(id, LoanProcessVariables.CREDIT_DECISION)).isNotNull();
  }

  @Test
  @DisplayName("リスト照合に該当すると、人手を介さず自動で謝絶される")
  void sanctionHitIsDeclinedAutomatically() {
    String id = start("sanctioned-user", 500_000L, 6_000_000L);
    passDocumentCheck(id);

    assertThat(variable(id, LoanProcessVariables.CREDIT_DECISION))
        .isEqualTo(CreditDecision.AUTO_DECLINE.name());
    assertThat(variable(id, LoanProcessVariables.DECLINE_REASON)).isEqualTo("リスト照合に該当");
    assertThat(endActivity(id)).isEqualTo("endEventDeclined");
  }

  @Test
  @DisplayName("少額かつ高スコアなら、人手を介さず自動承認で契約へ進む")
  void highScoreSmallAmountIsApprovedAutomatically() {
    String id = start("watanabe", 500_000L, 6_000_000L);
    passDocumentCheck(id);

    assertThat(variable(id, LoanProcessVariables.CREDIT_DECISION))
        .isEqualTo(CreditDecision.AUTO_APPROVE.name());
    // 審査タスクを挟まずに契約締結まで来ている
    assertThat(currentTask(id).getTaskDefinitionKey())
        .isEqualTo(LoanProcessVariables.TASK_CONTRACT_SIGNING);
    assertThat(variable(id, LoanProcessVariables.CONTRACT_NO)).asString().startsWith("LN-");
  }

  @Test
  @DisplayName("金額が500万円以上なら課長決裁のあとに部長決裁が入る")
  void largeAmountRequiresExecutiveApproval() {
    String id = start("yamada", 8_000_000L, 20_000_000L);
    passDocumentCheck(id);

    assertThat(variable(id, LoanProcessVariables.CREDIT_DECISION))
        .isEqualTo(CreditDecision.MANUAL.name());

    assertThat(currentTask(id).getTaskDefinitionKey())
        .isEqualTo(LoanProcessVariables.TASK_ANALYST_REVIEW);
    complete(id, Map.of(LoanProcessVariables.APPROVED, true));

    assertThat(currentTask(id).getTaskDefinitionKey())
        .isEqualTo(LoanProcessVariables.TASK_MANAGER_APPROVAL);
    complete(id, Map.of(LoanProcessVariables.APPROVED, true));

    // 500万円以上なので部長決裁へ回る
    assertThat(currentTask(id).getTaskDefinitionKey())
        .isEqualTo(LoanProcessVariables.TASK_EXECUTIVE_APPROVAL);
    complete(id, Map.of(LoanProcessVariables.APPROVED, true));

    // 承認されたので契約締結へ
    assertThat(currentTask(id).getTaskDefinitionKey())
        .isEqualTo(LoanProcessVariables.TASK_CONTRACT_SIGNING);
    assertThat(variable(id, LoanProcessVariables.CONTRACT_NO)).asString().startsWith("LN-");

    complete(id, Map.of());
    executeAsyncJobs(id);
    assertThat(endActivity(id)).isEqualTo("endEventFunded");
  }

  @Test
  @DisplayName("500万円未満なら部長決裁は挟まらない")
  void smallAmountSkipsExecutiveApproval() {
    String id = start("yamada", 3_000_000L, 20_000_000L);
    passDocumentCheck(id);

    complete(id, Map.of(LoanProcessVariables.APPROVED, true));
    complete(id, Map.of(LoanProcessVariables.APPROVED, true));

    // 課長決裁のあとは部長を飛ばして契約締結
    assertThat(currentTask(id).getTaskDefinitionKey())
        .isEqualTo(LoanProcessVariables.TASK_CONTRACT_SIGNING);
  }

  @Test
  @DisplayName("人手審査で否決すると謝絶へ回る")
  void manualRejectionGoesToDecline() {
    String id = start("yamada", 3_000_000L, 20_000_000L);
    passDocumentCheck(id);

    complete(id, Map.of(LoanProcessVariables.APPROVED, true));
    complete(
        id,
        Map.of(
            LoanProcessVariables.APPROVED,
            false,
            LoanProcessVariables.APPROVAL_COMMENT,
            "返済負担率が高い"));

    assertThat(endActivity(id)).isEqualTo("endEventDeclined");
  }

  @Test
  @DisplayName("契約締結の期限を過ぎると、タスクが打ち切られて失効する")
  void contractExpiresWhenNotSignedInTime() {
    String id = start("yamada", 3_000_000L, 20_000_000L);
    passDocumentCheck(id);
    complete(id, Map.of(LoanProcessVariables.APPROVED, true));
    complete(id, Map.of(LoanProcessVariables.APPROVED, true));
    assertThat(currentTask(id).getTaskDefinitionKey())
        .isEqualTo(LoanProcessVariables.TASK_CONTRACT_SIGNING);

    // 期限のタイマーを発火させる（中断型なので契約締結タスクごと打ち切られる）
    Job timer = managementService.createTimerJobQuery().processInstanceId(id).singleResult();
    assertThat(timer).as("契約締結には期限のタイマーが張られていること").isNotNull();
    managementService.executeJob(managementService.moveTimerToExecutableJob(timer.getId()).getId());

    assertThat(taskService.createTaskQuery().processInstanceId(id).list())
        .as("中断型タイマーなので契約締結タスクは残らない")
        .isEmpty();
    assertThat(endActivity(id)).isEqualTo("endEventExpired");
  }
}
