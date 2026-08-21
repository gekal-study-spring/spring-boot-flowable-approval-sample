package cn.gekal.spring.approval.infrastructure.delegate;

import cn.gekal.spring.approval.domain.model.CreditDecision;
import cn.gekal.spring.approval.domain.service.LoanScreeningPolicy;
import cn.gekal.spring.approval.infrastructure.workflow.LoanProcessVariables;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 照会結果から与信の判定区分を決める Service Task 実装。
 *
 * <p>判定そのものは {@link LoanScreeningPolicy}（ドメイン）に委ね、ここは変数の出し入れだけを行う。実運用ではこの判定を DMN
 * の決定表に置き換えると、審査基準をアプリの再デプロイなしに差し替えられる。
 */
@Component
public class CreditScoringDelegate implements JavaDelegate {

  private static final Logger log = LoggerFactory.getLogger(CreditScoringDelegate.class);

  private final LoanScreeningPolicy loanScreeningPolicy;

  public CreditScoringDelegate(LoanScreeningPolicy loanScreeningPolicy) {
    this.loanScreeningPolicy = loanScreeningPolicy;
  }

  @Override
  public void execute(DelegateExecution execution) {
    int creditScore = asInt(execution.getVariable(LoanProcessVariables.CREDIT_SCORE));
    boolean sanctionHit =
        Boolean.TRUE.equals(execution.getVariable(LoanProcessVariables.SANCTION_HIT));
    long requestedAmountYen =
        asLong(execution.getVariable(LoanProcessVariables.REQUESTED_AMOUNT_YEN));
    long annualIncomeYen = asLong(execution.getVariable(LoanProcessVariables.ANNUAL_INCOME_YEN));

    CreditDecision decision =
        loanScreeningPolicy.decide(creditScore, sanctionHit, requestedAmountYen, annualIncomeYen);
    execution.setVariable(LoanProcessVariables.CREDIT_DECISION, decision.name());

    if (decision == CreditDecision.AUTO_DECLINE) {
      execution.setVariable(
          LoanProcessVariables.DECLINE_REASON, sanctionHit ? "リスト照合に該当" : "信用スコアが基準を下回る");
    }

    log.info(
        "与信判定を行いました processInstanceId={} score={} sanctionHit={} amount={} decision={}",
        execution.getProcessInstanceId(),
        creditScore,
        sanctionHit,
        requestedAmountYen,
        decision);
  }

  private static int asInt(Object value) {
    return value instanceof Number number ? number.intValue() : 0;
  }

  private static long asLong(Object value) {
    return value instanceof Number number ? number.longValue() : 0L;
  }
}
