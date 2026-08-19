package cn.gekal.spring.approval.infrastructure.delegate;

import cn.gekal.spring.approval.infrastructure.workflow.ProcessVariables;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 却下時に申請者へ通知する Service Task 実装。サンプルのためログ出力に留める。 */
@Component
public class RejectNotificationDelegate implements JavaDelegate {

  private static final Logger log = LoggerFactory.getLogger(RejectNotificationDelegate.class);

  /** BPMN の flowable:field から注入される通知テンプレートコード。 */
  private Expression templateCode;

  @Override
  public void execute(DelegateExecution execution) {
    String template = templateCode == null ? "-" : (String) templateCode.getValue(execution);
    log.info(
        "却下を通知しました template={} to={} processInstanceId={} approver={} comment={}",
        template,
        execution.getVariable(ProcessVariables.APPLICANT_ID),
        execution.getProcessInstanceId(),
        execution.getVariable(ProcessVariables.APPROVER_ID),
        execution.getVariable(ProcessVariables.APPROVAL_COMMENT));
  }

  public void setTemplateCode(Expression templateCode) {
    this.templateCode = templateCode;
  }
}
