package cn.gekal.spring.approval.infrastructure.workflow;

import cn.gekal.spring.approval.domain.model.ApprovalDecision;
import cn.gekal.spring.approval.domain.model.ApprovalTask;
import cn.gekal.spring.approval.domain.model.ApproverRole;
import cn.gekal.spring.approval.domain.model.ExpenseAmount;
import cn.gekal.spring.approval.domain.repository.ApprovalTaskRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Repository;

/** 承認タスクをワークフローエンジン（Flowable）上で照会・完了する実装。 */
@Repository
public class FlowableApprovalTaskDatasource implements ApprovalTaskRepository {

  private final TaskService taskService;

  public FlowableApprovalTaskDatasource(TaskService taskService) {
    this.taskService = taskService;
  }

  @Override
  public List<ApprovalTask> findOperableTasks(String userId, Collection<String> groupIds) {
    return taskService
        .createTaskQuery()
        .processDefinitionKey(ProcessVariables.PROCESS_DEFINITION_KEY)
        .or()
        .taskCandidateGroupIn(List.copyOf(groupIds))
        .taskAssignee(userId)
        .endOr()
        .includeProcessVariables()
        .orderByTaskCreateTime()
        .asc()
        .list()
        .stream()
        .map(FlowableApprovalTaskDatasource::toApprovalTask)
        .toList();
  }

  @Override
  public Optional<ApprovalTask> findById(String taskId) {
    Task task =
        taskService.createTaskQuery().taskId(taskId).includeProcessVariables().singleResult();
    return Optional.ofNullable(task).map(FlowableApprovalTaskDatasource::toApprovalTask);
  }

  @Override
  public void complete(
      String taskId, String approverId, ApprovalDecision decision, String comment) {
    Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
    if (task == null) {
      return;
    }
    String previousUser = Authentication.getAuthenticatedUserId();
    try {
      Authentication.setAuthenticatedUserId(approverId);
      if (task.getAssignee() == null) {
        // 候補グループ宛てのタスクは、完了前に引き受け（claim）て担当者を履歴に残す
        taskService.claim(taskId, approverId);
      }
      if (comment != null && !comment.isBlank()) {
        // 履歴として各段階のコメントを残す。プロセス変数だけだと多段承認や差戻しで上書きされてしまう
        taskService.addComment(taskId, task.getProcessInstanceId(), comment);
      }
      Map<String, Object> variables = new HashMap<>();
      variables.put(ProcessVariables.APPROVED, decision.isApproved());
      // 直近の判断内容。却下通知の Service Task がこの変数を読む
      variables.put(ProcessVariables.APPROVAL_COMMENT, comment);
      variables.put(ProcessVariables.APPROVER_ID, approverId);
      taskService.complete(taskId, variables);
    } finally {
      Authentication.setAuthenticatedUserId(previousUser);
    }
  }

  private static ApprovalTask toApprovalTask(Task task) {
    Map<String, Object> variables = task.getProcessVariables();
    Object amount = variables.get(ProcessVariables.AMOUNT);
    return new ApprovalTask(
        task.getId(),
        task.getName(),
        roleOf(task.getTaskDefinitionKey()),
        task.getProcessInstanceId(),
        task.getAssignee(),
        FlowableExpenseRequestDatasource.toLocalDateTime(task.getCreateTime()),
        (String) variables.get(ProcessVariables.TITLE),
        ExpenseAmount.of(amount instanceof Number n ? n.longValue() : 0L),
        (String) variables.get(ProcessVariables.APPLICANT_ID));
  }

  private static ApproverRole roleOf(String taskDefinitionKey) {
    return switch (taskDefinitionKey) {
      case ProcessVariables.TASK_MANAGER_APPROVAL -> ApproverRole.MANAGER;
      case ProcessVariables.TASK_DIRECTOR_APPROVAL -> ApproverRole.DIRECTOR;
      default -> throw new IllegalStateException("未知の承認タスクです: " + taskDefinitionKey);
    };
  }
}
