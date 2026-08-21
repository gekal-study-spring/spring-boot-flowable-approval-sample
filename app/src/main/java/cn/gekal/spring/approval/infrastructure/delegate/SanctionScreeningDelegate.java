package cn.gekal.spring.approval.infrastructure.delegate;

import cn.gekal.spring.approval.infrastructure.workflow.LoanProcessVariables;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 反社会的勢力・制裁リストとの照合を行う Service Task 実装（スタブ）。
 *
 * <p>実際の照合は行わない。申込者IDが {@code sanctioned} で始まるときだけヒット扱いにして、謝絶ルートを動かせるようにしている。
 */
@Component
public class SanctionScreeningDelegate implements JavaDelegate {

  private static final Logger log = LoggerFactory.getLogger(SanctionScreeningDelegate.class);

  /** 照合対象のリスト種別。 */
  private Expression listCode;

  @Override
  public void execute(DelegateExecution execution) {
    String list = listCode == null ? "-" : (String) listCode.getValue(execution);
    String applicantId = String.valueOf(execution.getVariable(LoanProcessVariables.APPLICANT_ID));
    boolean hit = applicantId.startsWith("sanctioned");

    execution.setVariable(LoanProcessVariables.SANCTION_HIT, hit);
    log.info(
        "リスト照合を行いました list={} processInstanceId={} applicantId={} hit={}",
        list,
        execution.getProcessInstanceId(),
        applicantId,
        hit);
  }

  public void setListCode(Expression listCode) {
    this.listCode = listCode;
  }
}
