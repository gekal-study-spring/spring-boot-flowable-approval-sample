package cn.gekal.spring.approval.infrastructure.delegate;

import cn.gekal.spring.approval.infrastructure.workflow.LoanProcessVariables;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 勘定系へ融資実行を連携する Service Task 実装（スタブ）。
 *
 * <p>BPMN 側で {@code flowable:async="true"} かつ {@code exclusive="true"} にしてある。金銭が動く処理なので、同じ申込に対する
 * ジョブを直列化し、例外時は Flowable のリトライに委ねる。
 */
@Component
public class LoanExecutionDelegate implements JavaDelegate {

  private static final Logger log = LoggerFactory.getLogger(LoanExecutionDelegate.class);

  /** 連携先キー。 */
  private Expression endpointKey;

  @Override
  public void execute(DelegateExecution execution) {
    String endpoint = endpointKey == null ? "-" : (String) endpointKey.getValue(execution);
    log.info(
        "融資を実行しました endpoint={} processInstanceId={} contractNo={} amount={}",
        endpoint,
        execution.getProcessInstanceId(),
        execution.getVariable(LoanProcessVariables.CONTRACT_NO),
        execution.getVariable(LoanProcessVariables.REQUESTED_AMOUNT_YEN));
  }

  public void setEndpointKey(Expression endpointKey) {
    this.endpointKey = endpointKey;
  }
}
