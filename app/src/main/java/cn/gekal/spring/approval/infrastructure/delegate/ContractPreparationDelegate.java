package cn.gekal.spring.approval.infrastructure.delegate;

import cn.gekal.spring.approval.infrastructure.workflow.LoanProcessVariables;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 契約書を用意する Service Task 実装（スタブ）。契約番号を採番してプロセス変数へ入れる。 */
@Component
public class ContractPreparationDelegate implements JavaDelegate {

  private static final Logger log = LoggerFactory.getLogger(ContractPreparationDelegate.class);
  private static final DateTimeFormatter CONTRACT_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

  @Override
  public void execute(DelegateExecution execution) {
    String contractNo =
        "LN-"
            + LocalDate.now().format(CONTRACT_DATE)
            + "-"
            + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));

    execution.setVariable(LoanProcessVariables.CONTRACT_NO, contractNo);
    log.info(
        "契約書を作成しました processInstanceId={} applicantId={} amount={} contractNo={}",
        execution.getProcessInstanceId(),
        execution.getVariable(LoanProcessVariables.APPLICANT_ID),
        execution.getVariable(LoanProcessVariables.REQUESTED_AMOUNT_YEN),
        contractNo);
  }
}
