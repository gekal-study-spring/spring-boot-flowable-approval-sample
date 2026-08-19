package cn.gekal.spring.approval.application.service;

import cn.gekal.spring.approval.application.command.ApprovalDecisionCommand;
import cn.gekal.spring.approval.domain.model.ApprovalDecision;
import cn.gekal.spring.approval.domain.model.ApprovalNotPermittedException;
import cn.gekal.spring.approval.domain.model.ApprovalTask;
import cn.gekal.spring.approval.domain.model.ApprovalTaskNotFoundException;
import cn.gekal.spring.approval.domain.model.InvalidExpenseRequestException;
import cn.gekal.spring.approval.domain.repository.ApprovalTaskRepository;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 承認タスクのユースケース。 */
@Service
public class ApprovalTaskService {

  private final ApprovalTaskRepository approvalTaskRepository;

  public ApprovalTaskService(ApprovalTaskRepository approvalTaskRepository) {
    this.approvalTaskRepository = approvalTaskRepository;
  }

  /** 自分が処理できる承認タスク一覧。 */
  public List<ApprovalTask> findMyTasks(String userId, Collection<String> groupIds) {
    if (groupIds.isEmpty()) {
      return List.of();
    }
    return approvalTaskRepository.findOperableTasks(userId, groupIds);
  }

  /** 承認・却下を確定する。 */
  @Transactional
  public void decide(ApprovalDecisionCommand command) {
    ApprovalTask task =
        approvalTaskRepository
            .findById(command.taskId())
            .orElseThrow(() -> new ApprovalTaskNotFoundException(command.taskId()));
    if (!task.isOperableBy(command.approverId())) {
      throw new ApprovalNotPermittedException(
          "このタスクは既に " + task.assignee() + " が引き受けています: " + command.taskId());
    }
    if (command.decision() == ApprovalDecision.REJECTED
        && (command.comment() == null || command.comment().isBlank())) {
      // 却下は申請者へ理由を通知するため、コメントを必須にする
      throw new InvalidExpenseRequestException("却下する場合はコメントが必須です");
    }
    approvalTaskRepository.complete(
        command.taskId(), command.approverId(), command.decision(), command.comment());
  }
}
