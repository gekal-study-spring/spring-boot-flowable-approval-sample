package cn.gekal.spring.approval.infrastructure.workflow;

import cn.gekal.spring.approval.domain.model.ExpenseRequest;
import cn.gekal.spring.approval.domain.model.ExpenseRequestState;
import cn.gekal.spring.approval.domain.model.ExpenseRequestStatus;
import cn.gekal.spring.approval.domain.repository.ExpenseRequestRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.flowable.common.engine.impl.identity.Authentication;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Repository;

/** 経費精算申請をワークフローエンジン（Flowable）上で永続化・照会する実装。 */
@Repository
public class FlowableExpenseRequestDatasource implements ExpenseRequestRepository {

  private final RuntimeService runtimeService;
  private final TaskService taskService;
  private final HistoryService historyService;

  public FlowableExpenseRequestDatasource(
      RuntimeService runtimeService, TaskService taskService, HistoryService historyService) {
    this.runtimeService = runtimeService;
    this.taskService = taskService;
    this.historyService = historyService;
  }

  @Override
  public ExpenseRequest start(ExpenseRequest request) {
    Map<String, Object> variables = new HashMap<>();
    variables.put(ProcessVariables.TITLE, request.title());
    variables.put(ProcessVariables.AMOUNT, request.amount().yen());
    variables.put(ProcessVariables.EXPENSE_DATE, request.expenseDate().toString());
    variables.put(ProcessVariables.CATEGORY, request.category());
    variables.put(ProcessVariables.REMARKS, request.remarks());
    variables.put(ProcessVariables.REMINDER_COUNT, 0);

    // startEvent の flowable:initiator="applicantId" は認証済みユーザーIDを見るため、起動前に設定する
    String previousUser = Authentication.getAuthenticatedUserId();
    try {
      Authentication.setAuthenticatedUserId(request.applicantId());
      ProcessInstance instance =
          runtimeService.startProcessInstanceByKey(
              ProcessVariables.PROCESS_DEFINITION_KEY, variables);
      return request.withProcessInstanceId(instance.getId());
    } finally {
      Authentication.setAuthenticatedUserId(previousUser);
    }
  }

  @Override
  public Optional<ExpenseRequestState> findState(String processInstanceId) {
    HistoricProcessInstance instance =
        historyService
            .createHistoricProcessInstanceQuery()
            .processInstanceId(processInstanceId)
            .includeProcessVariables()
            .singleResult();
    return Optional.ofNullable(instance).map(this::toState);
  }

  @Override
  public List<ExpenseRequestState> findByApplicant(String applicantId) {
    return historyService
        .createHistoricProcessInstanceQuery()
        .processDefinitionKey(ProcessVariables.PROCESS_DEFINITION_KEY)
        .variableValueEquals(ProcessVariables.APPLICANT_ID, applicantId)
        .includeProcessVariables()
        .orderByProcessInstanceStartTime()
        .desc()
        .list()
        .stream()
        .map(this::toState)
        .toList();
  }

  private ExpenseRequestState toState(HistoricProcessInstance instance) {
    Map<String, Object> variables = instance.getProcessVariables();
    ExpenseRequest request =
        ExpenseRequest.reconstruct(
            instance.getId(),
            string(variables, ProcessVariables.APPLICANT_ID),
            string(variables, ProcessVariables.TITLE),
            number(variables, ProcessVariables.AMOUNT),
            LocalDate.parse(string(variables, ProcessVariables.EXPENSE_DATE)),
            string(variables, ProcessVariables.CATEGORY),
            string(variables, ProcessVariables.REMARKS));

    boolean finished = instance.getEndTime() != null;
    Boolean approved = (Boolean) variables.get(ProcessVariables.APPROVED);
    ExpenseRequestStatus status;
    if (!finished) {
      status = ExpenseRequestStatus.IN_PROGRESS;
    } else {
      status =
          Boolean.TRUE.equals(approved)
              ? ExpenseRequestStatus.APPROVED
              : ExpenseRequestStatus.REJECTED;
    }

    String currentTaskName = finished ? null : currentTaskName(instance.getId());
    Number reminderCount = (Number) variables.get(ProcessVariables.REMINDER_COUNT);

    return new ExpenseRequestState(
        request,
        status,
        currentTaskName,
        string(variables, ProcessVariables.APPROVER_ID),
        string(variables, ProcessVariables.APPROVAL_COMMENT),
        string(variables, ProcessVariables.ERP_VOUCHER_NO),
        reminderCount == null ? 0 : reminderCount.intValue(),
        toLocalDateTime(instance.getStartTime()),
        toLocalDateTime(instance.getEndTime()));
  }

  private String currentTaskName(String processInstanceId) {
    List<Task> tasks = taskService.createTaskQuery().processInstanceId(processInstanceId).list();
    return tasks.isEmpty() ? null : tasks.getFirst().getName();
  }

  private static String string(Map<String, Object> variables, String name) {
    Object value = variables.get(name);
    return value == null ? null : value.toString();
  }

  private static long number(Map<String, Object> variables, String name) {
    Object value = variables.get(name);
    return value instanceof Number n ? n.longValue() : 0L;
  }

  static LocalDateTime toLocalDateTime(Date date) {
    return date == null ? null : LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
  }
}
