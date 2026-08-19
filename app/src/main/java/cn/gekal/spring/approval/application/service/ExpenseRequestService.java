package cn.gekal.spring.approval.application.service;

import cn.gekal.spring.approval.application.command.StartExpenseRequestCommand;
import cn.gekal.spring.approval.domain.model.ApprovalHistoryEntry;
import cn.gekal.spring.approval.domain.model.ApproverRole;
import cn.gekal.spring.approval.domain.model.ExpenseRequest;
import cn.gekal.spring.approval.domain.model.ExpenseRequestNotFoundException;
import cn.gekal.spring.approval.domain.model.ExpenseRequestState;
import cn.gekal.spring.approval.domain.model.ProcessDiagram;
import cn.gekal.spring.approval.domain.repository.ApprovalHistoryRepository;
import cn.gekal.spring.approval.domain.repository.ExpenseRequestRepository;
import cn.gekal.spring.approval.domain.repository.ProcessDiagramRepository;
import cn.gekal.spring.approval.domain.service.ExpenseApprovalPolicy;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 経費精算申請のユースケース。 */
@Service
public class ExpenseRequestService {

  private final ExpenseRequestRepository expenseRequestRepository;
  private final ApprovalHistoryRepository approvalHistoryRepository;
  private final ProcessDiagramRepository processDiagramRepository;
  private final ExpenseApprovalPolicy expenseApprovalPolicy;

  public ExpenseRequestService(
      ExpenseRequestRepository expenseRequestRepository,
      ApprovalHistoryRepository approvalHistoryRepository,
      ProcessDiagramRepository processDiagramRepository,
      ExpenseApprovalPolicy expenseApprovalPolicy) {
    this.expenseRequestRepository = expenseRequestRepository;
    this.approvalHistoryRepository = approvalHistoryRepository;
    this.processDiagramRepository = processDiagramRepository;
    this.expenseApprovalPolicy = expenseApprovalPolicy;
  }

  /** 申請を起票し、承認ワークフローを開始する。 */
  @Transactional
  public ExpenseRequest start(StartExpenseRequestCommand command) {
    ExpenseRequest request =
        ExpenseRequest.create(
            command.applicantId(),
            command.title(),
            command.amountYen(),
            command.expenseDate(),
            command.category(),
            command.remarks());
    return expenseRequestRepository.start(request);
  }

  /** 申請の現在状態を取得する。存在しなければ例外。 */
  public ExpenseRequestState findState(String processInstanceId) {
    return expenseRequestRepository
        .findState(processInstanceId)
        .orElseThrow(() -> new ExpenseRequestNotFoundException(processInstanceId));
  }

  /** 申請の履歴（誰がいつ何をしたか）を発生順に取得する。存在しない申請なら例外。 */
  public List<ApprovalHistoryEntry> findHistory(String processInstanceId) {
    // 存在しない申請に対して空リストを返すと 404 と区別が付かないため、先に存在を確かめる
    findState(processInstanceId);
    return approvalHistoryRepository.findHistory(processInstanceId);
  }

  /** 承認フローの図（BPMN 定義と進捗）を取得する。存在しない申請なら例外。 */
  public ProcessDiagram findDiagram(String processInstanceId) {
    findState(processInstanceId);
    return processDiagramRepository.findDiagram(processInstanceId);
  }

  /** 申請者自身の申請一覧を取得する。 */
  public List<ExpenseRequestState> findMyRequests(String applicantId) {
    return expenseRequestRepository.findByApplicant(applicantId);
  }

  /** 起票前に、この金額がどちらの承認者に回るかを判定する（画面表示用）。 */
  public ApproverRole previewApproverRole(long amountYen) {
    return expenseApprovalPolicy.approverRoleOf(
        cn.gekal.spring.approval.domain.model.ExpenseAmount.of(amountYen));
  }
}
