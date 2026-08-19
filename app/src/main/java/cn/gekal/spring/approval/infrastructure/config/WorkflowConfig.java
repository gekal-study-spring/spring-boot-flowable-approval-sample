package cn.gekal.spring.approval.infrastructure.config;

import cn.gekal.spring.approval.domain.service.ExpenseApprovalPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** ドメインサービスを Spring Bean として公開する設定。 */
@Configuration
public class WorkflowConfig {

  /**
   * 承認ルーティング規程。
   *
   * <p>ドメイン層には Spring のアノテーションを持ち込まないため、ここで Bean 化する。Bean 名は BPMN の分岐条件 {@code
   * ${expenseApprovalPolicy.requiresDirectorApproval(amount)}} と一致させること。
   */
  @Bean
  public ExpenseApprovalPolicy expenseApprovalPolicy() {
    return new ExpenseApprovalPolicy();
  }
}
