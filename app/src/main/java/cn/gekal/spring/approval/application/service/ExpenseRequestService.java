package cn.gekal.spring.approval.application.service;

import cn.gekal.spring.approval.application.command.StartExpenseRequestCommand;
import cn.gekal.spring.approval.domain.model.ApproverRole;
import cn.gekal.spring.approval.domain.model.ExpenseRequest;
import cn.gekal.spring.approval.domain.model.ExpenseRequestNotFoundException;
import cn.gekal.spring.approval.domain.model.ExpenseRequestState;
import cn.gekal.spring.approval.domain.repository.ExpenseRequestRepository;
import cn.gekal.spring.approval.domain.service.ExpenseApprovalPolicy;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 経費精算申請のユースケース。 */
@Service
public class ExpenseRequestService {

  private final ExpenseRequestRepository expenseRequestRepository;
  private final ExpenseApprovalPolicy expenseApprovalPolicy;

  public ExpenseRequestService(
      ExpenseRequestRepository expenseRequestRepository,
      ExpenseApprovalPolicy expenseApprovalPolicy) {
    this.expenseRequestRepository = expenseRequestRepository;
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
