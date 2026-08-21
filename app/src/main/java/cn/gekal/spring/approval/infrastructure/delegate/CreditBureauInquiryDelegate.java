package cn.gekal.spring.approval.infrastructure.delegate;

import cn.gekal.spring.approval.infrastructure.workflow.LoanProcessVariables;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 信用情報機関へ照会する Service Task 実装（スタブ）。
 *
 * <p>実際の照会は行わず、申込者IDから決まるスコアを返す。同じ申込者なら毎回同じ値になるので、テストで結果を固定できる。
 *
 * <p>BPMN 側で {@code flowable:async="true"} かつ {@code exclusive="false"}
 * にしてあり、反社チェックと並行に走る。外部連携は失敗しうるため、実運用では リトライ回数とデッドレターの扱いをここで設計する。
 */
@Component
public class CreditBureauInquiryDelegate implements JavaDelegate {

  private static final Logger log = LoggerFactory.getLogger(CreditBureauInquiryDelegate.class);

  /** BPMN の flowable:field から注入される照会先。 */
  private Expression bureauCode;

  @Override
  public void execute(DelegateExecution execution) {
    String bureau = bureauCode == null ? "-" : (String) bureauCode.getValue(execution);
    String applicantId = (String) execution.getVariable(LoanProcessVariables.APPLICANT_ID);

    // 申込者IDから決まる 300〜850 のスコア。テストで期待値を書けるよう乱数は使わない
    int score = 300 + Math.floorMod(String.valueOf(applicantId).hashCode(), 551);

    execution.setVariable(LoanProcessVariables.CREDIT_SCORE, score);
    execution.setVariable(LoanProcessVariables.BUREAU_STATUS, "OK");
    log.info(
        "信用情報を照会しました bureau={} processInstanceId={} applicantId={} score={}",
        bureau,
        execution.getProcessInstanceId(),
        applicantId,
        score);
  }

  public void setBureauCode(Expression bureauCode) {
    this.bureauCode = bureauCode;
  }
}
