package cn.gekal.spring.approval.presentation.api;

import cn.gekal.spring.approval.application.command.ApprovalDecisionCommand;
import cn.gekal.spring.approval.application.service.ApprovalTaskService;
import cn.gekal.spring.approval.application.service.ExpenseRequestService;
import cn.gekal.spring.approval.domain.model.ApprovalDecision;
import cn.gekal.spring.approval.domain.model.ApprovalTask;
import cn.gekal.spring.approval.domain.model.ApprovalTaskNotFoundException;
import cn.gekal.spring.approval.domain.model.ApproverRole;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 承認タスク API。 */
@RestController
@RequestMapping("/api/tasks")
public class ApprovalTaskApi {

  /** 承認候補グループとして扱う権限文字列。 */
  private static final Set<String> APPROVER_GROUPS =
      Arrays.stream(ApproverRole.values()).map(ApproverRole::groupId).collect(Collectors.toSet());

  private final ApprovalTaskService approvalTaskService;
  private final ExpenseRequestService expenseRequestService;

  public ApprovalTaskApi(
      ApprovalTaskService approvalTaskService, ExpenseRequestService expenseRequestService) {
    this.approvalTaskService = approvalTaskService;
    this.expenseRequestService = expenseRequestService;
  }

  /** 自分が処理できる承認タスク一覧を取得する。 */
  @GetMapping
  public List<ApprovalTaskResponse> findMine(Authentication authentication) {
    return approvalTaskService
        .findMyTasks(authentication.getName(), approverGroupsOf(authentication))
        .stream()
        .map(ApprovalTaskResponse::from)
        .toList();
  }

  /** 承認する。 */
  @PostMapping("/{taskId}/approve")
  public ExpenseRequestResponse approve(
      @PathVariable String taskId,
      @Valid @RequestBody ApprovalDecisionRequest request,
      Authentication authentication) {
    return decide(taskId, ApprovalDecision.APPROVED, request.comment(), authentication);
  }

  /** 却下する。コメントは必須。 */
  @PostMapping("/{taskId}/reject")
  public ExpenseRequestResponse reject(
      @PathVariable String taskId,
      @Valid @RequestBody ApprovalDecisionRequest request,
      Authentication authentication) {
    return decide(taskId, ApprovalDecision.REJECTED, request.comment(), authentication);
  }

  private ExpenseRequestResponse decide(
      String taskId, ApprovalDecision decision, String comment, Authentication authentication) {
    ApprovalTask task =
        approvalTaskService
            .findMyTasks(authentication.getName(), approverGroupsOf(authentication))
            .stream()
            .filter(candidate -> candidate.taskId().equals(taskId))
            .findFirst()
            .orElseThrow(() -> new ApprovalTaskNotFoundException(taskId));

    approvalTaskService.decide(
        new ApprovalDecisionCommand(taskId, authentication.getName(), decision, comment));

    return ExpenseRequestResponse.from(expenseRequestService.findState(task.processInstanceId()));
  }

  /** 認証情報の権限のうち、承認候補グループに使うものだけを取り出す。 */
  private static Set<String> approverGroupsOf(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(APPROVER_GROUPS::contains)
        .collect(Collectors.toSet());
  }
}
