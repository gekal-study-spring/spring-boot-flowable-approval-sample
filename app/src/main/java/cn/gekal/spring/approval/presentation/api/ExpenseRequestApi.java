package cn.gekal.spring.approval.presentation.api;

import cn.gekal.spring.approval.application.command.StartExpenseRequestCommand;
import cn.gekal.spring.approval.application.service.ExpenseRequestService;
import cn.gekal.spring.approval.domain.model.ExpenseRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 経費精算申請 API。 */
@RestController
@RequestMapping("/api/expense-requests")
public class ExpenseRequestApi {

  private final ExpenseRequestService expenseRequestService;

  public ExpenseRequestApi(ExpenseRequestService expenseRequestService) {
    this.expenseRequestService = expenseRequestService;
  }

  /** 申請を起票し、承認ワークフローを開始する。 */
  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ExpenseRequestResponse create(
      @Valid @RequestBody ExpenseRequestCreateRequest request, Principal principal) {
    ExpenseRequest started =
        expenseRequestService.start(
            new StartExpenseRequestCommand(
                principal.getName(),
                request.title(),
                request.amount(),
                request.expenseDate(),
                request.category(),
                request.remarks()));
    return ExpenseRequestResponse.from(
        expenseRequestService.findState(started.processInstanceId()));
  }

  /** 申請の現在状態を取得する。 */
  @GetMapping("/{processInstanceId}")
  public ExpenseRequestResponse find(@PathVariable String processInstanceId) {
    return ExpenseRequestResponse.from(expenseRequestService.findState(processInstanceId));
  }

  /** 申請の履歴（誰がいつ何をしたか）を取得する。 */
  @GetMapping("/{processInstanceId}/history")
  public List<ApprovalHistoryResponse> findHistory(@PathVariable String processInstanceId) {
    return expenseRequestService.findHistory(processInstanceId).stream()
        .map(ApprovalHistoryResponse::from)
        .toList();
  }

  /** 承認フローの図（BPMN 定義と、どこまで進んだか）を取得する。 */
  @GetMapping("/{processInstanceId}/diagram")
  public ProcessDiagramResponse findDiagram(@PathVariable String processInstanceId) {
    return ProcessDiagramResponse.from(expenseRequestService.findDiagram(processInstanceId));
  }

  /** 自分の申請一覧を取得する。 */
  @GetMapping
  public List<ExpenseRequestResponse> findMine(Principal principal) {
    return expenseRequestService.findMyRequests(principal.getName()).stream()
        .map(ExpenseRequestResponse::from)
        .toList();
  }
}
