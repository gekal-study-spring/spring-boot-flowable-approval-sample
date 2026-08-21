package cn.gekal.spring.approval.infrastructure.delegate;

import cn.gekal.spring.approval.infrastructure.workflow.LoanProcessVariables;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 謝絶を申込者へ通知する Service Task 実装（スタブ）。
 *
 * <p>自動謝絶と人手審査での否決の両方から呼ばれる。理由は自動謝絶ならスコアリング側が、否決なら審査担当の所見が入る。
 */
@Component
public class DeclineNotificationDelegate implements JavaDelegate {

  private static final Logger log = LoggerFactory.getLogger(DeclineNotificationDelegate.class);

  @Override
  public void execute(DelegateExecution execution) {
    Object reason = execution.getVariable(LoanProcessVariables.DECLINE_REASON);
    if (reason == null) {
      reason = execution.getVariable(LoanProcessVariables.APPROVAL_COMMENT);
    }
    log.info(
        "謝絶を通知しました processInstanceId={} applicantId={} reason={}",
        execution.getProcessInstanceId(),
        execution.getVariable(LoanProcessVariables.APPLICANT_ID),
        reason);
  }
}
