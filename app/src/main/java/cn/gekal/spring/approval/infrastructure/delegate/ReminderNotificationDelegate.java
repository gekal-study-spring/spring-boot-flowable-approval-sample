package cn.gekal.spring.approval.infrastructure.delegate;

import cn.gekal.spring.approval.infrastructure.workflow.ProcessVariables;
import org.flowable.common.engine.api.delegate.Expression;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 承認が滞留したときにリマインドを送る Service Task 実装。
 *
 * <p>非中断型の境界タイマーから並行トークンで呼ばれるため、承認タスク自体は残ったままである。送信回数はプロセス変数 {@code reminderCount} に累積する。
 */
@Component
public class ReminderNotificationDelegate implements JavaDelegate {

  private static final Logger log = LoggerFactory.getLogger(ReminderNotificationDelegate.class);

  /** BPMN の flowable:field から注入される通知先グループ。 */
  private Expression targetGroup;

  /** BPMN の flowable:field から注入される通知テンプレートコード。 */
  private Expression templateCode;

  @Override
  public void execute(DelegateExecution execution) {
    String group = targetGroup == null ? "-" : (String) targetGroup.getValue(execution);
    String template = templateCode == null ? "-" : (String) templateCode.getValue(execution);

    Number current = (Number) execution.getVariable(ProcessVariables.REMINDER_COUNT);
    int count = (current == null ? 0 : current.intValue()) + 1;
    execution.setVariable(ProcessVariables.REMINDER_COUNT, count);

    log.info(
        "リマインドを送信しました template={} to={} processInstanceId={} title={} count={}",
        template,
        group,
        execution.getProcessInstanceId(),
        execution.getVariable(ProcessVariables.TITLE),
        count);
  }

  public void setTargetGroup(Expression targetGroup) {
    this.targetGroup = targetGroup;
  }

  public void setTemplateCode(Expression templateCode) {
    this.templateCode = templateCode;
  }
}
