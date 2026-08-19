package cn.gekal.spring.approval.infrastructure.delegate;

import cn.gekal.spring.approval.infrastructure.workflow.ProcessVariables;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 承認済みの経費を基幹システムへ連携する Service Task 実装。
 *
 * <p>サンプルのため実際の HTTP 呼び出しは行わず、伝票番号を採番してログ出力する。例外を投げた場合は Flowable のジョブリトライ（BPMN 側で {@code
 * flowable:async="true"}）で再実行される。
 */
@Component
public class ErpIntegrationDelegate implements JavaDelegate {

  private static final Logger log = LoggerFactory.getLogger(ErpIntegrationDelegate.class);
  private static final DateTimeFormatter VOUCHER_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

  /** BPMN の flowable:field から注入される連携先キー。 */
  private Expression endpointKey;

  @Override
  public void execute(DelegateExecution execution) {
    String endpoint = endpointKey == null ? "-" : (String) endpointKey.getValue(execution);
    String voucherNo =
        "ERP-"
            + LocalDate.now().format(VOUCHER_DATE)
            + "-"
            + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));

    log.info(
        "基幹システムへ連携しました endpoint={} processInstanceId={} applicantId={} amount={} voucherNo={}",
        endpoint,
        execution.getProcessInstanceId(),
        execution.getVariable(ProcessVariables.APPLICANT_ID),
        execution.getVariable(ProcessVariables.AMOUNT),
        voucherNo);

    execution.setVariable(ProcessVariables.ERP_VOUCHER_NO, voucherNo);
  }

  public void setEndpointKey(Expression endpointKey) {
    this.endpointKey = endpointKey;
  }
}
